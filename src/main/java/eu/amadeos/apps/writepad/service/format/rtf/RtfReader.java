package eu.amadeos.apps.writepad.service.format.rtf;

import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.domain.Margins;
import eu.amadeos.apps.writepad.domain.Orientation;
import eu.amadeos.apps.writepad.domain.Picture;
import eu.amadeos.apps.writepad.domain.TableFormat;
import eu.amadeos.apps.writepad.domain.Units;
import eu.amadeos.apps.writepad.service.format.DocumentContent;
import eu.amadeos.apps.writepad.service.format.Documents;
import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import eu.amadeos.apps.writepad.service.list.ListAttributes;
import eu.amadeos.apps.writepad.service.list.ListFormat;
import eu.amadeos.apps.writepad.service.picture.PictureAttributes;
import eu.amadeos.apps.writepad.service.table.TableAttributes;
import eu.amadeos.apps.writepad.service.table.TableBuilder;
import eu.amadeos.apps.writepad.service.table.TableLines;
import eu.amadeos.apps.writepad.service.table.Tables;
import eu.amadeos.commons.print.PaperSize;

import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads RTF into a styled document: text, fonts, sizes, bold, italic, underline, strike-through,
 * super-/subscript, colours, alignment, indents, spacing, line spacing, tab stops, lists ({@code \listtable}
 * and WordPad's {@code \pn}), PNG and JPEG pictures ({@code \pict} in the line, {@code \shp} picture shapes
 * floating at the left or right), tables ({@code \trowd}, {@code \cellx}, {@code \cell}, {@code \row}; merged
 * cells come in split, a table in a table as its paragraphs) and the page setup. Everything else (other picture formats, drawings,
 * headers, footnotes, fields' instructions, style sheets, document info) is skipped, so unknown content never
 * breaks the import or appears as garbage.
 *
 * <p>Handles the character sets RTF files use: the document code page ({@code \ansicpg}), per-font
 * charsets ({@code \fcharset}, including double-byte code pages), raw 8-bit bytes and Unicode
 * (<code>&#92;u</code> with <code>&#92;uc</code> fallback characters).
 */
final class RtfReader {
    /** Destinations whose content is not text of the document. */
    private static final Set<String> SKIPPED = Set.of(
            "stylesheet", "info", "pict", "header", "headerl", "headerr", "headerf", "footer", "footerl",
            "footerr", "footerf", "footnote", "annotation", "object", "nonshppict", "shppict", "rsidtbl", "generator", "xmlnstbl", "themedata", "colorschememapping",
            "latentstyles", "datastore", "pntext", "pntxta", "pntxtb", "fldinst", "bkmkstart", "bkmkend",
            "mmathPr", "upr", "filetbl", "revtbl", "protusertbl", "listtext", "userprops", "docvar", "template",
            "background", "shp", "sp", "do", "atnid", "atnauthor", "comment", "keywords", "operator", "title",
            "subject", "author", "company", "doccomm", "hlinkbase", "xe", "tc", "txe", "pgdsctbl", "ftnsep",
            "ftnsepc", "aftnsep", "aftnsepc", "wgrffmtfilter", "fchars", "lchars", "passwordhash", "nesttableprops",
            "nonesttables", "tblind");

    private static final Map<Integer, String> CHARSET_CODEPAGES = Map.ofEntries(
            Map.entry(0, "windows-1252"), Map.entry(2, "windows-1252"), Map.entry(77, "x-MacRoman"),
            Map.entry(128, "windows-31j"), Map.entry(129, "x-windows-949"), Map.entry(134, "GBK"),
            Map.entry(136, "x-windows-950"), Map.entry(161, "windows-1253"), Map.entry(162, "windows-1254"),
            Map.entry(163, "windows-1258"), Map.entry(177, "windows-1255"), Map.entry(178, "windows-1256"),
            Map.entry(186, "windows-1257"), Map.entry(204, "windows-1251"), Map.entry(222, "x-windows-874"),
            Map.entry(238, "windows-1250"), Map.entry(255, "IBM437"));

    private enum Destination {
        TEXT, FONT_TABLE, COLOR_TABLE, LIST_TABLE, LIST_OVERRIDES, PN,
        /** In a shape ({@code \shp}, {@code \shpinst}) or a picture's {@code \picprop}: properties. */
        SHAPE, SHAPE_NAME, SHAPE_VALUE, PICT, SKIP
    }

    /** A {@code \pict} being read: its image bytes (hex or {@code \bin}), sizes and properties. */
    private static final class Pict {
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        int nibble = -1;
        boolean supported;
        int pixelWidth;
        int pixelHeight;
        int goalWidth;
        int goalHeight;
        int scaleX = 100;
        int scaleY = 100;
        final Map<String, String> properties = new HashMap<>();
    }

    /** A {@code \shp} being read: its bounds and wrapping in twips, its properties and its picture. */
    private static final class Shape {
        int left;
        int right;
        int top;
        int bottom;
        int wrap = -1;
        int wrapSide = -1;
        final Map<String, String> properties = new HashMap<>();
        Pict pict;
    }

    /** Formatting that RTF groups save and restore. */
    private static final class State implements Cloneable {
        Destination destination = Destination.TEXT;
        int font = -1;
        int halfPoints = 24;
        boolean bold;
        boolean italic;
        boolean underline;
        boolean strike;
        int script; // 1 superscript, -1 subscript
        int color;
        int unicodeSkip = 1;
        int alignment = StyleConstants.ALIGN_LEFT;
        int leftIndent;
        int rightIndent;
        int firstIndent;
        int spaceBefore;
        int spaceAfter;
        int lineSpacing;
        boolean lineMultiple;
        /** Tab stops so far, never changed in place (groups share it): replaced on every change. */
        List<TabStop> tabs = List.of();
        int tabAlignment = TabStop.ALIGN_LEFT;
        int tabLeader = TabStop.LEAD_NONE;
        /** {@code \ls}: the list (override) of the paragraph, 0 for none; {@code \ilvl}: its level. */
        int listOverride;
        int listLevel;
        /** WordPad's {@code \pn} lists: the mark, null for none, and the level. */
        ListFormat pnFormat;
        int pnLevel;
        /** {@code \intbl}: the paragraph is in a table's cell. */
        boolean inTable;

        @Override
        public State clone() {
            try {
                return (State) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }

        void plain() {
            font = -1;
            halfPoints = 24;
            bold = italic = underline = strike = false;
            script = 0;
            color = 0;
        }

        void pard() {
            alignment = StyleConstants.ALIGN_LEFT;
            leftIndent = rightIndent = firstIndent = spaceBefore = spaceAfter = lineSpacing = 0;
            lineMultiple = false;
            tabs = List.of();
            tabAlignment = TabStop.ALIGN_LEFT;
            tabLeader = TabStop.LEAD_NONE;
            listOverride = listLevel = pnLevel = 0;
            pnFormat = null;
            inTable = false;
        }

        /** {@code \tx} or {@code \tb}: a stop with the alignment and leader given just before it. */
        void addTab(int twips) {
            List<TabStop> more = new ArrayList<>(tabs);
            more.add(new TabStop((float) Units.twipsToPoints(twips), tabAlignment, tabLeader));
            tabs = List.copyOf(more);
            tabAlignment = TabStop.ALIGN_LEFT;
            tabLeader = TabStop.LEAD_NONE;
        }
    }

    /** A line of a cell or row: on or off, its width in twips and its colour's number. */
    private static final class Border {
        boolean on;
        int width = 10;
        int color;
    }

    /** A cell as {@code \trowd} describes it, up to its {@code \cellx}. */
    private static final class CellDef {
        int right;
        int background;
        int padding = -1;
        TableAttributes.VerticalAlignment alignment = TableAttributes.VerticalAlignment.TOP;
        final Border top = new Border();
        final Border left = new Border();
        final Border bottom = new Border();
        final Border right_ = new Border();
    }

    /** The row being described: its cells, its left edge, alignment, padding, heading mark and lines. */
    private static final class RowDef {
        final List<CellDef> cells = new ArrayList<>();
        CellDef next = new CellDef();
        int left;
        TableFormat.Alignment alignment = TableFormat.Alignment.LEFT;
        int gap = -1;
        int padding = -1;
        boolean header;
        final Border top = new Border();
        final Border left_ = new Border();
        final Border bottom = new Border();
        final Border right = new Border();
        final Border insideH = new Border();
        final Border insideV = new Border();
    }

    private record FontEntry(String name, Charset charset) {
    }

    /** A {@code \list} of the list table: the numbering style of each level. */
    private static final class ListDefinition {
        final List<ListFormat> levels = new ArrayList<>();

        ListFormat level(int level) {
            if (level < levels.size()) return levels.get(level);
            return levels.isEmpty() ? ListFormat.numberingFor(level) : levels.get(levels.size() - 1);
        }
    }

    private final byte[] in;
    private int pos;
    private final StyledDocument document = Documents.newDocument();
    private final Deque<State> stack = new ArrayDeque<>();
    private State state = new State();
    private final Map<Integer, FontEntry> fonts = new HashMap<>();
    private final List<Color> colors = new ArrayList<>();
    private Charset codepage = Charset.forName("windows-1252");
    private int defaultFont;

    // Font and colour table entries being read
    private int entryFont;
    private Charset entryCharset;
    private final StringBuilder entryName = new StringBuilder();
    private int red = -1;
    private int green = -1;
    private int blue = -1;

    // Text being collected
    private final StringBuilder text = new StringBuilder();
    private State textState;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private int skipChars;
    private int paragraphStart;
    private boolean groupStart;
    private boolean ignorable;
    private boolean rootClosed;

    // Lists: definitions by \listid, overrides \ls -> \listid, our list ids by \ls
    private final Map<Integer, ListDefinition> lists = new HashMap<>();
    private final Map<Integer, Integer> overrides = new HashMap<>();
    private final Map<Integer, Integer> listIds = new HashMap<>();
    private ListDefinition currentList;
    private int overrideListId;
    private int nextListId = 1;
    /** While reading a {@code \pn} group. */
    private boolean pnNumbered;
    private ListFormat pnNumbering = ListFormat.DECIMAL;
    private int pnLevel;
    /** The list of the last paragraph if it was a WordPad list item, so that the next one continues it. */
    private int lastPnList;

    // Tables: the row described last, the table being read, its first row, the cell's first paragraph
    private RowDef rowDef = new RowDef();
    private Border border;
    private TableBuilder table;
    private RowDef firstRow;
    private boolean tableHeader;
    private int cellFrom = -1;
    private int cellInRow;

    // Page setup
    private int paperWidth = -1;
    private int paperHeight = -1;
    private final int[] margins = {-1, -1, -1, -1}; // top, left, bottom, right in twips
    private boolean landscape;

    private RtfReader(byte[] in) {
        this.in = in;
    }

    static DocumentContent read(byte[] bytes) throws IOException {
        return new RtfReader(bytes).parse();
    }

    // Pictures and shapes being read
    private Shape shape;
    private Pict pict;
    private Map<String, String> properties;
    private final StringBuilder propertyName = new StringBuilder();
    private final StringBuilder propertyValue = new StringBuilder();

    private DocumentContent parse() throws IOException {
        skipWhitespace();
        if (!startsWith("{\\rtf")) throw new IOException("Not an RTF file");
        while (pos < in.length) {
            int c = in[pos] & 0xFF;
            if (c == '{') {
                pos++;
                flushBytes();
                stack.push(state);
                state = state.clone();
                groupStart = true;
                ignorable = false;
                skipChars = 0;
            } else if (c == '}') {
                pos++;
                endGroup();
            } else if (c == '\\') {
                pos++;
                control();
            } else if (c == '\r' || c == '\n') {
                pos++;
            } else {
                pos++;
                groupStart = false;
                if (c >= 0x80) {
                    addByte(c);
                } else {
                    flushBytes();
                    addChar((char) c);
                }
            }
        }
        if (!rootClosed) {
            flushBytes();
            flushText();
            finishParagraph(false);
            endTable();
        }
        removeTrailingEmptyParagraph();
        return new DocumentContent(document, settings());
    }

    // ---------------------------------------------------------------- tokens

    private void control() {
        if (pos >= in.length) return;
        int c = in[pos] & 0xFF;
        if (isLetter(c)) {
            int start = pos;
            while (pos < in.length && isLetter(in[pos] & 0xFF)) pos++;
            String word = new String(in, start, pos - start, java.nio.charset.StandardCharsets.US_ASCII);
            boolean hasParameter = false;
            int parameter = 0;
            if (pos < in.length && (in[pos] == '-' || isDigit(in[pos] & 0xFF))) {
                int numberStart = pos;
                if (in[pos] == '-') pos++;
                while (pos < in.length && isDigit(in[pos] & 0xFF)) pos++;
                try {
                    parameter = Integer.parseInt(new String(in, numberStart, pos - numberStart,
                            java.nio.charset.StandardCharsets.US_ASCII));
                    hasParameter = true;
                } catch (NumberFormatException e) {
                    parameter = 0; // a lone '-' or an absurd number
                }
            }
            if (pos < in.length && in[pos] == ' ') pos++;
            word(word, hasParameter, parameter);
            return;
        }
        pos++;
        switch (c) {
            case '\'' -> {
                if (pos + 2 <= in.length) {
                    int value = hex(in[pos] & 0xFF) * 16 + hex(in[pos + 1] & 0xFF);
                    pos += 2;
                    groupStart = false;
                    if (value >= 0) addByte(value);
                }
            }
            case '*' -> ignorable = true;
            case '\\', '{', '}' -> literal((char) c);
            case '~' -> literal(' ');
            case '_' -> literal('‑');
            case '-' -> {
                // optional hyphen: not shown
            }
            case '\n', '\r' -> word("par", false, 0);
            case '\t' -> literal('\t');
            default -> {
                // \: \| and others carry no text
            }
        }
    }

    private void word(String word, boolean hasParameter, int parameter) {
        boolean first = groupStart;
        groupStart = false;
        boolean on = !hasParameter || parameter != 0;
        if (first) {
            if (state.destination == Destination.SKIP) return;
            if (state.destination == Destination.LIST_TABLE) {
                if (word.equals("list")) {
                    currentList = new ListDefinition();
                    return;
                }
                if (word.equals("listlevel")) {
                    if (currentList != null) currentList.levels.add(ListFormat.DECIMAL);
                    return;
                }
            }
            if (word.equals("listtable")) {
                state.destination = Destination.LIST_TABLE;
                return;
            }
            if (word.equals("listoverridetable")) {
                state.destination = Destination.LIST_OVERRIDES;
                return;
            }
            if (word.equals("pn") && state.destination == Destination.TEXT) {
                flushText();
                state.destination = Destination.PN;
                pnNumbered = false;
                pnNumbering = ListFormat.DECIMAL;
                pnLevel = 0;
                return;
            }
            if (pictureGroup(word)) return;
            if (word.equals("fonttbl")) {
                state.destination = Destination.FONT_TABLE;
                return;
            }
            if (word.equals("colortbl")) {
                state.destination = Destination.COLOR_TABLE;
                return;
            }
            if (SKIPPED.contains(word) || (ignorable && !word.equals("fldrslt"))) {
                flushText();
                state.destination = Destination.SKIP;
                return;
            }
        }
        switch (state.destination) {
            case SKIP -> {
                if (word.equals("bin") && hasParameter) pos = Math.min(in.length, pos + Math.max(0, parameter));
            }
            case FONT_TABLE -> fontTableWord(word, parameter);
            case COLOR_TABLE -> {
                switch (word) {
                    case "red" -> red = parameter;
                    case "green" -> green = parameter;
                    case "blue" -> blue = parameter;
                    default -> {
                    }
                }
            }
            case LIST_TABLE -> {
                switch (word) {
                    case "levelnfc", "levelnfcn" -> {
                        if (currentList != null && !currentList.levels.isEmpty()) {
                            currentList.levels.set(currentList.levels.size() - 1, ListFormat.ofRtf(parameter));
                        }
                    }
                    case "listid" -> {
                        if (currentList != null) lists.put(parameter, currentList);
                    }
                    default -> {
                    }
                }
            }
            case LIST_OVERRIDES -> {
                switch (word) {
                    case "listid" -> overrideListId = parameter;
                    case "ls" -> overrides.put(parameter, overrideListId);
                    default -> {
                    }
                }
            }
            case PN -> pnWord(word, parameter);
            case SHAPE -> shapeWord(word, parameter);
            case PICT -> pictWord(word, hasParameter, parameter);
            case SHAPE_NAME, SHAPE_VALUE -> {
                if (word.equals("u")) unicode(parameter);
            }
            case TEXT -> textWord(word, hasParameter, parameter, on);
        }
    }

    /** WordPad's lists: {@code {\*\pn\pnlvlblt ...}} for bullets, {@code \pnlvlbody\pndec} for numbers. */
    private void pnWord(String word, int parameter) {
        switch (word) {
            case "pnlvlblt" -> {
                pnNumbered = false;
                pnLevel = 0;
            }
            case "pnlvlbody" -> {
                pnNumbered = true;
                pnLevel = 0;
            }
            case "pnlvl" -> {
                pnNumbered = true;
                pnLevel = Math.max(0, Math.min(ListAttributes.MAX_LEVEL, parameter - 1));
            }
            case "pndec" -> pnNumbering = ListFormat.DECIMAL;
            case "pnlcltr" -> pnNumbering = ListFormat.LOWER_LETTER;
            case "pnucltr" -> pnNumbering = ListFormat.UPPER_LETTER;
            case "pnlcrm" -> pnNumbering = ListFormat.LOWER_ROMAN;
            case "pnucrm" -> pnNumbering = ListFormat.UPPER_ROMAN;
            default -> {
            }
        }
    }

    private void fontTableWord(String word, int parameter) {
        switch (word) {
            case "f" -> {
                entryFont = parameter;
                entryCharset = null;
                entryName.setLength(0);
            }
            case "fcharset" -> entryCharset = charset(CHARSET_CODEPAGES.get(parameter));
            case "cpg" -> entryCharset = codepage(parameter);
            default -> {
            }
        }
    }

    private void textWord(String word, boolean hasParameter, int parameter, boolean on) {
        switch (word) {
            // document
            case "ansicpg" -> codepage = codepage(parameter);
            case "deff" -> defaultFont = parameter;
            case "paperw", "pgwsxn" -> paperWidth = parameter;
            case "paperh", "pghsxn" -> paperHeight = parameter;
            case "margt", "margtsxn" -> margins[0] = parameter;
            case "margl", "marglsxn" -> margins[1] = parameter;
            case "margb", "margbsxn" -> margins[2] = parameter;
            case "margr", "margrsxn" -> margins[3] = parameter;
            case "landscape", "lndscpsxn" -> landscape = true;
            case "bin" -> {
                if (hasParameter) pos = Math.min(in.length, pos + Math.max(0, parameter));
            }
            // text
            case "par", "sect" -> paragraph();
            case "line" -> lineBreak();
            case "cell" -> cell();
            case "row" -> row();
            case "nestcell", "nestrow" -> paragraph();
            case "tab" -> literal('\t');
            case "emdash" -> literal('—');
            case "endash" -> literal('–');
            case "emspace", "enspace", "qmspace" -> literal(' ');
            case "bullet" -> literal('•');
            case "lquote" -> literal('‘');
            case "rquote" -> literal('’');
            case "ldblquote" -> literal('“');
            case "rdblquote" -> literal('”');
            case "u" -> unicode(parameter);
            case "uc" -> state.unicodeSkip = Math.max(0, parameter);
            // character formatting
            case "plain" -> change(() -> state.plain());
            case "f" -> change(() -> state.font = parameter);
            case "fs" -> change(() -> state.halfPoints = hasParameter && parameter > 0 ? parameter : 24);
            case "b" -> change(() -> state.bold = on);
            case "i" -> change(() -> state.italic = on);
            case "strike", "striked" -> change(() -> state.strike = on);
            case "ul", "uld", "uldb", "uldash", "uldashd", "uldashdd", "ulth", "ulw", "ulwave", "ulhwave",
                 "ulldash", "ulthd", "ulthdash", "ulthdashd", "ulthdashdd", "ulthldash", "ululdbwave" ->
                    change(() -> state.underline = on);
            case "ulnone" -> change(() -> state.underline = false);
            case "super" -> change(() -> state.script = 1);
            case "sub" -> change(() -> state.script = -1);
            case "nosupersub" -> change(() -> state.script = 0);
            case "cf" -> change(() -> state.color = parameter);
            // paragraph formatting
            case "pard" -> state.pard();
            case "ql" -> state.alignment = StyleConstants.ALIGN_LEFT;
            case "qc" -> state.alignment = StyleConstants.ALIGN_CENTER;
            case "qr" -> state.alignment = StyleConstants.ALIGN_RIGHT;
            case "qj", "qd" -> state.alignment = StyleConstants.ALIGN_JUSTIFIED;
            case "li", "lin" -> state.leftIndent = parameter;
            case "ri", "rin" -> state.rightIndent = parameter;
            case "fi" -> state.firstIndent = parameter;
            case "sb" -> state.spaceBefore = parameter;
            case "sa" -> state.spaceAfter = parameter;
            case "sl" -> state.lineSpacing = parameter;
            case "slmult" -> state.lineMultiple = on;
            case "tqc" -> state.tabAlignment = TabStop.ALIGN_CENTER;
            case "tqr" -> state.tabAlignment = TabStop.ALIGN_RIGHT;
            case "tqdec" -> state.tabAlignment = TabStop.ALIGN_DECIMAL;
            case "tldot", "tlmdot" -> state.tabLeader = TabStop.LEAD_DOTS;
            case "tlhyph" -> state.tabLeader = TabStop.LEAD_HYPHENS;
            case "tlul" -> state.tabLeader = TabStop.LEAD_UNDERLINE;
            case "tlth" -> state.tabLeader = TabStop.LEAD_THICKLINE;
            case "tleq" -> state.tabLeader = TabStop.LEAD_EQUALS;
            case "tx", "tb" -> {
                if (hasParameter && parameter >= 0) state.addTab(parameter);
            }
            case "intbl" -> state.inTable = true;
            case "itap" -> state.inTable = parameter > 0;
            case "ls" -> state.listOverride = parameter;
            case "ilvl" -> state.listLevel = Math.max(0, Math.min(ListAttributes.MAX_LEVEL, parameter));
            default -> tableWord(word, hasParameter, parameter);
        }
    }

    // ---------------------------------------------------------------- tables

    // --8<-- [start:table]
    /** The words that describe a table's row and its cells. */
    private void tableWord(String word, boolean hasParameter, int parameter) {
        CellDef next = rowDef.next;
        switch (word) {
            case "trowd" -> {
                rowDef = new RowDef();
                border = null;
            }
            case "trleft" -> rowDef.left = parameter;
            case "trgaph" -> rowDef.gap = Math.max(0, parameter);
            case "trpaddl" -> rowDef.padding = Math.max(0, parameter);
            case "trqc" -> rowDef.alignment = TableFormat.Alignment.CENTER;
            case "trqr" -> rowDef.alignment = TableFormat.Alignment.RIGHT;
            case "trql" -> rowDef.alignment = TableFormat.Alignment.LEFT;
            case "trhdr" -> rowDef.header = true;
            case "trbrdrt" -> border = rowDef.top;
            case "trbrdrl" -> border = rowDef.left_;
            case "trbrdrb" -> border = rowDef.bottom;
            case "trbrdrr" -> border = rowDef.right;
            case "trbrdrh" -> border = rowDef.insideH;
            case "trbrdrv" -> border = rowDef.insideV;
            case "clbrdrt" -> border = next.top;
            case "clbrdrl" -> border = next.left;
            case "clbrdrb" -> border = next.bottom;
            case "clbrdrr" -> border = next.right_;
            case "brdrnone", "brdrnil" -> {
                if (border != null) border.on = false;
            }
            case "brdrw" -> {
                if (border != null && hasParameter) border.width = Math.max(0, parameter);
            }
            case "brdrcf" -> {
                if (border != null) border.color = parameter;
            }
            case "clcbpat" -> next.background = parameter;
            case "clpadl" -> next.padding = Math.max(0, parameter);
            case "clvertalt" -> next.alignment = TableAttributes.VerticalAlignment.TOP;
            case "clvertalc" -> next.alignment = TableAttributes.VerticalAlignment.CENTER;
            case "clvertalb" -> next.alignment = TableAttributes.VerticalAlignment.BOTTOM;
            case "cellx" -> {
                next.right = parameter;
                rowDef.cells.add(next);
                rowDef.next = new CellDef();
                border = null;
            }
            default -> {
                // \brdrs, \brdrdb, \brdrth and the other line styles: a line
                if (border != null && word.startsWith("brdr") && word.length() > 4) border.on = true;
            }
        }
    }

    /**
     * {@code \cell}: the paragraph ends the cell. The cell is the paragraphs since the last cell (or the
     * first {@code \intbl} paragraph), with the background and alignment of its {@code \cellx} entry.
     */
    private void cell() {
        flushBytes();
        flushText();
        if (cellFrom < 0) cellFrom = paragraphStart;
        insert("\n", state);
        finishParagraph(true);
        if (table == null) {
            table = new TableBuilder(document);
            firstRow = rowDef;
            tableHeader = false;
            cellInRow = 0;
        }
        if (cellInRow == 0) table.row();
        CellDef def = cellInRow < rowDef.cells.size() ? rowDef.cells.get(cellInRow) : null;
        Color background = def != null && def.background > 0 && def.background < colors.size()
                ? colors.get(def.background) : null;
        table.cellStart(cellFrom);
        table.cellEnd(background, def != null ? def.alignment : null, characterAttributes(state));
        cellInRow++;
        cellFrom = -1;
    }

    /** {@code \row}: the row is complete; a heading row at the top repeats on each page. */
    private void row() {
        flushBytes();
        flushText();
        if (table == null) return;
        if (table.rows() == 1 && rowDef.header) tableHeader = true;
        cellInRow = 0;
        cellFrom = -1;
    }

    /** Makes the paragraphs read since the table began a table, in the format of its first row. */
    private void endTable() {
        if (table == null) return;
        TableBuilder done = table;
        table = null;
        cellFrom = -1;
        cellInRow = 0;
        RowDef first = firstRow;
        List<Double> widths = new ArrayList<>();
        int x = first.left;
        for (CellDef cell : first.cells) {
            widths.add(Math.max(TableFormat.MIN_COLUMN, Units.twipsToPoints(cell.right - x)));
            x = Math.max(x, cell.right);
        }
        int columns = Math.max(1, done.columns());
        double[] w = new double[columns];
        for (int c = 0; c < columns; c++) {
            w[c] = c < widths.size() ? widths.get(c) : settings().contentWidth() / columns;
        }
        TableFormat f = TableFormat.of(w).withRepeatHeader(tableHeader).withAlignment(first.alignment);
        CellDef a = first.cells.isEmpty() ? new CellDef() : first.cells.getFirst();
        CellDef b = first.cells.size() > 1 ? first.cells.get(1) : null;
        Border left = a.left.on ? a.left : first.left_;
        Border top = a.top.on ? a.top : first.top;
        Border insideV = a.right_.on ? a.right_ : b != null && b.left.on ? b.left : first.insideV;
        Border insideH = a.bottom.on ? a.bottom : first.insideH;
        f = f.withLines(TableLines.guess(done.columns() > 1 && insideV.on, done.rows() > 1 && insideH.on,
                left.on, top.on));
        for (Border line : new Border[]{left, top, insideV, insideH}) {
            if (!line.on) continue;
            f = f.withLineWidth((float) Math.max(0.25, Units.twipsToPoints(line.width)));
            if (line.color > 0 && line.color < colors.size() && colors.get(line.color) != null) {
                f = f.withLineColor(colors.get(line.color));
            }
            break;
        }
        // LibreOffice writes the padding per cell, Word per row, older programs only the gap between cells.
        int padding = a.padding > 0 ? a.padding : first.padding > 0 ? first.padding : first.gap;
        if (padding >= 0) f = f.withPadding(Units.twipsToPoints(padding));
        done.finish(f);
    }
    // --8<-- [end:table]

    private void endGroup() {
        flushBytes();
        State inner = state;
        if (inner.destination == Destination.FONT_TABLE && entryName.length() > 0) addFont();
        boolean pnEnded = inner.destination == Destination.PN;
        if (stack.size() == 1 && !rootClosed) {
            // The end of the document: the last paragraph keeps the formatting in effect inside it.
            flushText();
            finishParagraph(false);
            endTable();
            rootClosed = true;
        }
        if (inner.destination == Destination.TEXT && !stack.isEmpty() && differs(inner, stack.peek())) flushText();
        state = stack.isEmpty() ? new State() : stack.pop();
        if (inner.destination == Destination.SHAPE || inner.destination == Destination.PICT) {
            endPictureGroup(inner.destination);
        }
        if (pnEnded) {
            // The group only describes the list: the paragraph around it is the list item.
            state.pnFormat = pnNumbered ? pnNumbering : ListFormat.BULLET;
            state.pnLevel = pnLevel;
        }
        groupStart = false;
        ignorable = false;
        skipChars = 0;
    }

    // ---------------------------------------------------------------- pictures

    // --8<-- [start:pictures]
    /**
     * The first word of a group that belongs to a picture: {@code \shp} starts a shape, {@code \pict} a
     * picture (in the text or as a shape's {@code pib} property); {@code \shppict} holds Word's picture
     * (its {@code \nonshppict} twin, an old-style copy, is skipped). Other groups inside are skipped.
     */
    private boolean pictureGroup(String word) {
        Destination d = state.destination;
        if (d == Destination.TEXT && word.equals("shppict")) return true;
        if (d == Destination.TEXT && word.equals("shp")) {
            flushText();
            shape = new Shape();
            properties = shape.properties;
            state.destination = Destination.SHAPE;
            return true;
        }
        if (word.equals("pict") && (d == Destination.TEXT || d == Destination.SHAPE_VALUE)) {
            flushText();
            pict = new Pict();
            state.destination = Destination.PICT;
            return true;
        }
        if (d == Destination.PICT && word.equals("picprop")) {
            properties = pict.properties;
            state.destination = Destination.SHAPE;
            return true;
        }
        if (d == Destination.SHAPE && (word.equals("shpinst") || word.equals("sp"))) {
            propertyName.setLength(0);
            propertyValue.setLength(0);
            return true;
        }
        if (d == Destination.SHAPE && (word.equals("sn") || word.equals("sv"))) {
            state.destination = word.equals("sn") ? Destination.SHAPE_NAME : Destination.SHAPE_VALUE;
            return true;
        }
        if (d == Destination.SHAPE || d == Destination.SHAPE_NAME || d == Destination.SHAPE_VALUE
                || d == Destination.PICT) {
            state.destination = Destination.SKIP; // \shprslt, \shptxt, \blipuid and the like
            return true;
        }
        return false;
    }

    private void shapeWord(String word, int parameter) {
        if (shape == null) return;
        switch (word) {
            case "shpleft" -> shape.left = parameter;
            case "shpright" -> shape.right = parameter;
            case "shptop" -> shape.top = parameter;
            case "shpbottom" -> shape.bottom = parameter;
            case "shpwr" -> shape.wrap = parameter;
            case "shpwrk" -> shape.wrapSide = parameter;
            default -> {
            }
        }
    }

    private void pictWord(String word, boolean hasParameter, int parameter) {
        switch (word) {
            case "picw" -> pict.pixelWidth = parameter;
            case "pich" -> pict.pixelHeight = parameter;
            case "picwgoal" -> pict.goalWidth = parameter;
            case "pichgoal" -> pict.goalHeight = parameter;
            case "picscalex" -> pict.scaleX = parameter > 0 ? parameter : 100;
            case "picscaley" -> pict.scaleY = parameter > 0 ? parameter : 100;
            case "pngblip", "jpegblip" -> pict.supported = true;
            case "bin" -> {
                int n = hasParameter ? Math.max(0, Math.min(parameter, in.length - pos)) : 0;
                pict.data.write(in, pos, n);
                pos += n;
            }
            default -> {
            }
        }
    }

    private void pictHex(char c) {
        int value = hex(c);
        if (value < 0) return; // line ends and spaces between the digits
        if (pict.nibble < 0) {
            pict.nibble = value;
        } else {
            pict.data.write(pict.nibble * 16 + value);
            pict.nibble = -1;
        }
    }

    /** The end of a group of a picture or shape: a property, a picture or a whole shape is complete. */
    private void endPictureGroup(Destination inner) {
        Destination outer = state.destination;
        if (inner == Destination.SHAPE && (outer == Destination.SHAPE || outer == Destination.PICT)) {
            if (!propertyName.isEmpty() && properties != null) {
                properties.put(propertyName.toString().strip(), propertyValue.toString().strip());
            }
            propertyName.setLength(0);
            propertyValue.setLength(0);
            return;
        }
        if (inner == Destination.PICT && outer != Destination.PICT && pict != null) {
            Pict done = pict;
            pict = null;
            properties = shape != null ? shape.properties : null;
            if (outer == Destination.SHAPE_VALUE && shape != null) shape.pict = done;
            else if (outer == Destination.TEXT) insertPicture(inlinePicture(done));
            return;
        }
        if (inner == Destination.SHAPE && outer == Destination.TEXT && shape != null) {
            Shape done = shape;
            shape = null;
            properties = null;
            insertPicture(shapePicture(done));
        }
    }

    private Picture inlinePicture(Pict p) {
        Picture picture = decode(p);
        if (picture == null) return null;
        Picture.Border border = border(p.properties);
        double w = p.goalWidth > 0 ? Units.twipsToPoints(p.goalWidth) * p.scaleX / 100 : picture.width();
        double h = p.goalHeight > 0 ? Units.twipsToPoints(p.goalHeight) * p.scaleY / 100 : w * picture.aspect();
        return picture.withLayout(Math.max(2, w), Math.max(2, h), Picture.Placement.IN_LINE, border,
                p.properties.getOrDefault("wzDescription", ""));
    }

    /**
     * A picture shape: its bounds are the picture with its border. It floats if the text wraps around it
     * (square, tight or through), at the right if it is positioned there or the text wraps only on its left.
     */
    private Picture shapePicture(Shape s) {
        Pict p = s.pict;
        if (p == null) return null;
        Picture picture = inlinePicture(p);
        if (picture == null) return null;
        Map<String, String> props = new HashMap<>(p.properties);
        props.putAll(s.properties);
        Picture.Border border = border(props);
        double frame = 2 * border.width();
        double w = s.right > s.left ? Units.twipsToPoints(s.right - s.left) - frame : picture.width();
        double h = s.bottom > s.top ? Units.twipsToPoints(s.bottom - s.top) - frame : picture.height();
        Picture.Placement placement = Picture.Placement.IN_LINE;
        if (s.wrap == 2 || s.wrap == 4 || s.wrap == 5) {
            boolean right = "3".equals(props.get("posh")) || s.wrapSide == 1;
            placement = right ? Picture.Placement.FLOAT_RIGHT : Picture.Placement.FLOAT_LEFT;
        }
        return picture.withLayout(Math.max(2, w), Math.max(2, h), placement, border,
                props.getOrDefault("wzDescription", ""));
    }

    private static Picture decode(Pict p) {
        if (!p.supported || p.data.size() == 0) return null;
        try {
            byte[] data = p.data.toByteArray();
            return Picture.of(data, "image/jpeg".equals(Picture.mimeType(data)) ? "picture.jpg" : "picture.png");
        } catch (IOException e) {
            return null; // not an image Java can read
        }
    }

    /** The border from shape properties: a line only if {@code fLine} is on, its width in EMU. */
    private static Picture.Border border(Map<String, String> props) {
        if (!"1".equals(props.get("fLine")) && !props.containsKey("lineWidth")) return Picture.Border.NONE;
        if ("0".equals(props.get("fLine"))) return Picture.Border.NONE;
        double points;
        try {
            points = Long.parseLong(props.getOrDefault("lineWidth", "9525")) / 12700.0;
        } catch (NumberFormatException e) {
            points = 0.75;
        }
        return points > 1.2 ? Picture.Border.THICK : Picture.Border.THIN;
    }

    private void insertPicture(Picture picture) {
        if (picture == null) return;
        flushBytes();
        flushText();
        try {
            document.insertString(document.getLength(), String.valueOf(PictureAttributes.OBJECT),
                    PictureAttributes.attributes(characterAttributes(state), picture));
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }
    // --8<-- [end:pictures]

    // ---------------------------------------------------------------- text

    private void literal(char c) {
        groupStart = false;
        flushBytes();
        addChar(c);
    }

    private void unicode(int value) {
        flushBytes();
        int code = value < 0 ? value + 65536 : value;
        if (state.destination == Destination.TEXT) addTextChar((char) code);
        else if (state.destination == Destination.FONT_TABLE) entryName.append((char) code);
        else if (state.destination == Destination.SHAPE_NAME) propertyName.append((char) code);
        else if (state.destination == Destination.SHAPE_VALUE) propertyValue.append((char) code);
        skipChars = state.unicodeSkip;
    }

    private void addByte(int value) {
        if (skipChars > 0) {
            skipChars--;
            return;
        }
        bytes.write(value);
    }

    /** Decodes collected 8-bit bytes in the code page of the current font, so double-byte pairs stay whole. */
    private void flushBytes() {
        if (bytes.size() == 0) return;
        Charset charset = codepage;
        if (state.destination == Destination.FONT_TABLE) {
            if (entryCharset != null) charset = entryCharset;
        } else {
            FontEntry font = fonts.get(state.font >= 0 ? state.font : defaultFont);
            if (font != null && font.charset() != null) charset = font.charset();
        }
        String decoded = new String(bytes.toByteArray(), charset);
        bytes.reset();
        for (int i = 0; i < decoded.length(); i++) addDecoded(decoded.charAt(i));
    }

    private void addChar(char c) {
        if (skipChars > 0) {
            skipChars--;
            return;
        }
        addDecoded(c);
    }

    private void addDecoded(char c) {
        switch (state.destination) {
            case TEXT -> addTextChar(c);
            case FONT_TABLE -> {
                if (c == ';') addFont();
                else entryName.append(c);
            }
            case COLOR_TABLE -> {
                if (c == ';') {
                    colors.add(red < 0 && green < 0 && blue < 0 ? null
                            : new Color(clamp(red), clamp(green), clamp(blue)));
                    red = green = blue = -1;
                }
            }
            case SHAPE_NAME -> propertyName.append(c);
            case SHAPE_VALUE -> propertyValue.append(c);
            case PICT -> pictHex(c);
            default -> {
            }
        }
    }

    private void addTextChar(char c) {
        if (textState != null && differs(textState, state)) flushText();
        if (textState == null) textState = state.clone();
        text.append(c);
    }

    /** Runs a formatting change, writing out the text formatted the old way first. */
    private void change(Runnable change) {
        flushBytes();
        change.run();
    }

    private void flushText() {
        if (text.isEmpty()) {
            textState = null;
            return;
        }
        insert(text.toString(), textState);
        text.setLength(0);
        textState = null;
    }

    private void paragraph() {
        flushBytes();
        flushText();
        if (state.inTable && cellFrom < 0) cellFrom = paragraphStart;
        insert("\n", state);
        finishParagraph(true);
        if (!state.inTable) endTable();
    }

    /** {@code \line}: a line break within the paragraph. */
    private void lineBreak() {
        flushBytes();
        flushText();
        insert(String.valueOf(WritepadDocument.LINE_BREAK), state);
    }

    private void insert(String s, State format) {
        try {
            document.insertString(document.getLength(), s, characterAttributes(format));
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Gives the paragraph that just ended (or the last one) the paragraph formatting in effect. */
    private void finishParagraph(boolean ended) {
        int end = document.getLength();
        int length = Math.max(1, end - paragraphStart);
        document.setParagraphAttributes(paragraphStart, length, paragraphAttributes(state), false);
        lastPnList = state.pnFormat != null ? pnListId : 0;
        if (ended) paragraphStart = end;
    }

    /** "...\par}" ends many files: the empty paragraph after the last \par is not part of the text. */
    private void removeTrailingEmptyParagraph() {
        int length = document.getLength();
        try {
            // A table at the end keeps the empty paragraph after it: every cell ends in a paragraph of its own.
            if (length > 0 && document.getText(length - 1, 1).equals("\n")
                    && !Tables.inTable(document, length - 1)) {
                document.remove(length - 1, 1);
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    private SimpleAttributeSet characterAttributes(State s) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        FontEntry font = fonts.get(s.font >= 0 ? s.font : defaultFont);
        StyleConstants.setFontFamily(a, font != null && !font.name().isBlank() ? font.name() : Documents.DEFAULT_FAMILY);
        StyleConstants.setFontSize(a, Math.max(1, (s.halfPoints + 1) / 2));
        if (s.bold) StyleConstants.setBold(a, true);
        if (s.italic) StyleConstants.setItalic(a, true);
        if (s.underline) StyleConstants.setUnderline(a, true);
        if (s.strike) StyleConstants.setStrikeThrough(a, true);
        if (s.script > 0) StyleConstants.setSuperscript(a, true);
        if (s.script < 0) StyleConstants.setSubscript(a, true);
        if (s.color > 0 && s.color < colors.size() && colors.get(s.color) != null) {
            StyleConstants.setForeground(a, colors.get(s.color));
        }
        return a;
    }

    private int pnListId;

    private SimpleAttributeSet paragraphAttributes(State s) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setAlignment(a, s.alignment);
        StyleConstants.setLeftIndent(a, (float) Units.twipsToPoints(s.leftIndent));
        StyleConstants.setRightIndent(a, (float) Units.twipsToPoints(s.rightIndent));
        StyleConstants.setFirstLineIndent(a, (float) Units.twipsToPoints(s.firstIndent));
        StyleConstants.setSpaceAbove(a, (float) Units.twipsToPoints(s.spaceBefore));
        StyleConstants.setSpaceBelow(a, (float) Units.twipsToPoints(s.spaceAfter));
        // Swing's line spacing is the extra space as a fraction of the line: 1.15 lines -> 0.15.
        float extra = s.lineMultiple && s.lineSpacing > 0 ? s.lineSpacing / 240f - 1 : 0;
        StyleConstants.setLineSpacing(a, Math.max(0, extra));
        if (!s.tabs.isEmpty()) {
            List<TabStop> sorted = new ArrayList<>(s.tabs);
            sorted.sort((x, y) -> Float.compare(x.getPosition(), y.getPosition()));
            StyleConstants.setTabSet(a, new TabSet(sorted.toArray(TabStop[]::new)));
        }
        if (s.listOverride > 0) {
            int list = listIds.computeIfAbsent(s.listOverride, ls -> nextListId++);
            ListDefinition definition = lists.get(overrides.getOrDefault(s.listOverride, -1));
            ListFormat format = definition != null ? definition.level(s.listLevel) : ListFormat.numberingFor(s.listLevel);
            ListAttributes.set(a, list, s.listLevel, format);
        } else if (s.pnFormat != null) {
            pnListId = lastPnList != 0 ? lastPnList : nextListId++;
            ListAttributes.set(a, pnListId, s.pnLevel, s.pnFormat);
        }
        return a;
    }

    private static boolean differs(State a, State b) {
        return a.font != b.font || a.halfPoints != b.halfPoints || a.bold != b.bold || a.italic != b.italic
                || a.underline != b.underline || a.strike != b.strike || a.script != b.script || a.color != b.color;
    }

    private void addFont() {
        String name = entryName.toString().strip();
        if (name.endsWith(";")) name = name.substring(0, name.length() - 1).strip();
        fonts.put(entryFont, new FontEntry(name, entryCharset));
        entryName.setLength(0);
    }

    // ---------------------------------------------------------------- page setup

    private DocumentSettings settings() {
        if (paperWidth <= 0 || paperHeight <= 0) return withMargins(DocumentSettings.DEFAULT);
        double shortSide = Units.twipsToPoints(Math.min(paperWidth, paperHeight));
        double longSide = Units.twipsToPoints(Math.max(paperWidth, paperHeight));
        PaperSize paper = PaperSize.A4;
        double best = Double.MAX_VALUE;
        for (PaperSize candidate : PaperSize.values()) {
            double distance = Math.abs(candidate.width() - shortSide) + Math.abs(candidate.height() - longSide);
            if (distance < best) {
                best = distance;
                paper = candidate;
            }
        }
        Orientation orientation = landscape || paperWidth > paperHeight ? Orientation.LANDSCAPE : Orientation.PORTRAIT;
        return withMargins(new DocumentSettings(paper, orientation, Margins.DEFAULT));
    }

    private DocumentSettings withMargins(DocumentSettings settings) {
        Margins current = settings.margins();
        Margins read = new Margins(points(margins[0], current.top()), points(margins[1], current.left()),
                points(margins[2], current.bottom()), points(margins[3], current.right()));
        try {
            return settings.withMargins(read);
        } catch (IllegalArgumentException e) {
            return settings; // margins that leave no room for text
        }
    }

    private static double points(int twips, double fallback) {
        return twips >= 0 ? Units.twipsToPoints(twips) : fallback;
    }

    // ---------------------------------------------------------------- helpers

    private static Charset codepage(int number) {
        return switch (number) {
            case 437, 850, 852, 855, 857, 860, 861, 862, 863, 864, 865, 866, 869 -> charset("IBM" + number);
            case 874 -> charset("x-windows-874");
            case 932 -> charset("windows-31j");
            case 936 -> charset("GBK");
            case 949 -> charset("x-windows-949");
            case 950 -> charset("x-windows-950");
            case 10000 -> charset("x-MacRoman");
            case 65001 -> charset("UTF-8");
            default -> charset("windows-" + number);
        };
    }

    private static Charset charset(String name) {
        if (name == null) return null;
        try {
            return Charset.forName(name);
        } catch (IllegalArgumentException e) {
            return Charset.forName("windows-1252");
        }
    }

    private boolean startsWith(String prefix) {
        if (pos + prefix.length() > in.length) return false;
        for (int i = 0; i < prefix.length(); i++) {
            if (in[pos + i] != prefix.charAt(i)) return false;
        }
        return true;
    }

    private void skipWhitespace() {
        // A UTF-8 byte order mark or blank lines before the RTF header are tolerated.
        if (in.length >= 3 && (in[0] & 0xFF) == 0xEF && (in[1] & 0xFF) == 0xBB && (in[2] & 0xFF) == 0xBF) pos = 3;
        while (pos < in.length && Character.isWhitespace(in[pos])) pos++;
    }

    private static boolean isLetter(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private static int hex(int c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -100;
    }

    private static int clamp(int component) {
        return Math.max(0, Math.min(255, component));
    }
}
