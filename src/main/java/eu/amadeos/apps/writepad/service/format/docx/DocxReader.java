package eu.amadeos.apps.writepad.service.format.docx;

import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.domain.Margins;
import eu.amadeos.apps.writepad.domain.Orientation;
import eu.amadeos.apps.writepad.domain.PaperSize;
import eu.amadeos.apps.writepad.service.format.DocumentContent;
import eu.amadeos.apps.writepad.service.format.Documents;
import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import eu.amadeos.apps.writepad.service.list.ListAttributes;
import eu.amadeos.apps.writepad.service.list.ListFormat;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads a DOCX file into a styled document. Formatting is resolved as Word does it: document defaults, then
 * the paragraph style and the styles it is based on, then the character style, then direct formatting; list
 * indents come between a paragraph's style and its direct formatting. Theme fonts are looked up in the theme.
 * Lists become list attributes: paragraphs of the same numbering count together, a numbering with a restart
 * of its own counts separately. Tables give their paragraphs, fields their shown result, hyperlinks and
 * insertions their text; deletions, pictures, footnotes and comments are skipped.
 */
final class DocxReader {
    private static final XMLInputFactory XML = XMLInputFactory.newFactory();

    static {
        XML.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        XML.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XML.setProperty(XMLInputFactory.IS_COALESCING, true);
    }

    /** Children of a paragraph or run that are skipped with everything in them. */
    private static final Set<String> SKIPPED = Set.of("del", "moveFrom", "drawing", "pict", "object",
            "instrText", "delText", "delInstrText", "footnoteReference", "endnoteReference", "commentReference",
            "annotationRef", "sym", "softHyphen", "lastRenderedPageBreak", "fldData", "rPr", "pPr", "sdtPr",
            "sdtEndPr", "proofErr", "bookmarkStart", "bookmarkEnd", "commentRangeStart", "commentRangeEnd",
            "permStart", "permEnd", "oMath", "oMathPara", "AlternateContent");

    /** Formatting as read from one {@code w:pPr} or {@code w:rPr}, or several merged. */
    private static final class Props {
        final Map<String, String> values = new HashMap<>();
        /** Tab stops by position in twips; null where nothing sets them. */
        TreeMap<Integer, TabStop> tabs;
        /** Positions of inherited stops that a {@code w:tab w:val="clear"} removes. */
        List<Integer> clearedTabs;

        void merge(Props other) {
            if (other == null) return;
            if (other.values.containsKey("hanging")) values.remove("firstLine");
            if (other.values.containsKey("firstLine")) values.remove("hanging");
            values.putAll(other.values);
            if (other.tabs != null || other.clearedTabs != null) {
                TreeMap<Integer, TabStop> merged = tabs != null ? new TreeMap<>(tabs) : new TreeMap<>();
                if (other.clearedTabs != null) other.clearedTabs.forEach(merged::remove);
                if (other.tabs != null) merged.putAll(other.tabs);
                tabs = merged;
            }
        }

        String get(String key) {
            return values.get(key);
        }
    }

    private record Style(String id, String basedOn, Props paragraph, Props run) {
    }

    /** A list level: its format (null for none), its indents in twips (null if not set). */
    private record Level(ListFormat format, Props indent) {
    }

    private record Num(String abstractId, boolean restarts) {
    }

    private final Map<String, byte[]> files;
    private final StyledDocument document = Documents.newDocument();
    private final Props defaultParagraph = new Props();
    private final Props defaultRun = new Props();
    private final Map<String, Style> paragraphStyles = new HashMap<>();
    private final Map<String, Style> characterStyles = new HashMap<>();
    private String defaultParagraphStyle;
    private final Map<String, String> themeFonts = new HashMap<>();
    private final Map<String, Map<Integer, Level>> abstractNums = new HashMap<>();
    private final Map<String, Num> nums = new HashMap<>();
    private final Map<String, Integer> listIds = new HashMap<>();
    private int nextListId = 1;
    private Map<String, String> pageSize;
    private Map<String, String> pageMargins;

    /** Text waiting to be inserted, all in the same format. */
    private final StringBuilder text = new StringBuilder();
    private AttributeSet textFormat;
    /** Complex fields: for each open one, whether its result (shown) part has begun. */
    private final Deque<Boolean> fields = new ArrayDeque<>();

    private DocxReader(Map<String, byte[]> files) {
        this.files = files;
    }

    static DocumentContent read(byte[] bytes) throws IOException {
        DocxReader reader = new DocxReader(unzip(bytes));
        try {
            reader.parse();
        } catch (XMLStreamException e) {
            throw new IOException("Broken DOCX file: " + e.getMessage(), e);
        }
        return new DocumentContent(reader.document, reader.settings());
    }

    private static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".xml") || name.endsWith(".rels")) files.put(name, zip.readAllBytes());
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Broken ZIP file", e);
        }
        return files;
    }

    // ---------------------------------------------------------------- package

    private void parse() throws IOException, XMLStreamException {
        String main = target("", "_rels/.rels", Ooxml.OFFICE_DOCUMENT);
        if (main == null) main = "word/document.xml";
        byte[] body = files.get(main);
        if (body == null) throw new IOException("Not a Word document: no " + main);
        String folder = main.contains("/") ? main.substring(0, main.lastIndexOf('/') + 1) : "";
        String rels = folder + "_rels/" + main.substring(folder.length()) + ".rels";
        String theme = target(folder, rels, Ooxml.THEME);
        if (theme != null && files.containsKey(theme)) theme(files.get(theme));
        String styles = target(folder, rels, Ooxml.STYLES);
        if (styles != null && files.containsKey(styles)) styles(files.get(styles));
        String numbering = target(folder, rels, Ooxml.NUMBERING);
        if (numbering != null && files.containsKey(numbering)) numbering(files.get(numbering));
        document(body);
        removeTrailingParagraphEnd();
    }

    /** The part a relationship of the given type points to, as a path in the ZIP; null if there is none. */
    private String target(String folder, String relsPath, String type) throws XMLStreamException {
        byte[] rels = files.get(relsPath);
        if (rels == null) return null;
        XMLStreamReader r = reader(rels);
        while (r.hasNext()) {
            if (r.next() == XMLStreamConstants.START_ELEMENT && r.getLocalName().equals("Relationship")
                    && type.equals(r.getAttributeValue(null, "Type"))
                    && !"External".equals(r.getAttributeValue(null, "TargetMode"))) {
                return resolve(folder, r.getAttributeValue(null, "Target"));
            }
        }
        return null;
    }

    private static String resolve(String folder, String target) {
        if (target == null) return null;
        if (target.startsWith("/")) return target.substring(1);
        Deque<String> parts = new ArrayDeque<>();
        for (String part : (folder + target).split("/")) {
            if (part.equals("..")) {
                if (!parts.isEmpty()) parts.removeLast();
            } else if (!part.isEmpty() && !part.equals(".")) {
                parts.addLast(part);
            }
        }
        return String.join("/", parts);
    }

    private static XMLStreamReader reader(byte[] xml) throws XMLStreamException {
        return XML.createXMLStreamReader(new ByteArrayInputStream(xml));
    }

    private static boolean isW(XMLStreamReader r) {
        return Ooxml.W.equals(r.getNamespaceURI());
    }

    private static String val(XMLStreamReader r, String name) {
        return r.getAttributeValue(Ooxml.W, name);
    }

    private static void skip(XMLStreamReader r) throws XMLStreamException {
        int depth = 1;
        while (depth > 0 && r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) depth++;
            if (event == XMLStreamConstants.END_ELEMENT) depth--;
        }
    }

    private interface Children {
        void child(String localName) throws XMLStreamException;
    }

    /** Calls the handler for each child element; elements outside the main namespace are skipped. */
    private static void children(XMLStreamReader r, Children handler) throws XMLStreamException {
        while (r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (isW(r)) handler.child(r.getLocalName());
                else skip(r);
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                return;
            }
        }
    }

    // ---------------------------------------------------------------- theme, styles, numbering

    private void theme(byte[] xml) throws XMLStreamException {
        XMLStreamReader r = reader(xml);
        String font = null;
        while (r.hasNext()) {
            int event = r.next();
            if (event != XMLStreamConstants.START_ELEMENT || !Ooxml.A.equals(r.getNamespaceURI())) continue;
            switch (r.getLocalName()) {
                case "majorFont" -> font = "major";
                case "minorFont" -> font = "minor";
                case "latin" -> {
                    String typeface = r.getAttributeValue(null, "typeface");
                    if (font != null && typeface != null && !typeface.isBlank()) themeFonts.putIfAbsent(font, typeface);
                }
                default -> {
                }
            }
        }
    }

    private void styles(byte[] xml) throws XMLStreamException {
        XMLStreamReader r = reader(xml);
        while (r.hasNext()) {
            if (r.next() != XMLStreamConstants.START_ELEMENT || !isW(r)) continue;
            switch (r.getLocalName()) {
                case "rPrDefault" -> children(r, child -> {
                    if (child.equals("rPr")) defaultRun.merge(runProperties(r));
                    else skip(r);
                });
                case "pPrDefault" -> children(r, child -> {
                    if (child.equals("pPr")) defaultParagraph.merge(paragraphProperties(r, null));
                    else skip(r);
                });
                case "style" -> style(r);
                default -> {
                }
            }
        }
    }

    private void style(XMLStreamReader r) throws XMLStreamException {
        String type = val(r, "type");
        String id = val(r, "styleId");
        boolean isDefault = "1".equals(val(r, "default")) || "true".equals(val(r, "default"));
        String[] basedOn = {null};
        Props paragraph = new Props();
        Props run = new Props();
        children(r, child -> {
            switch (child) {
                case "basedOn" -> {
                    basedOn[0] = val(r, "val");
                    skip(r);
                }
                case "pPr" -> paragraph.merge(paragraphProperties(r, null));
                case "rPr" -> run.merge(runProperties(r));
                default -> skip(r);
            }
        });
        if (id == null) return;
        Style style = new Style(id, basedOn[0], paragraph, run);
        if ("paragraph".equals(type)) {
            paragraphStyles.put(id, style);
            if (isDefault) defaultParagraphStyle = id;
        } else if ("character".equals(type)) {
            characterStyles.put(id, style);
        }
    }

    private void numbering(byte[] xml) throws XMLStreamException {
        XMLStreamReader r = reader(xml);
        while (r.hasNext()) {
            if (r.next() != XMLStreamConstants.START_ELEMENT || !isW(r)) continue;
            switch (r.getLocalName()) {
                case "abstractNum" -> {
                    String id = val(r, "abstractNumId");
                    Map<Integer, Level> levels = new HashMap<>();
                    children(r, child -> {
                        if (child.equals("lvl")) level(r, levels);
                        else skip(r);
                    });
                    if (id != null) abstractNums.put(id, levels);
                }
                case "num" -> {
                    String id = val(r, "numId");
                    String[] abstractId = {null};
                    boolean[] restarts = {false};
                    children(r, child -> {
                        if (child.equals("abstractNumId")) {
                            abstractId[0] = val(r, "val");
                            skip(r);
                        } else if (child.equals("lvlOverride")) {
                            restarts[0] = true;
                            skip(r);
                        } else {
                            skip(r);
                        }
                    });
                    if (id != null) nums.put(id, new Num(abstractId[0], restarts[0]));
                }
                default -> {
                }
            }
        }
    }

    private void level(XMLStreamReader r, Map<Integer, Level> levels) throws XMLStreamException {
        int level;
        try {
            level = Integer.parseInt(val(r, "ilvl"));
        } catch (NumberFormatException e) {
            skip(r);
            return;
        }
        String[] numFmt = {"decimal"};
        Props[] indent = {null};
        children(r, child -> {
            switch (child) {
                case "numFmt" -> {
                    if (val(r, "val") != null) numFmt[0] = val(r, "val");
                    skip(r);
                }
                case "pPr" -> indent[0] = paragraphProperties(r, null);
                default -> skip(r);
            }
        });
        ListFormat format = switch (numFmt[0]) {
            case "none" -> null;
            case "bullet" -> ListFormat.BULLET;
            default -> ListFormat.ofDocx(numFmt[0]);
        };
        levels.put(level, new Level(format, indent[0]));
    }

    // ---------------------------------------------------------------- properties

    /**
     * A {@code w:pPr}; its {@code w:rPr} (the paragraph mark's format) goes into {@code mark} if given. The
     * paragraph style is kept as {@code pStyle}, the list as {@code numId} and {@code ilvl}.
     */
    private Props paragraphProperties(XMLStreamReader r, Props mark) throws XMLStreamException {
        Props p = new Props();
        children(r, child -> {
            switch (child) {
                case "pStyle" -> put(p, "pStyle", val(r, "val"));
                case "numPr" -> children(r, num -> {
                    if (num.equals("ilvl")) put(p, "ilvl", val(r, "val"));
                    if (num.equals("numId")) put(p, "numId", val(r, "val"));
                    skip(r);
                });
                case "tabs" -> children(r, tab -> {
                    if (tab.equals("tab")) tab(r, p);
                    skip(r);
                });
                case "spacing" -> {
                    put(p, "before", val(r, "before"));
                    put(p, "after", val(r, "after"));
                    put(p, "line", val(r, "line"));
                    put(p, "lineRule", val(r, "lineRule"));
                }
                case "ind" -> {
                    put(p, "left", val(r, "left") != null ? val(r, "left") : val(r, "start"));
                    put(p, "right", val(r, "right") != null ? val(r, "right") : val(r, "end"));
                    if (val(r, "hanging") != null) put(p, "hanging", val(r, "hanging"));
                    else put(p, "firstLine", val(r, "firstLine"));
                }
                case "jc" -> put(p, "jc", val(r, "val"));
                case "rPr" -> {
                    Props run = runProperties(r);
                    if (mark != null) mark.merge(run);
                    return;
                }
                case "sectPr" -> {
                    section(r);
                    return;
                }
                default -> {
                }
            }
            if (!child.equals("numPr") && !child.equals("tabs")) skip(r);
        });
        return p;
    }

    private static void put(Props p, String key, String value) {
        if (value != null) p.values.put(key, value);
    }

    private static void tab(XMLStreamReader r, Props p) {
        int pos;
        try {
            pos = Integer.parseInt(val(r, "pos"));
        } catch (NumberFormatException e) {
            return;
        }
        String kind = val(r, "val");
        if ("num".equals(kind)) return; // the tab after a list label, which the page view places itself
        if ("clear".equals(kind)) {
            if (p.clearedTabs == null) p.clearedTabs = new ArrayList<>();
            p.clearedTabs.add(pos);
            if (p.tabs != null) p.tabs.remove(pos);
            return;
        }
        int alignment = switch (kind == null ? "left" : kind) {
            case "center" -> TabStop.ALIGN_CENTER;
            case "right", "end" -> TabStop.ALIGN_RIGHT;
            case "decimal" -> TabStop.ALIGN_DECIMAL;
            default -> TabStop.ALIGN_LEFT;
        };
        int leader = switch (val(r, "leader") == null ? "none" : val(r, "leader")) {
            case "dot", "middleDot" -> TabStop.LEAD_DOTS;
            case "hyphen" -> TabStop.LEAD_HYPHENS;
            case "underscore" -> TabStop.LEAD_UNDERLINE;
            case "heavy" -> TabStop.LEAD_THICKLINE;
            default -> TabStop.LEAD_NONE;
        };
        if (p.tabs == null) p.tabs = new TreeMap<>();
        p.tabs.put(pos, new TabStop((float) Ooxml.points(Math.max(0, pos)), alignment, leader));
    }

    /** A {@code w:rPr}: font, size, the switches, colour; the character style as {@code rStyle}. */
    private Props runProperties(XMLStreamReader r) throws XMLStreamException {
        Props p = new Props();
        children(r, child -> {
            switch (child) {
                case "rStyle" -> put(p, "rStyle", val(r, "val"));
                case "rFonts" -> {
                    String font = val(r, "ascii") != null ? val(r, "ascii") : val(r, "hAnsi");
                    String theme = val(r, "asciiTheme") != null ? val(r, "asciiTheme") : val(r, "hAnsiTheme");
                    // A theme font wins over a named one, as in Word.
                    if (theme != null) {
                        String themed = themeFonts.get(theme.startsWith("major") ? "major" : "minor");
                        if (themed != null) font = themed;
                    }
                    // LibreOffice writes fonts it took from DOC files with their alternatives: "Liberation Sans;Arial".
                    if (font != null && font.indexOf(';') > 0) font = font.substring(0, font.indexOf(';'));
                    put(p, "font", font);
                }
                case "b", "i", "strike" -> put(p, child, isOn(val(r, "val")) ? "1" : "0");
                case "u" -> put(p, "u", "none".equals(val(r, "val")) ? "0" : "1");
                case "vertAlign" -> put(p, "vertAlign", val(r, "val"));
                case "color" -> put(p, "color", val(r, "val"));
                case "sz" -> put(p, "sz", val(r, "val"));
                default -> {
                }
            }
            skip(r);
        });
        return p;
    }

    private static boolean isOn(String value) {
        return value == null || !(value.equals("0") || value.equals("false") || value.equals("off")
                || value.equals("none"));
    }

    private void section(XMLStreamReader r) throws XMLStreamException {
        Map<String, String> size = new HashMap<>();
        Map<String, String> margins = new HashMap<>();
        children(r, child -> {
            if (child.equals("pgSz") || child.equals("pgMar")) {
                Map<String, String> target = child.equals("pgSz") ? size : margins;
                for (int i = 0; i < r.getAttributeCount(); i++) {
                    if (Ooxml.W.equals(r.getAttributeNamespace(i))) {
                        target.put(r.getAttributeLocalName(i), r.getAttributeValue(i));
                    }
                }
            }
            skip(r);
        });
        // Later sections replace earlier ones; the body's own section, at its end, is the last.
        if (!size.isEmpty()) pageSize = size;
        if (!margins.isEmpty()) pageMargins = margins;
    }

    /** A style with all it is based on; the styles' own properties from the top of the chain down. */
    private static Props chain(Map<String, Style> styles, String id, boolean paragraph) {
        List<Style> chain = new ArrayList<>();
        Style style = id != null ? styles.get(id) : null;
        while (style != null && chain.size() < 32 && !chain.contains(style)) {
            chain.add(0, style);
            style = style.basedOn() != null ? styles.get(style.basedOn()) : null;
        }
        Props p = new Props();
        for (Style s : chain) p.merge(paragraph ? s.paragraph() : s.run());
        return p;
    }

    // ---------------------------------------------------------------- document.xml

    private void document(byte[] xml) throws XMLStreamException {
        XMLStreamReader r = reader(xml);
        while (r.hasNext()) {
            if (r.next() == XMLStreamConstants.START_ELEMENT && isW(r) && r.getLocalName().equals("body")) {
                block(r);
                flushText();
            }
        }
    }

    /** Block content: paragraphs, and tables and content controls with paragraphs in them. */
    private void block(XMLStreamReader r) throws XMLStreamException {
        children(r, child -> {
            switch (child) {
                case "p" -> paragraph(r);
                case "sectPr" -> section(r);
                case "tbl", "tr", "tc", "sdt", "sdtContent", "customXml", "ins", "moveTo" -> block(r);
                default -> skip(r);
            }
        });
    }

    private void paragraph(XMLStreamReader r) throws XMLStreamException {
        flushText();
        int start = document.getLength();
        Props direct = new Props();
        Props mark = new Props();
        Props[] style = {null};
        Props[] runBase = {null};
        children(r, child -> {
            if (child.equals("pPr") && style[0] == null) {
                direct.merge(paragraphProperties(r, mark));
                return;
            }
            if (style[0] == null) {
                style[0] = paragraphStyle(direct);
                runBase[0] = runBase(direct);
            }
            inline(r, child, runBase[0]);
        });
        if (style[0] == null) {
            style[0] = paragraphStyle(direct);
            runBase[0] = runBase(direct);
        }
        flushText();
        Props markFormat = new Props();
        markFormat.merge(runBase[0]);
        markFormat.merge(mark);
        insert("\n", characterAttributes(markFormat));
        document.setParagraphAttributes(start, Math.max(1, document.getLength() - start),
                paragraphAttributes(style[0], direct), false);
        fields.clear();
    }

    /** The paragraph's style properties: defaults, then its style chain (or the default paragraph style). */
    private Props paragraphStyle(Props direct) {
        Props p = new Props();
        p.merge(defaultParagraph);
        String id = direct.get("pStyle") != null ? direct.get("pStyle") : defaultParagraphStyle;
        p.merge(chain(paragraphStyles, id, true));
        return p;
    }

    /** The format runs start from: defaults and the paragraph style's run properties. */
    private Props runBase(Props direct) {
        Props p = new Props();
        p.merge(defaultRun);
        String id = direct.get("pStyle") != null ? direct.get("pStyle") : defaultParagraphStyle;
        p.merge(chain(paragraphStyles, id, false));
        return p;
    }

    /** One child of a paragraph (or of a hyperlink, insertion, field, content control in it). */
    private void inline(XMLStreamReader r, String child, Props base) throws XMLStreamException {
        switch (child) {
            case "r" -> run(r, base);
            case "hyperlink", "ins", "moveTo", "smartTag", "customXml", "fldSimple", "sdt", "sdtContent", "dir",
                 "bdo" -> children(r, inner -> inline(r, inner, base));
            default -> skip(r);
        }
    }

    private void run(XMLStreamReader r, Props base) throws XMLStreamException {
        Props format = new Props();
        format.merge(base);
        AttributeSet[] attributes = {null};
        children(r, child -> {
            if (child.equals("rPr") && attributes[0] == null) {
                Props direct = runProperties(r);
                format.merge(chain(characterStyles, direct.get("rStyle"), false));
                format.merge(direct);
                return;
            }
            if (attributes[0] == null) attributes[0] = characterAttributes(format);
            switch (child) {
                case "t" -> {
                    String t = r.getElementText();
                    if (visible()) append(t, attributes[0]);
                }
                case "tab", "ptab" -> {
                    if (visible()) append("\t", attributes[0]);
                    skip(r);
                }
                case "br" -> {
                    String type = val(r, "type");
                    if (visible() && (type == null || type.equals("textWrapping"))) {
                        append(String.valueOf(WritepadDocument.LINE_BREAK), attributes[0]);
                    }
                    skip(r);
                }
                case "cr" -> {
                    if (visible()) append(String.valueOf(WritepadDocument.LINE_BREAK), attributes[0]);
                    skip(r);
                }
                case "noBreakHyphen" -> {
                    if (visible()) append("-", attributes[0]);
                    skip(r);
                }
                case "fldChar" -> {
                    switch (val(r, "fldCharType") == null ? "" : val(r, "fldCharType")) {
                        case "begin" -> fields.push(false);
                        case "separate" -> {
                            if (!fields.isEmpty()) {
                                fields.pop();
                                fields.push(true);
                            }
                        }
                        case "end" -> {
                            if (!fields.isEmpty()) fields.pop();
                        }
                        default -> {
                        }
                    }
                    skip(r);
                }
                default -> skip(r);
            }
        });
    }

    /** False inside the code of a field, where text is the field's instruction, not what it shows. */
    private boolean visible() {
        for (boolean shown : fields) {
            if (!shown) return false;
        }
        return true;
    }

    private void append(String s, AttributeSet attributes) {
        StringBuilder clean = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') continue;
            clean.append(c);
        }
        if (clean.isEmpty()) return;
        if (textFormat != null && !textFormat.isEqual(attributes)) flushText();
        textFormat = attributes;
        text.append(clean);
    }

    private void flushText() {
        if (!text.isEmpty()) insert(text.toString(), textFormat);
        text.setLength(0);
        textFormat = null;
    }

    private void insert(String s, AttributeSet attributes) {
        try {
            document.insertString(document.getLength(), s, attributes);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void removeTrailingParagraphEnd() {
        int length = document.getLength();
        try {
            if (length > 0 && document.getText(length - 1, 1).equals("\n")) document.remove(length - 1, 1);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- formatting

    private SimpleAttributeSet characterAttributes(Props p) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        String font = p.get("font");
        StyleConstants.setFontFamily(a, font != null && !font.isBlank() ? font : "Times New Roman");
        int size = number(p.get("sz"), 20);
        StyleConstants.setFontSize(a, Math.max(1, (size + 1) / 2));
        if ("1".equals(p.get("b"))) StyleConstants.setBold(a, true);
        if ("1".equals(p.get("i"))) StyleConstants.setItalic(a, true);
        if ("1".equals(p.get("u"))) StyleConstants.setUnderline(a, true);
        if ("1".equals(p.get("strike"))) StyleConstants.setStrikeThrough(a, true);
        if ("superscript".equals(p.get("vertAlign"))) StyleConstants.setSuperscript(a, true);
        if ("subscript".equals(p.get("vertAlign"))) StyleConstants.setSubscript(a, true);
        String color = p.get("color");
        if (color != null && color.matches("[0-9a-fA-F]{6}") && !color.equals("000000")) {
            StyleConstants.setForeground(a, new Color(Integer.parseInt(color, 16)));
        }
        return a;
    }

    private SimpleAttributeSet paragraphAttributes(Props style, Props direct) {
        Props p = new Props();
        p.merge(style);
        String numId = direct.get("numId") != null ? direct.get("numId") : style.get("numId");
        String ilvl = direct.get("ilvl") != null ? direct.get("ilvl") : style.get("ilvl");
        int level = Math.max(0, Math.min(ListAttributes.MAX_LEVEL, number(ilvl, 0)));
        Num num = numId != null ? nums.get(numId) : null;
        Level listLevel = null;
        if (num != null) {
            Map<Integer, Level> levels = abstractNums.get(num.abstractId());
            listLevel = levels != null ? levels.get(level) : null;
            // List indents override the paragraph style's, direct formatting overrides both.
            if (listLevel != null) p.merge(listLevel.indent());
        }
        p.merge(direct);
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setAlignment(a, switch (p.get("jc") == null ? "left" : p.get("jc")) {
            case "center" -> StyleConstants.ALIGN_CENTER;
            case "right", "end" -> StyleConstants.ALIGN_RIGHT;
            case "both", "distribute" -> StyleConstants.ALIGN_JUSTIFIED;
            default -> StyleConstants.ALIGN_LEFT;
        });
        StyleConstants.setLeftIndent(a, twips(p.get("left")));
        StyleConstants.setRightIndent(a, twips(p.get("right")));
        StyleConstants.setFirstLineIndent(a, p.get("hanging") != null ? 0f - twips(p.get("hanging")) : twips(p.get("firstLine")));
        StyleConstants.setSpaceAbove(a, Math.max(0, twips(p.get("before"))));
        StyleConstants.setSpaceBelow(a, Math.max(0, twips(p.get("after"))));
        String rule = p.get("lineRule");
        int line = number(p.get("line"), 240);
        StyleConstants.setLineSpacing(a, rule == null || rule.equals("auto") ? Math.max(0, line / 240f - 1) : 0);
        if (p.tabs != null && !p.tabs.isEmpty()) {
            StyleConstants.setTabSet(a, new TabSet(p.tabs.values().toArray(TabStop[]::new)));
        }
        if (num != null && listLevel != null && listLevel.format() != null && !"0".equals(numId)) {
            String key = num.restarts() ? "num:" + numId : "abstract:" + num.abstractId();
            int list = listIds.computeIfAbsent(key, k -> nextListId++);
            ListAttributes.set(a, list, level, listLevel.format());
        }
        return a;
    }

    private static float twips(String value) {
        return (float) Ooxml.points(number(value, 0));
    }

    private static int number(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return (int) Math.round(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ---------------------------------------------------------------- page setup

    private DocumentSettings settings() {
        DocumentSettings settings = DocumentSettings.DEFAULT;
        if (pageSize != null) {
            double width = Ooxml.points(number(pageSize.get("w"), 0));
            double height = Ooxml.points(number(pageSize.get("h"), 0));
            if (width > 0 && height > 0) {
                double shortSide = Math.min(width, height);
                double longSide = Math.max(width, height);
                PaperSize paper = PaperSize.A4;
                double best = Double.MAX_VALUE;
                for (PaperSize candidate : PaperSize.values()) {
                    double distance = Math.abs(candidate.width() - shortSide) + Math.abs(candidate.height() - longSide);
                    if (distance < best) {
                        best = distance;
                        paper = candidate;
                    }
                }
                boolean landscape = "landscape".equals(pageSize.get("orient")) || width > height;
                settings = new DocumentSettings(paper, landscape ? Orientation.LANDSCAPE : Orientation.PORTRAIT,
                        Margins.DEFAULT);
            }
        }
        if (pageMargins == null) return settings;
        Margins m = settings.margins();
        Margins read = new Margins(margin("top", m.top()), margin("left", m.left()), margin("bottom", m.bottom()),
                margin("right", m.right()));
        try {
            return settings.withMargins(read);
        } catch (IllegalArgumentException e) {
            return settings; // margins that leave no room for text
        }
    }

    /** A page margin; Word writes negative top and bottom margins for text that may not push them. */
    private double margin(String side, double fallback) {
        String value = pageMargins.get(side);
        if (value == null && side.equals("left")) value = pageMargins.get("start");
        if (value == null && side.equals("right")) value = pageMargins.get("end");
        return value == null ? fallback : Ooxml.points(Math.abs(number(value, 0)));
    }
}
