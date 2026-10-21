package eu.amadeos.apps.writepad.service.format.docx;

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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a styled document as a DOCX file that Word and LibreOffice open: {@code word/document.xml} with the
 * paragraphs, runs in direct formatting and the page in the last {@code w:sectPr}; {@code word/styles.xml}
 * with the default font; {@code word/numbering.xml} with one numbering per list (a list interrupted by other
 * paragraphs keeps its number, so it counts on); {@code word/settings.xml} with the default tab stops; the
 * content types and relationships. Lengths are in twips, font sizes in half points. Tab positions count from
 * the margin, as in our model.
 */
final class DocxWriter {
    private static final XMLOutputFactory XML = XMLOutputFactory.newFactory();
    private static final String W = Ooxml.W;

    private final StyledDocument document;
    private final DocumentSettings settings;
    /** Our list ids by order of appearance as {@code w:numId} (from 1), with the styles of their levels. */
    private final Map<Integer, Integer> numIds = new LinkedHashMap<>();
    private final Map<Integer, ListFormat[]> listLevels = new HashMap<>();

    private DocxWriter(DocumentContent content) {
        this.document = content.document();
        this.settings = content.settings();
    }

    static byte[] write(DocumentContent content) throws IOException {
        return new DocxWriter(content).zip();
    }

    private byte[] zip() throws IOException {
        byte[] body = xml(this::document);
        boolean lists = !numIds.isEmpty();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "[Content_Types].xml", xml(xml -> contentTypes(xml, lists)));
            put(zip, "_rels/.rels", xml(xml -> relationships(xml, new String[][]{
                    {"rId1", Ooxml.OFFICE_DOCUMENT, "word/document.xml"}})));
            put(zip, "word/document.xml", body);
            put(zip, "word/styles.xml", xml(this::styles));
            put(zip, "word/settings.xml", xml(DocxWriter::settings));
            if (lists) put(zip, "word/numbering.xml", xml(this::numbering));
            String[][] parts = lists
                    ? new String[][]{{"rId1", Ooxml.STYLES, "styles.xml"}, {"rId2", Ooxml.SETTINGS, "settings.xml"},
                    {"rId3", Ooxml.NUMBERING, "numbering.xml"}}
                    : new String[][]{{"rId1", Ooxml.STYLES, "styles.xml"}, {"rId2", Ooxml.SETTINGS, "settings.xml"}};
            put(zip, "word/_rels/document.xml.rels", xml(xml -> relationships(xml, parts)));
        }
        return out.toByteArray();
    }

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
            throw new IOException("Cannot write the document as DOCX", e);
        }
        return out.toByteArray();
    }

    private static void start(XMLStreamWriter xml, String name) throws XMLStreamException {
        xml.writeStartElement("w", name, W);
    }

    private static void empty(XMLStreamWriter xml, String name, String... attributes) throws XMLStreamException {
        xml.writeEmptyElement("w", name, W);
        for (int i = 0; i + 1 < attributes.length; i += 2) xml.writeAttribute("w", W, attributes[i], attributes[i + 1]);
    }

    private static void root(XMLStreamWriter xml, String name) throws XMLStreamException {
        start(xml, name);
        xml.writeNamespace("w", W);
        xml.writeNamespace("r", Ooxml.R);
    }

    // ---------------------------------------------------------------- package parts

    private static void contentTypes(XMLStreamWriter xml, boolean lists) throws XMLStreamException {
        xml.writeStartElement("", "Types", Ooxml.CONTENT_TYPES);
        xml.writeDefaultNamespace(Ooxml.CONTENT_TYPES);
        xml.writeEmptyElement("", "Default", Ooxml.CONTENT_TYPES);
        xml.writeAttribute("Extension", "rels");
        xml.writeAttribute("ContentType", Ooxml.RELATIONSHIPS_TYPE);
        xml.writeEmptyElement("", "Default", Ooxml.CONTENT_TYPES);
        xml.writeAttribute("Extension", "xml");
        xml.writeAttribute("ContentType", "application/xml");
        override(xml, "/word/document.xml", Ooxml.MAIN_TYPE);
        override(xml, "/word/styles.xml", Ooxml.STYLES_TYPE);
        override(xml, "/word/settings.xml", Ooxml.SETTINGS_TYPE);
        if (lists) override(xml, "/word/numbering.xml", Ooxml.NUMBERING_TYPE);
        xml.writeEndElement();
    }

    private static void override(XMLStreamWriter xml, String part, String type) throws XMLStreamException {
        xml.writeEmptyElement("", "Override", Ooxml.CONTENT_TYPES);
        xml.writeAttribute("PartName", part);
        xml.writeAttribute("ContentType", type);
    }

    private static void relationships(XMLStreamWriter xml, String[][] relationships) throws XMLStreamException {
        xml.writeStartElement("", "Relationships", Ooxml.PACKAGE_RELATIONSHIPS);
        xml.writeDefaultNamespace(Ooxml.PACKAGE_RELATIONSHIPS);
        for (String[] r : relationships) {
            xml.writeEmptyElement("", "Relationship", Ooxml.PACKAGE_RELATIONSHIPS);
            xml.writeAttribute("Id", r[0]);
            xml.writeAttribute("Type", r[1]);
            xml.writeAttribute("Target", r[2]);
        }
        xml.writeEndElement();
    }

    private static void settings(XMLStreamWriter xml) throws XMLStreamException {
        root(xml, "settings");
        empty(xml, "defaultTabStop", "val", String.valueOf(Ooxml.twips(TabStops.DEFAULT_INTERVAL)));
        start(xml, "compat");
        // Word opens files without a compatibility mode as Word 2007 documents.
        empty(xml, "compatSetting", "name", "compatibilityMode", "uri", "http://schemas.microsoft.com/office/word",
                "val", "15");
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private void styles(XMLStreamWriter xml) throws XMLStreamException {
        root(xml, "styles");
        start(xml, "docDefaults");
        start(xml, "rPrDefault");
        start(xml, "rPr");
        fonts(xml, Documents.DEFAULT_FAMILY);
        size(xml, Documents.DEFAULT_SIZE);
        xml.writeEndElement();
        xml.writeEndElement();
        start(xml, "pPrDefault");
        start(xml, "pPr");
        empty(xml, "spacing", "before", "0", "after", "0", "line", "240", "lineRule", "auto");
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeStartElement("w", "style", W);
        xml.writeAttribute("w", W, "type", "paragraph");
        xml.writeAttribute("w", W, "default", "1");
        xml.writeAttribute("w", W, "styleId", "Normal");
        empty(xml, "name", "val", "Normal");
        empty(xml, "qFormat");
        xml.writeEndElement();
        xml.writeEndElement();
    }

    // ---------------------------------------------------------------- document.xml

    private void document(XMLStreamWriter xml) throws XMLStreamException {
        root(xml, "document");
        start(xml, "body");
        Element root = document.getDefaultRootElement();
        for (int p = 0; p < root.getElementCount(); p++) paragraph(xml, root.getElement(p));
        section(xml);
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private void section(XMLStreamWriter xml) throws XMLStreamException {
        start(xml, "sectPr");
        xml.writeEmptyElement("w", "pgSz", W);
        xml.writeAttribute("w", W, "w", String.valueOf(Ooxml.twips(settings.pageWidth())));
        xml.writeAttribute("w", W, "h", String.valueOf(Ooxml.twips(settings.pageHeight())));
        if (settings.orientation() == Orientation.LANDSCAPE) xml.writeAttribute("w", W, "orient", "landscape");
        Margins m = settings.margins();
        empty(xml, "pgMar", "top", String.valueOf(Ooxml.twips(m.top())),
                "right", String.valueOf(Ooxml.twips(m.right())),
                "bottom", String.valueOf(Ooxml.twips(m.bottom())),
                "left", String.valueOf(Ooxml.twips(m.left())),
                "header", "708", "footer", "708", "gutter", "0");
        xml.writeEndElement();
    }

    private void paragraph(XMLStreamWriter xml, Element paragraph) throws XMLStreamException {
        AttributeSet a = paragraph.getAttributes();
        start(xml, "p");
        start(xml, "pPr");
        // The order of these elements is fixed by the schema.
        if (ListAttributes.isListItem(a)) {
            start(xml, "numPr");
            empty(xml, "ilvl", "val", String.valueOf(ListAttributes.level(a)));
            empty(xml, "numId", "val", String.valueOf(numId(a)));
            xml.writeEndElement();
        }
        TabSet tabs = StyleConstants.getTabSet(a);
        if (tabs != null && tabs.getTabCount() > 0) {
            start(xml, "tabs");
            for (int i = 0; i < tabs.getTabCount(); i++) tab(xml, tabs.getTab(i));
            xml.writeEndElement();
        }
        float lineSpacing = StyleConstants.getLineSpacing(a);
        empty(xml, "spacing", "before", String.valueOf(Ooxml.twips(StyleConstants.getSpaceAbove(a))),
                "after", String.valueOf(Ooxml.twips(StyleConstants.getSpaceBelow(a))),
                "line", String.valueOf(Math.round((1 + Math.max(0, lineSpacing)) * 240)), "lineRule", "auto");
        float first = StyleConstants.getFirstLineIndent(a);
        xml.writeEmptyElement("w", "ind", W);
        xml.writeAttribute("w", W, "left", String.valueOf(Ooxml.twips(StyleConstants.getLeftIndent(a))));
        xml.writeAttribute("w", W, "right", String.valueOf(Ooxml.twips(StyleConstants.getRightIndent(a))));
        if (first < 0) xml.writeAttribute("w", W, "hanging", String.valueOf(Ooxml.twips(-first)));
        else xml.writeAttribute("w", W, "firstLine", String.valueOf(Ooxml.twips(first)));
        empty(xml, "jc", "val", switch (StyleConstants.getAlignment(a)) {
            case StyleConstants.ALIGN_CENTER -> "center";
            case StyleConstants.ALIGN_RIGHT -> "right";
            case StyleConstants.ALIGN_JUSTIFIED -> "both";
            default -> "left";
        });
        // The paragraph mark's format sets the height of an empty paragraph.
        int end = Math.max(paragraph.getStartOffset(), paragraph.getEndOffset() - 1);
        runProperties(xml, document.getCharacterElement(end).getAttributes());
        xml.writeEndElement();
        for (int r = 0; r < paragraph.getElementCount(); r++) {
            Element run = paragraph.getElement(r);
            int start = run.getStartOffset();
            int stop = Math.min(run.getEndOffset(), document.getLength());
            String text;
            try {
                text = document.getText(start, Math.max(0, stop - start));
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
            if (text.endsWith("\n")) text = text.substring(0, text.length() - 1);
            if (text.isEmpty()) continue;
            start(xml, "r");
            runProperties(xml, run.getAttributes());
            text(xml, text);
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    private static void tab(XMLStreamWriter xml, TabStop tab) throws XMLStreamException {
        xml.writeEmptyElement("w", "tab", W);
        xml.writeAttribute("w", W, "val", switch (tab.getAlignment()) {
            case TabStop.ALIGN_CENTER -> "center";
            case TabStop.ALIGN_RIGHT -> "right";
            case TabStop.ALIGN_DECIMAL -> "decimal";
            default -> "left";
        });
        String leader = switch (tab.getLeader()) {
            case TabStop.LEAD_DOTS -> "dot";
            case TabStop.LEAD_HYPHENS -> "hyphen";
            case TabStop.LEAD_UNDERLINE -> "underscore";
            case TabStop.LEAD_THICKLINE -> "heavy";
            default -> null;
        };
        if (leader != null) xml.writeAttribute("w", W, "leader", leader);
        xml.writeAttribute("w", W, "pos", String.valueOf(Math.max(0, Ooxml.twips(tab.getPosition()))));
    }

    /** Direct formatting of a run, in the schema's order. */
    private static void runProperties(XMLStreamWriter xml, AttributeSet a) throws XMLStreamException {
        start(xml, "rPr");
        fonts(xml, StyleConstants.getFontFamily(a));
        if (StyleConstants.isBold(a)) {
            empty(xml, "b");
            empty(xml, "bCs");
        }
        if (StyleConstants.isItalic(a)) {
            empty(xml, "i");
            empty(xml, "iCs");
        }
        if (StyleConstants.isStrikeThrough(a)) empty(xml, "strike");
        if (a.isDefined(StyleConstants.Foreground)) {
            Color c = StyleConstants.getForeground(a);
            empty(xml, "color", "val", String.format(Locale.ROOT, "%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue()));
        }
        size(xml, StyleConstants.getFontSize(a));
        if (StyleConstants.isUnderline(a)) empty(xml, "u", "val", "single");
        if (StyleConstants.isSuperscript(a)) empty(xml, "vertAlign", "val", "superscript");
        else if (StyleConstants.isSubscript(a)) empty(xml, "vertAlign", "val", "subscript");
        xml.writeEndElement();
    }

    private static void fonts(XMLStreamWriter xml, String family) throws XMLStreamException {
        empty(xml, "rFonts", "ascii", family, "hAnsi", family, "eastAsia", family, "cs", family);
    }

    private static void size(XMLStreamWriter xml, int points) throws XMLStreamException {
        String halfPoints = String.valueOf(points * 2);
        empty(xml, "sz", "val", halfPoints);
        empty(xml, "szCs", "val", halfPoints);
    }

    /** Text as {@code w:t}, tabs as {@code w:tab}, line breaks as {@code w:br}. */
    private static void text(XMLStreamWriter xml, String text) throws XMLStreamException {
        StringBuilder chars = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\t' || c == WritepadDocument.LINE_BREAK) {
                flush(xml, chars);
                empty(xml, c == '\t' ? "tab" : "br");
            } else if (c >= 0x20 && c != 0xFFFE && c != 0xFFFF) {
                chars.append(c);
            }
        }
        flush(xml, chars);
    }

    private static void flush(XMLStreamWriter xml, StringBuilder chars) throws XMLStreamException {
        if (chars.isEmpty()) return;
        start(xml, "t");
        xml.writeAttribute("xml", Ooxml.XML, "space", "preserve");
        xml.writeCharacters(chars.toString());
        xml.writeEndElement();
        chars.setLength(0);
    }

    // ---------------------------------------------------------------- numbering.xml

    /** The paragraph's list as {@code w:numId}; remembers the style of each level as first used. */
    private int numId(AttributeSet a) {
        int id = ListAttributes.list(a);
        int number = numIds.computeIfAbsent(id, k -> numIds.size() + 1);
        ListFormat[] levels = listLevels.computeIfAbsent(id, k -> new ListFormat[ListAttributes.MAX_LEVEL + 1]);
        int level = ListAttributes.level(a);
        if (levels[level] == null) levels[level] = ListAttributes.format(a);
        return number;
    }

    /**
     * One abstract numbering per list, its nine levels in the styles used (the others follow the first: all
     * bullets, or 1. a. i. for numbering), and one numbering instance pointing to it.
     */
    private void numbering(XMLStreamWriter xml) throws XMLStreamException {
        root(xml, "numbering");
        for (Map.Entry<Integer, Integer> list : numIds.entrySet()) {
            ListFormat[] levels = listLevels.get(list.getKey());
            ListFormat first = Arrays.stream(levels).filter(Objects::nonNull).findFirst().orElse(ListFormat.BULLET);
            xml.writeStartElement("w", "abstractNum", W);
            xml.writeAttribute("w", W, "abstractNumId", String.valueOf(list.getValue()));
            empty(xml, "multiLevelType", "val", "hybridMultilevel");
            for (int level = 0; level < levels.length; level++) {
                ListFormat format = levels[level] != null ? levels[level]
                        : first.isBullet() ? ListFormat.BULLET : ListFormat.numberingFor(level);
                xml.writeStartElement("w", "lvl", W);
                xml.writeAttribute("w", W, "ilvl", String.valueOf(level));
                empty(xml, "start", "val", "1");
                empty(xml, "numFmt", "val", format.docxCode());
                empty(xml, "lvlText", "val", format.isBullet() ? format.label(1, level) : "%" + (level + 1) + ".");
                empty(xml, "lvlJc", "val", "left");
                start(xml, "pPr");
                empty(xml, "ind", "left", String.valueOf(Ooxml.twips(ListAttributes.leftIndent(level))),
                        "hanging", String.valueOf(Ooxml.twips(ListAttributes.STEP)));
                xml.writeEndElement();
                xml.writeEndElement();
            }
            xml.writeEndElement();
        }
        for (int number : numIds.values()) {
            xml.writeStartElement("w", "num", W);
            xml.writeAttribute("w", W, "numId", String.valueOf(number));
            empty(xml, "abstractNumId", "val", String.valueOf(number));
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }
}
