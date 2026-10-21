package eu.amadeos.apps.writepad.service.format.odt;

import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.domain.Margins;
import eu.amadeos.apps.writepad.domain.Orientation;
import eu.amadeos.apps.writepad.service.format.DocumentContent;
import eu.amadeos.apps.writepad.service.format.Documents;
import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import eu.amadeos.apps.writepad.service.layout.TabStops;
import eu.amadeos.apps.writepad.service.list.ListAttributes;
import eu.amadeos.apps.writepad.service.list.ListFormat;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a styled document as an OpenDocument text file: the ZIP with {@code mimetype} first and stored,
 * {@code content.xml} with one automatic style per distinct paragraph format (P1, P2, ...), text format (T1,
 * ...) and list (L1, ...), {@code styles.xml} with the default font and the page, {@code meta.xml} and the
 * manifest. Lists become nested {@code text:list} elements; a list interrupted by other paragraphs continues
 * with {@code text:continue-list}. Tab positions are written relative to the paragraph's left indent, as ODF
 * defines them.
 */
final class OdtWriter {
    private static final XMLOutputFactory XML = XMLOutputFactory.newFactory();
    /** List levels a list style has in ODF. */
    private static final int ODF_LEVELS = 10;

    private final StyledDocument document;
    private final DocumentSettings settings;
    /** Font families in order of use; the base style's first. */
    private final Set<String> fonts = new LinkedHashSet<>();
    /** Formatting properties by style name, and style names by the properties' key. */
    private final Map<String, String> paragraphStyles = new LinkedHashMap<>();
    private final Map<String, Props> paragraphProps = new HashMap<>();
    private final Map<String, String> textStyles = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> textProps = new HashMap<>();
    /** Our list ids by order of appearance (L1, L2, ...), with the styles of their levels as first used. */
    private final Map<Integer, Integer> listNumbers = new LinkedHashMap<>();
    private final Map<Integer, ListFormat[]> listLevels = new HashMap<>();

    /** The paragraph formatting of one P style. */
    private record Props(Map<String, String> paragraph, List<TabStop> tabs, Map<String, String> text) {
    }

    private OdtWriter(DocumentContent content) {
        this.document = content.document();
        this.settings = content.settings();
    }

    static byte[] write(DocumentContent content) throws IOException {
        return new OdtWriter(content).zip();
    }

    // --8<-- [start:zip]
    private byte[] zip() throws IOException {
        fonts.add(Documents.DEFAULT_FAMILY);
        byte[] body = xml(this::content);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            // The mimetype comes first and uncompressed, so that its name and type can be read at fixed offsets.
            byte[] mime = Odf.MIME_TYPE.getBytes(StandardCharsets.US_ASCII);
            ZipEntry entry = new ZipEntry("mimetype");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(mime.length);
            entry.setCompressedSize(mime.length);
            CRC32 crc = new CRC32();
            crc.update(mime);
            entry.setCrc(crc.getValue());
            zip.putNextEntry(entry);
            zip.write(mime);
            zip.closeEntry();
            put(zip, "content.xml", body);
            put(zip, "styles.xml", xml(this::styles));
            put(zip, "meta.xml", xml(this::meta));
            put(zip, "META-INF/manifest.xml", xml(this::manifest));
        }
        return out.toByteArray();
    }
    // --8<-- [end:zip]

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private interface XmlPart {
        void write(XMLStreamWriter xml) throws XMLStreamException;
    }

    private static byte[] xml(XmlPart part) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            XMLStreamWriter xml = XML.createXMLStreamWriter(out, "UTF-8");
            xml.writeStartDocument("UTF-8", "1.0");
            part.write(xml);
            xml.writeEndDocument();
            xml.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot write the document as ODT", e);
        }
        return out.toByteArray();
    }

    private static void root(XMLStreamWriter xml, String name) throws XMLStreamException {
        xml.writeStartElement("office", name, Odf.OFFICE);
        for (Map.Entry<String, String> ns : new java.util.TreeMap<>(Odf.PREFIXES).entrySet()) {
            xml.writeNamespace(ns.getKey(), ns.getValue());
        }
        xml.writeNamespace("dc", "http://purl.org/dc/elements/1.1/");
        xml.writeAttribute("office", Odf.OFFICE, "version", Odf.VERSION);
    }

    // ---------------------------------------------------------------- content.xml

    private void content(XMLStreamWriter xml) throws XMLStreamException {
        // A first pass over the text finds the styles, lists and fonts it uses, which come before it.
        XMLStreamWriter discard = XML.createXMLStreamWriter(OutputStream.nullOutputStream(), "UTF-8");
        discard.writeStartElement("office", "text", Odf.OFFICE);
        paragraphs(discard);
        discard.close();

        root(xml, "document-content");
        fontFaces(xml);
        xml.writeStartElement("office", "automatic-styles", Odf.OFFICE);
        for (Map.Entry<String, String> style : paragraphStyles.entrySet()) {
            paragraphStyle(xml, style.getValue(), paragraphProps.get(style.getKey()));
        }
        for (Map.Entry<String, String> style : textStyles.entrySet()) {
            xml.writeStartElement("style", "style", Odf.STYLE);
            xml.writeAttribute("style", Odf.STYLE, "name", style.getValue());
            xml.writeAttribute("style", Odf.STYLE, "family", "text");
            textProperties(xml, textProps.get(style.getKey()));
            xml.writeEndElement();
        }
        for (Map.Entry<Integer, Integer> list : listNumbers.entrySet()) listStyle(xml, list.getKey(), list.getValue());
        xml.writeEndElement();
        xml.writeStartElement("office", "body", Odf.OFFICE);
        xml.writeStartElement("office", "text", Odf.OFFICE);
        paragraphs(xml);
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private void paragraphs(XMLStreamWriter xml) throws XMLStreamException {
        Element root = document.getDefaultRootElement();
        ListWriter lists = new ListWriter(xml);
        for (int p = 0; p < root.getElementCount(); p++) {
            Element paragraph = root.getElement(p);
            AttributeSet a = paragraph.getAttributes();
            if (ListAttributes.isListItem(a)) {
                lists.item(ListAttributes.list(a), ListAttributes.level(a), listNumber(a));
            } else {
                lists.closeAll();
            }
            paragraph(xml, paragraph);
        }
        lists.closeAll();
    }

    /** Opens and closes {@code text:list} and {@code text:list-item} elements around the paragraphs. */
    private final class ListWriter {
        private final XMLStreamWriter xml;
        /** Our id of the open list, 0 if none. */
        private int open;
        /** Level of the innermost open list item, -1 outside lists. */
        private int depth = -1;
        /** The xml:id last written for each list, to continue it after other paragraphs. */
        private final Map<Integer, String> lastXmlId = new HashMap<>();
        private int segments;

        ListWriter(XMLStreamWriter xml) {
            this.xml = xml;
        }

        void item(int list, int level, int number) throws XMLStreamException {
            if (list != open) {
                closeAll();
                open = list;
                xml.writeStartElement("text", "list", Odf.TEXT);
                String xmlId = "list" + number + "-" + (++segments);
                xml.writeAttribute("xml", Odf.XML, "id", xmlId);
                xml.writeAttribute("text", Odf.TEXT, "style-name", "L" + number);
                String previous = lastXmlId.put(list, xmlId);
                if (previous != null) xml.writeAttribute("text", Odf.TEXT, "continue-list", previous);
                xml.writeStartElement("text", "list-item", Odf.TEXT);
                depth = 0;
            } else if (level <= depth) {
                while (depth > level) {
                    xml.writeEndElement(); // list-item
                    xml.writeEndElement(); // list
                    depth--;
                }
                xml.writeEndElement();
                xml.writeStartElement("text", "list-item", Odf.TEXT);
            }
            while (depth < level) {
                xml.writeStartElement("text", "list", Odf.TEXT);
                xml.writeStartElement("text", "list-item", Odf.TEXT);
                depth++;
            }
        }

        void closeAll() throws XMLStreamException {
            while (depth >= 0) {
                xml.writeEndElement();
                xml.writeEndElement();
                depth--;
            }
            open = 0;
        }
    }

    private void paragraph(XMLStreamWriter xml, Element paragraph) throws XMLStreamException {
        xml.writeStartElement("text", "p", Odf.TEXT);
        xml.writeAttribute("text", Odf.TEXT, "style-name", paragraphStyle(paragraph));
        boolean[] lastSpace = {true};
        String open = null;
        for (int r = 0; r < paragraph.getElementCount(); r++) {
            Element run = paragraph.getElement(r);
            int start = run.getStartOffset();
            int end = Math.min(run.getEndOffset(), document.getLength());
            String text;
            try {
                text = document.getText(start, Math.max(0, end - start));
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
            if (text.endsWith("\n")) text = text.substring(0, text.length() - 1);
            if (text.isEmpty()) continue;
            // Runs in the same format (a line break has an element of its own) share one span.
            String style = textStyle(run.getAttributes());
            if (!style.equals(open)) {
                if (open != null) xml.writeEndElement();
                xml.writeStartElement("text", "span", Odf.TEXT);
                xml.writeAttribute("text", Odf.TEXT, "style-name", style);
                open = style;
            }
            text(xml, text, lastSpace);
        }
        if (open != null) xml.writeEndElement();
        xml.writeEndElement();
    }

    /**
     * Writes text with ODF's white space rules: XML white space collapses, so a space after a space or at the
     * start of the paragraph becomes {@code text:s}, a tab {@code text:tab}, a line break
     * {@code text:line-break}.
     */
    private static void text(XMLStreamWriter xml, String text, boolean[] lastSpace) throws XMLStreamException {
        StringBuilder chars = new StringBuilder();
        int spaces = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                if (lastSpace[0]) {
                    spaces++;
                } else {
                    chars.append(' ');
                    lastSpace[0] = true;
                }
                continue;
            }
            if (spaces > 0 || c == '\t' || c == WritepadDocument.LINE_BREAK || c < 0x20) {
                flush(xml, chars);
                spaces = writeSpaces(xml, spaces);
            }
            if (c == '\t') {
                xml.writeEmptyElement("text", "tab", Odf.TEXT);
            } else if (c == WritepadDocument.LINE_BREAK) {
                xml.writeEmptyElement("text", "line-break", Odf.TEXT);
            } else if (c >= 0x20 && c != 0xFFFE && c != 0xFFFF) {
                chars.append(c);
            } else {
                continue; // other control characters cannot be written in XML
            }
            lastSpace[0] = false;
        }
        flush(xml, chars);
        writeSpaces(xml, spaces);
    }

    private static void flush(XMLStreamWriter xml, StringBuilder chars) throws XMLStreamException {
        if (chars.isEmpty()) return;
        xml.writeCharacters(chars.toString());
        chars.setLength(0);
    }

    private static int writeSpaces(XMLStreamWriter xml, int spaces) throws XMLStreamException {
        if (spaces == 0) return 0;
        xml.writeEmptyElement("text", "s", Odf.TEXT);
        if (spaces > 1) xml.writeAttribute("text", Odf.TEXT, "c", String.valueOf(spaces));
        return 0;
    }

    // ---------------------------------------------------------------- automatic styles

    private String paragraphStyle(Element paragraph) {
        AttributeSet a = paragraph.getAttributes();
        Map<String, String> p = new LinkedHashMap<>();
        p.put("fo:text-align", switch (StyleConstants.getAlignment(a)) {
            case StyleConstants.ALIGN_CENTER -> "center";
            case StyleConstants.ALIGN_RIGHT -> "end";
            case StyleConstants.ALIGN_JUSTIFIED -> "justify";
            default -> "start";
        });
        float left = StyleConstants.getLeftIndent(a);
        p.put("fo:margin-left", Odf.cm(left));
        p.put("fo:margin-right", Odf.cm(StyleConstants.getRightIndent(a)));
        p.put("fo:text-indent", Odf.cm(StyleConstants.getFirstLineIndent(a)));
        p.put("fo:margin-top", Odf.cm(StyleConstants.getSpaceAbove(a)));
        p.put("fo:margin-bottom", Odf.cm(StyleConstants.getSpaceBelow(a)));
        float lineSpacing = StyleConstants.getLineSpacing(a);
        if (lineSpacing > 0) p.put("fo:line-height", Math.round((1 + lineSpacing) * 100) + "%");
        List<TabStop> tabs = new ArrayList<>();
        TabSet tabSet = StyleConstants.getTabSet(a);
        if (tabSet != null) {
            for (int i = 0; i < tabSet.getTabCount(); i++) tabs.add(tabSet.getTab(i));
        }
        // An empty paragraph is as high as its font: the format of its end goes into the paragraph style.
        int end = Math.max(paragraph.getStartOffset(), paragraph.getEndOffset() - 1);
        Map<String, String> text = textProperties(document.getCharacterElement(end).getAttributes());
        StringBuilder key = new StringBuilder(p.toString()).append(text);
        for (TabStop tab : tabs) {
            key.append('|').append(tab.getPosition() - left).append(',').append(tab.getAlignment()).append(',')
                    .append(tab.getLeader());
        }
        return paragraphStyles.computeIfAbsent(key.toString(), k -> {
            paragraphProps.put(k, new Props(p, tabs, text));
            return "P" + (paragraphStyles.size() + 1);
        });
    }

    private void paragraphStyle(XMLStreamWriter xml, String name, Props props) throws XMLStreamException {
        xml.writeStartElement("style", "style", Odf.STYLE);
        xml.writeAttribute("style", Odf.STYLE, "name", name);
        xml.writeAttribute("style", Odf.STYLE, "family", "paragraph");
        xml.writeAttribute("style", Odf.STYLE, "parent-style-name", "Standard");
        if (props.tabs().isEmpty()) {
            xml.writeEmptyElement("style", "paragraph-properties", Odf.STYLE);
            attributes(xml, props.paragraph());
        } else {
            xml.writeStartElement("style", "paragraph-properties", Odf.STYLE);
            attributes(xml, props.paragraph());
            float left = (float) Odf.points(props.paragraph().get("fo:margin-left"));
            xml.writeStartElement("style", "tab-stops", Odf.STYLE);
            for (TabStop tab : props.tabs()) tabStop(xml, tab, left);
            xml.writeEndElement();
            xml.writeEndElement();
        }
        textProperties(xml, props.text());
        xml.writeEndElement();
    }

    private static void tabStop(XMLStreamWriter xml, TabStop tab, float left) throws XMLStreamException {
        xml.writeEmptyElement("style", "tab-stop", Odf.STYLE);
        xml.writeAttribute("style", Odf.STYLE, "position", Odf.cm(tab.getPosition() - left));
        switch (tab.getAlignment()) {
            case TabStop.ALIGN_CENTER -> xml.writeAttribute("style", Odf.STYLE, "type", "center");
            case TabStop.ALIGN_RIGHT -> xml.writeAttribute("style", Odf.STYLE, "type", "right");
            case TabStop.ALIGN_DECIMAL -> {
                xml.writeAttribute("style", Odf.STYLE, "type", "char");
                char separator = java.text.DecimalFormatSymbols.getInstance().getDecimalSeparator();
                xml.writeAttribute("style", Odf.STYLE, "char", String.valueOf(separator));
            }
            default -> {
            }
        }
        String[] leader = switch (tab.getLeader()) {
            case TabStop.LEAD_DOTS -> new String[]{"dotted", "."};
            case TabStop.LEAD_HYPHENS -> new String[]{"dash", "-"};
            case TabStop.LEAD_UNDERLINE, TabStop.LEAD_THICKLINE -> new String[]{"solid", "_"};
            case TabStop.LEAD_EQUALS -> new String[]{"solid", "="};
            default -> null;
        };
        if (leader != null) {
            xml.writeAttribute("style", Odf.STYLE, "leader-style", leader[0]);
            xml.writeAttribute("style", Odf.STYLE, "leader-text", leader[1]);
        }
    }

    private String textStyle(AttributeSet a) {
        Map<String, String> props = textProperties(a);
        String key = props.toString();
        return textStyles.computeIfAbsent(key, k -> {
            textProps.put(k, props);
            return "T" + (textStyles.size() + 1);
        });
    }

    /** The text properties of a run, for Western, Asian and complex scripts alike. */
    private Map<String, String> textProperties(AttributeSet a) {
        Map<String, String> t = new LinkedHashMap<>();
        String family = StyleConstants.getFontFamily(a);
        fonts.add(family);
        String size = Odf.pt(StyleConstants.getFontSize(a));
        for (String script : new String[]{"", "-asian", "-complex"}) {
            t.put("style:font-name" + script, family);
            t.put((script.isEmpty() ? "fo:" : "style:") + "font-size" + script, size);
            if (StyleConstants.isBold(a)) t.put((script.isEmpty() ? "fo:" : "style:") + "font-weight" + script, "bold");
            if (StyleConstants.isItalic(a)) t.put((script.isEmpty() ? "fo:" : "style:") + "font-style" + script, "italic");
        }
        if (StyleConstants.isUnderline(a)) {
            t.put("style:text-underline-style", "solid");
            t.put("style:text-underline-width", "auto");
            t.put("style:text-underline-color", "font-color");
        }
        if (StyleConstants.isStrikeThrough(a)) {
            t.put("style:text-line-through-style", "solid");
            t.put("style:text-line-through-type", "single");
        }
        if (StyleConstants.isSuperscript(a)) t.put("style:text-position", "super 58%");
        if (StyleConstants.isSubscript(a)) t.put("style:text-position", "sub 58%");
        if (a.isDefined(StyleConstants.Foreground)) {
            Color c = StyleConstants.getForeground(a);
            t.put("fo:color", String.format(Locale.ROOT, "#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()));
        }
        return t;
    }

    private static void textProperties(XMLStreamWriter xml, Map<String, String> props) throws XMLStreamException {
        xml.writeEmptyElement("style", "text-properties", Odf.STYLE);
        attributes(xml, props);
    }

    private static void attributes(XMLStreamWriter xml, Map<String, String> props) throws XMLStreamException {
        for (Map.Entry<String, String> property : props.entrySet()) {
            String[] name = property.getKey().split(":", 2);
            xml.writeAttribute(name[0], Odf.PREFIXES.get(name[0]), name[1], property.getValue());
        }
    }

    /** The number of the paragraph's list (L1, L2, ...); remembers the style of each level as first used. */
    private int listNumber(AttributeSet a) {
        int id = ListAttributes.list(a);
        int number = listNumbers.computeIfAbsent(id, k -> listNumbers.size() + 1);
        ListFormat[] levels = listLevels.computeIfAbsent(id, k -> new ListFormat[ListAttributes.MAX_LEVEL + 1]);
        int level = ListAttributes.level(a);
        if (levels[level] == null) levels[level] = ListAttributes.format(a);
        return number;
    }

    /**
     * A list's style: all ten levels, in the styles used (the others follow the first: all bullets, or 1. a. i.
     * for numbering), each with the label at the first line's indent and the text at the left indent.
     */
    private void listStyle(XMLStreamWriter xml, int list, int number) throws XMLStreamException {
        ListFormat[] levels = listLevels.get(list);
        ListFormat first = Arrays.stream(levels).filter(Objects::nonNull).findFirst().orElse(ListFormat.BULLET);
        xml.writeStartElement("text", "list-style", Odf.TEXT);
        xml.writeAttribute("style", Odf.STYLE, "name", "L" + number);
        for (int level = 0; level < ODF_LEVELS; level++) {
            ListFormat format = level < levels.length && levels[level] != null ? levels[level]
                    : first.isBullet() ? ListFormat.BULLET : ListFormat.numberingFor(level);
            if (format.isBullet()) {
                xml.writeStartElement("text", "list-level-style-bullet", Odf.TEXT);
                xml.writeAttribute("text", Odf.TEXT, "level", String.valueOf(level + 1));
                xml.writeAttribute("text", Odf.TEXT, "bullet-char", format.label(1, level));
            } else {
                xml.writeStartElement("text", "list-level-style-number", Odf.TEXT);
                xml.writeAttribute("text", Odf.TEXT, "level", String.valueOf(level + 1));
                xml.writeAttribute("style", Odf.STYLE, "num-suffix", ".");
                xml.writeAttribute("style", Odf.STYLE, "num-format", format.odfCode());
            }
            xml.writeStartElement("style", "list-level-properties", Odf.STYLE);
            xml.writeAttribute("text", Odf.TEXT, "list-level-position-and-space-mode", "label-alignment");
            xml.writeEmptyElement("style", "list-level-label-alignment", Odf.STYLE);
            String indent = Odf.cm(ListAttributes.leftIndent(level));
            xml.writeAttribute("text", Odf.TEXT, "label-followed-by", "listtab");
            xml.writeAttribute("text", Odf.TEXT, "list-tab-stop-position", indent);
            xml.writeAttribute("fo", Odf.FO, "text-indent", Odf.cm(-ListAttributes.STEP));
            xml.writeAttribute("fo", Odf.FO, "margin-left", indent);
            xml.writeEndElement();
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    private void fontFaces(XMLStreamWriter xml) throws XMLStreamException {
        xml.writeStartElement("office", "font-face-decls", Odf.OFFICE);
        for (String family : fonts) {
            xml.writeEmptyElement("style", "font-face", Odf.STYLE);
            xml.writeAttribute("style", Odf.STYLE, "name", family);
            boolean plain = family.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '-');
            xml.writeAttribute("svg", Odf.SVG, "font-family", plain ? family : "'" + family.replace("'", "") + "'");
            String generic = generic(family);
            if (generic != null) xml.writeAttribute("style", Odf.STYLE, "font-family-generic", generic);
        }
        xml.writeEndElement();
    }

    /** The generic family, so that a program without the font (Java's "Serif") picks a similar one. */
    static String generic(String family) {
        return switch (family.toLowerCase(Locale.ROOT)) {
            case "serif", "times new roman", "liberation serif", "dejavu serif", "georgia" -> "roman";
            case "sansserif", "sans-serif", "dialog", "arial", "liberation sans", "dejavu sans", "helvetica",
                 "calibri", "verdana" -> "swiss";
            case "monospaced", "dialoginput", "courier new", "liberation mono", "dejavu sans mono", "courier" ->
                    "modern";
            default -> null;
        };
    }

    // ---------------------------------------------------------------- styles.xml, meta.xml, manifest

    private void styles(XMLStreamWriter xml) throws XMLStreamException {
        root(xml, "document-styles");
        fontFaces(xml);
        xml.writeStartElement("office", "styles", Odf.OFFICE);
        xml.writeStartElement("style", "default-style", Odf.STYLE);
        xml.writeAttribute("style", Odf.STYLE, "family", "paragraph");
        xml.writeEmptyElement("style", "paragraph-properties", Odf.STYLE);
        xml.writeAttribute("style", Odf.STYLE, "tab-stop-distance", Odf.cm(TabStops.DEFAULT_INTERVAL));
        Map<String, String> text = new LinkedHashMap<>();
        for (String script : new String[]{"", "-asian", "-complex"}) {
            text.put("style:font-name" + script, Documents.DEFAULT_FAMILY);
            text.put((script.isEmpty() ? "fo:" : "style:") + "font-size" + script, Odf.pt(Documents.DEFAULT_SIZE));
        }
        textProperties(xml, text);
        xml.writeEndElement();
        xml.writeEmptyElement("style", "style", Odf.STYLE);
        xml.writeAttribute("style", Odf.STYLE, "name", "Standard");
        xml.writeAttribute("style", Odf.STYLE, "family", "paragraph");
        xml.writeAttribute("style", Odf.STYLE, "class", "text");
        xml.writeEndElement();

        xml.writeStartElement("office", "automatic-styles", Odf.OFFICE);
        xml.writeStartElement("style", "page-layout", Odf.STYLE);
        xml.writeAttribute("style", Odf.STYLE, "name", "pm1");
        xml.writeEmptyElement("style", "page-layout-properties", Odf.STYLE);
        Margins m = settings.margins();
        xml.writeAttribute("fo", Odf.FO, "page-width", Odf.cm(settings.pageWidth()));
        xml.writeAttribute("fo", Odf.FO, "page-height", Odf.cm(settings.pageHeight()));
        xml.writeAttribute("style", Odf.STYLE, "print-orientation",
                settings.orientation() == Orientation.LANDSCAPE ? "landscape" : "portrait");
        xml.writeAttribute("fo", Odf.FO, "margin-top", Odf.cm(m.top()));
        xml.writeAttribute("fo", Odf.FO, "margin-bottom", Odf.cm(m.bottom()));
        xml.writeAttribute("fo", Odf.FO, "margin-left", Odf.cm(m.left()));
        xml.writeAttribute("fo", Odf.FO, "margin-right", Odf.cm(m.right()));
        xml.writeEndElement();
        xml.writeEndElement();

        xml.writeStartElement("office", "master-styles", Odf.OFFICE);
        xml.writeEmptyElement("style", "master-page", Odf.STYLE);
        xml.writeAttribute("style", Odf.STYLE, "name", "Standard");
        xml.writeAttribute("style", Odf.STYLE, "page-layout-name", "pm1");
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private void meta(XMLStreamWriter xml) throws XMLStreamException {
        root(xml, "document-meta");
        xml.writeStartElement("office", "meta", Odf.OFFICE);
        xml.writeStartElement("meta", "generator", Odf.META);
        xml.writeCharacters("amade-writepad");
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private void manifest(XMLStreamWriter xml) throws XMLStreamException {
        xml.writeStartElement("manifest", "manifest", Odf.MANIFEST);
        xml.writeNamespace("manifest", Odf.MANIFEST);
        xml.writeAttribute("manifest", Odf.MANIFEST, "version", Odf.VERSION);
        entry(xml, "/", Odf.MIME_TYPE, true);
        for (String file : List.of("content.xml", "styles.xml", "meta.xml")) entry(xml, file, "text/xml", false);
        xml.writeEndElement();
    }

    private static void entry(XMLStreamWriter xml, String path, String type, boolean root) throws XMLStreamException {
        xml.writeEmptyElement("manifest", "file-entry", Odf.MANIFEST);
        xml.writeAttribute("manifest", Odf.MANIFEST, "full-path", path);
        if (root) xml.writeAttribute("manifest", Odf.MANIFEST, "version", Odf.VERSION);
        xml.writeAttribute("manifest", Odf.MANIFEST, "media-type", type);
    }
}
