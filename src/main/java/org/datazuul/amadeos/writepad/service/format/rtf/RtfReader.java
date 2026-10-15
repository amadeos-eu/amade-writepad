package org.datazuul.amadeos.writepad.service.format.rtf;

import org.datazuul.amadeos.writepad.domain.DocumentSettings;
import org.datazuul.amadeos.writepad.domain.Margins;
import org.datazuul.amadeos.writepad.domain.Orientation;
import org.datazuul.amadeos.writepad.domain.PaperSize;
import org.datazuul.amadeos.writepad.domain.Units;
import org.datazuul.amadeos.writepad.service.format.DocumentContent;
import org.datazuul.amadeos.writepad.service.format.Documents;

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
 * super-/subscript, colours, alignment, indents, spacing, line spacing, tab stops and the page setup. Everything else
 * (pictures, headers, footnotes, fields' instructions, list definitions, style sheets, document info) is
 * skipped, so unknown content never breaks the import or appears as garbage.
 *
 * <p>Handles the character sets RTF files use: the document code page ({@code \ansicpg}), per-font
 * charsets ({@code \fcharset}, including double-byte code pages), raw 8-bit bytes and Unicode
 * (<code>&#92;u</code> with <code>&#92;uc</code> fallback characters).
 */
final class RtfReader {
    /** Destinations whose content is not text of the document. */
    private static final Set<String> SKIPPED = Set.of(
            "stylesheet", "info", "pict", "header", "headerl", "headerr", "headerf", "footer", "footerl",
            "footerr", "footerf", "footnote", "annotation", "object", "nonshppict", "shppict", "listtable",
            "listoverridetable", "rsidtbl", "generator", "xmlnstbl", "themedata", "colorschememapping",
            "latentstyles", "datastore", "pntext", "pntxta", "pntxtb", "pn", "fldinst", "bkmkstart", "bkmkend",
            "mmathPr", "upr", "filetbl", "revtbl", "protusertbl", "listtext", "userprops", "docvar", "template",
            "background", "shp", "sp", "do", "atnid", "atnauthor", "comment", "keywords", "operator", "title",
            "subject", "author", "company", "doccomm", "hlinkbase", "xe", "tc", "txe", "pgdsctbl", "ftnsep",
            "ftnsepc", "aftnsep", "aftnsepc", "wgrffmtfilter", "fchars", "lchars", "passwordhash");

    private static final Map<Integer, String> CHARSET_CODEPAGES = Map.ofEntries(
            Map.entry(0, "windows-1252"), Map.entry(2, "windows-1252"), Map.entry(77, "x-MacRoman"),
            Map.entry(128, "windows-31j"), Map.entry(129, "x-windows-949"), Map.entry(134, "GBK"),
            Map.entry(136, "x-windows-950"), Map.entry(161, "windows-1253"), Map.entry(162, "windows-1254"),
            Map.entry(163, "windows-1258"), Map.entry(177, "windows-1255"), Map.entry(178, "windows-1256"),
            Map.entry(186, "windows-1257"), Map.entry(204, "windows-1251"), Map.entry(222, "x-windows-874"),
            Map.entry(238, "windows-1250"), Map.entry(255, "IBM437"));

    private enum Destination { TEXT, FONT_TABLE, COLOR_TABLE, SKIP }

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

    private record FontEntry(String name, Charset charset) {
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
            case TEXT -> textWord(word, hasParameter, parameter, on);
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
            case "line" -> paragraph();
            case "row" -> paragraph();
            case "tab", "cell" -> literal('\t');
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
            default -> {
                // unknown or unsupported: ignored
            }
        }
    }

    private void endGroup() {
        flushBytes();
        State inner = state;
        if (inner.destination == Destination.FONT_TABLE && entryName.length() > 0) addFont();
        if (stack.size() == 1 && !rootClosed) {
            // The end of the document: the last paragraph keeps the formatting in effect inside it.
            flushText();
            finishParagraph(false);
            rootClosed = true;
        }
        if (inner.destination == Destination.TEXT && !stack.isEmpty() && differs(inner, stack.peek())) flushText();
        state = stack.isEmpty() ? new State() : stack.pop();
        groupStart = false;
        ignorable = false;
        skipChars = 0;
    }

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
            case SKIP -> {
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
        insert("\n", state);
        finishParagraph(true);
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
        if (ended) paragraphStart = end;
    }

    /** "...\par}" ends many files: the empty paragraph after the last \par is not part of the text. */
    private void removeTrailingEmptyParagraph() {
        int length = document.getLength();
        try {
            if (length > 0 && document.getText(length - 1, 1).equals("\n")) document.remove(length - 1, 1);
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

    private static SimpleAttributeSet paragraphAttributes(State s) {
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
