package eu.amadeos.apps.writepad.service.format.docx;

import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.domain.Margins;
import eu.amadeos.apps.writepad.domain.Orientation;
import eu.amadeos.apps.writepad.domain.PaperSize;
import eu.amadeos.apps.writepad.domain.Units;
import eu.amadeos.apps.writepad.service.format.DocumentContent;
import eu.amadeos.apps.writepad.service.format.Documents;
import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import eu.amadeos.apps.writepad.service.format.odt.OdtFormat;
import eu.amadeos.apps.writepad.service.list.ListAttributes;
import eu.amadeos.apps.writepad.service.list.ListFormat;
import eu.amadeos.apps.writepad.service.list.ListNumbering;
import org.junit.jupiter.api.Test;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxFormatTest {
    private static final String NS = "xmlns:w=\"" + Ooxml.W + "\" xmlns:a=\"" + Ooxml.A + "\"";

    private final DocxFormat format = new DocxFormat();

    private DocumentContent fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/docx/" + name)) {
            if (in == null) throw new IOException("Missing fixture " + name);
            return format.read(in.readAllBytes());
        }
    }

    private static String text(DocumentContent content) {
        StyledDocument doc = content.document();
        try {
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            throw new AssertionError(e);
        }
    }

    private static AttributeSet charAt(DocumentContent content, String text) {
        int offset = text(content).indexOf(text);
        assertTrue(offset >= 0, "no '" + text + "' in " + text(content));
        return content.document().getCharacterElement(offset).getAttributes();
    }

    private static AttributeSet paragraphAt(DocumentContent content, String text) {
        int offset = text(content).indexOf(text);
        assertTrue(offset >= 0, "no '" + text + "'");
        return content.document().getParagraphElement(offset).getAttributes();
    }

    private static String labels(DocumentContent c) {
        StringBuilder s = new StringBuilder();
        Element root = c.document().getDefaultRootElement();
        for (int i = 0; i < root.getElementCount(); i++) {
            String label = ListNumbering.label(c.document(), root.getElement(i));
            if (i > 0) s.append(' ');
            s.append(label == null ? "-" : label);
        }
        return s.toString();
    }

    private static Map<String, byte[]> unzip(byte[] docx) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) files.put(entry.getName(), zip.readAllBytes());
        }
        return files;
    }

    private static String part(byte[] docx, String name) throws IOException {
        return new String(unzip(docx).get(name), StandardCharsets.UTF_8);
    }

    /** A DOCX with the given parts (document, styles, numbering, theme), as Word could write it. */
    private static byte[] docx(String body, String styles, String numbering, String theme) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "_rels/.rels", "<Relationships xmlns=\"" + Ooxml.PACKAGE_RELATIONSHIPS + "\"><Relationship"
                    + " Id=\"rId1\" Type=\"" + Ooxml.OFFICE_DOCUMENT + "\" Target=\"word/document.xml\"/></Relationships>");
            put(zip, "word/_rels/document.xml.rels", "<Relationships xmlns=\"" + Ooxml.PACKAGE_RELATIONSHIPS + "\">"
                    + "<Relationship Id=\"rId1\" Type=\"" + Ooxml.STYLES + "\" Target=\"styles.xml\"/>"
                    + "<Relationship Id=\"rId2\" Type=\"" + Ooxml.NUMBERING + "\" Target=\"numbering.xml\"/>"
                    + "<Relationship Id=\"rId3\" Type=\"" + Ooxml.THEME + "\" Target=\"theme/theme1.xml\"/>"
                    + "</Relationships>");
            put(zip, "word/document.xml", "<w:document " + NS + "><w:body>" + body + "</w:body></w:document>");
            if (styles != null) put(zip, "word/styles.xml", "<w:styles " + NS + ">" + styles + "</w:styles>");
            if (numbering != null) put(zip, "word/numbering.xml", "<w:numbering " + NS + ">" + numbering + "</w:numbering>");
            if (theme != null) put(zip, "word/theme/theme1.xml", "<a:theme " + NS + "><a:themeElements><a:fontScheme>"
                    + theme + "</a:fontScheme></a:themeElements></a:theme>");
        }
        return out.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    @Test
    void writesAPackageWordRecognises() throws IOException, BadLocationException {
        StyledDocument doc = Documents.newDocument();
        doc.insertString(0, "Hallo", null);
        byte[] docx = format.write(new DocumentContent(doc, DocumentSettings.DEFAULT));
        assertTrue(format.recognizes(docx));
        assertFalse(new OdtFormat().recognizes(docx));
        Map<String, byte[]> files = unzip(docx);
        assertTrue(files.keySet().containsAll(List.of("[Content_Types].xml", "_rels/.rels", "word/document.xml",
                "word/styles.xml", "word/settings.xml", "word/_rels/document.xml.rels")), files.keySet().toString());
        assertFalse(files.containsKey("word/numbering.xml"), "no lists, no numbering part");
        assertTrue(part(docx, "[Content_Types].xml").contains(Ooxml.MAIN_TYPE));
    }

    @Test
    void recognisesOnlyWordDocuments() throws IOException, BadLocationException {
        assertFalse(format.recognizes("{\\rtf1 x}".getBytes(StandardCharsets.US_ASCII)));
        StyledDocument doc = Documents.newDocument();
        doc.insertString(0, "x", null);
        assertFalse(format.recognizes(new OdtFormat().write(new DocumentContent(doc, DocumentSettings.DEFAULT))));
        assertThrows(IOException.class, () -> format.read("not a zip".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void writesWhatItReads() throws IOException, BadLocationException {
        StyledDocument doc = Documents.newDocument();
        SimpleAttributeSet bold = new SimpleAttributeSet();
        StyleConstants.setFontFamily(bold, "Liberation Sans");
        StyleConstants.setFontSize(bold, 20);
        StyleConstants.setBold(bold, true);
        StyleConstants.setForeground(bold, new Color(0x1F3A5F));
        SimpleAttributeSet special = new SimpleAttributeSet();
        StyleConstants.setItalic(special, true);
        StyleConstants.setUnderline(special, true);
        StyleConstants.setStrikeThrough(special, true);
        StyleConstants.setSuperscript(special, true);
        SimpleAttributeSet sub = new SimpleAttributeSet();
        StyleConstants.setSubscript(sub, true);
        doc.insertString(0, "Title\n", bold);
        doc.insertString(doc.getLength(), "  Größe <tags> & \"quotes\"\tafter tab € Привет 😀  two spaces ", null);
        doc.insertString(doc.getLength(), "special", special);
        doc.insertString(doc.getLength(), " H", null);
        doc.insertString(doc.getLength(), "2", sub);
        doc.insertString(doc.getLength(), "O next line\n\nAfter an empty paragraph", null);
        SimpleAttributeSet centre = new SimpleAttributeSet();
        StyleConstants.setAlignment(centre, StyleConstants.ALIGN_CENTER);
        StyleConstants.setSpaceAbove(centre, 3);
        StyleConstants.setSpaceBelow(centre, 6);
        StyleConstants.setLineSpacing(centre, 0.5f);
        doc.setParagraphAttributes(0, 1, centre, false);
        SimpleAttributeSet indented = new SimpleAttributeSet();
        StyleConstants.setLeftIndent(indented, 36);
        StyleConstants.setRightIndent(indented, 12);
        StyleConstants.setFirstLineIndent(indented, -18);
        StyleConstants.setAlignment(indented, StyleConstants.ALIGN_JUSTIFIED);
        doc.setParagraphAttributes(8, 1, indented, false);
        SimpleAttributeSet firstLine = new SimpleAttributeSet();
        StyleConstants.setFirstLineIndent(firstLine, 14);
        doc.setParagraphAttributes(doc.getLength() - 2, 1, firstLine, false);
        DocumentSettings settings = new DocumentSettings(PaperSize.A5, Orientation.LANDSCAPE,
                Margins.ofMm(10, 15, 12, 18));

        DocumentContent back = format.read(format.write(new DocumentContent(doc, settings)));

        assertEquals(text(new DocumentContent(doc, settings)), text(back));
        AttributeSet title = charAt(back, "Title");
        assertEquals("Liberation Sans", StyleConstants.getFontFamily(title));
        assertEquals(20, StyleConstants.getFontSize(title));
        assertTrue(StyleConstants.isBold(title));
        assertEquals(new Color(0x1F3A5F), StyleConstants.getForeground(title));
        AttributeSet titleParagraph = paragraphAt(back, "Title");
        assertEquals(StyleConstants.ALIGN_CENTER, StyleConstants.getAlignment(titleParagraph));
        assertEquals(3f, StyleConstants.getSpaceAbove(titleParagraph), 0.01);
        assertEquals(6f, StyleConstants.getSpaceBelow(titleParagraph), 0.01);
        assertEquals(0.5f, StyleConstants.getLineSpacing(titleParagraph), 0.01);
        AttributeSet plain = charAt(back, "Größe");
        assertEquals(Documents.DEFAULT_FAMILY, StyleConstants.getFontFamily(plain));
        assertEquals(Documents.DEFAULT_SIZE, StyleConstants.getFontSize(plain));
        assertFalse(StyleConstants.isBold(plain));
        assertNull(plain.getAttribute(StyleConstants.Foreground));
        AttributeSet s = charAt(back, "special");
        assertTrue(StyleConstants.isItalic(s) && StyleConstants.isUnderline(s) && StyleConstants.isStrikeThrough(s)
                && StyleConstants.isSuperscript(s));
        assertTrue(StyleConstants.isSubscript(charAt(back, "2O")));
        assertFalse(StyleConstants.isSubscript(charAt(back, "O ")));
        assertTrue(WritepadDocument.isLineBreak(charAt(back, " next")));
        AttributeSet second = paragraphAt(back, "Größe");
        assertEquals(36f, StyleConstants.getLeftIndent(second), 0.01);
        assertEquals(12f, StyleConstants.getRightIndent(second), 0.01);
        assertEquals(-18f, StyleConstants.getFirstLineIndent(second), 0.01);
        assertEquals(StyleConstants.ALIGN_JUSTIFIED, StyleConstants.getAlignment(second));
        assertEquals(14f, StyleConstants.getFirstLineIndent(paragraphAt(back, "After")), 0.01);
        assertEquals(StyleConstants.ALIGN_LEFT, StyleConstants.getAlignment(paragraphAt(back, "After")));
        assertEquals(settings.paper(), back.settings().paper());
        assertEquals(settings.orientation(), back.settings().orientation());
        assertEquals(settings.margins().top(), back.settings().margins().top(), 0.05);
        assertEquals(settings.margins().left(), back.settings().margins().left(), 0.05);
        assertEquals(settings.margins().bottom(), back.settings().margins().bottom(), 0.05);
        assertEquals(settings.margins().right(), back.settings().margins().right(), 0.05);
    }

    @Test
    void emptyParagraphsKeepTheirFont() throws IOException, BadLocationException {
        StyledDocument doc = Documents.newDocument();
        SimpleAttributeSet big = new SimpleAttributeSet();
        StyleConstants.setFontSize(big, 28);
        doc.insertString(0, "a\n\nb", null);
        doc.setCharacterAttributes(2, 1, big, false);
        DocumentContent back = format.read(format.write(new DocumentContent(doc, DocumentSettings.DEFAULT)));
        assertEquals(28, StyleConstants.getFontSize(back.document().getCharacterElement(2).getAttributes()));
    }

    @Test
    void writesTabStopsFromTheMargin() throws IOException, BadLocationException {
        StyledDocument doc = Documents.newDocument();
        doc.insertString(0, "a\tb\tc", null);
        SimpleAttributeSet p = new SimpleAttributeSet();
        StyleConstants.setTabSet(p, new TabSet(new TabStop[]{new TabStop(56.7f),
                new TabStop(141.75f, TabStop.ALIGN_CENTER, TabStop.LEAD_NONE),
                new TabStop(300f, TabStop.ALIGN_RIGHT, TabStop.LEAD_DOTS),
                new TabStop(400f, TabStop.ALIGN_DECIMAL, TabStop.LEAD_HYPHENS)}));
        StyleConstants.setLeftIndent(p, 56.7f);
        StyleConstants.setFirstLineIndent(p, -28.35f);
        doc.setParagraphAttributes(0, 1, p, false);
        byte[] docx = format.write(new DocumentContent(doc, DocumentSettings.DEFAULT));
        String xml = part(docx, "word/document.xml");
        assertTrue(xml.contains("<w:tab w:val=\"left\" w:pos=\"1134\"/>"), xml);
        assertTrue(xml.contains("<w:ind w:left=\"1134\" w:right=\"0\" w:hanging=\"567\"/>"), xml);
        TabSet back = StyleConstants.getTabSet(paragraphAt(format.read(docx), "a"));
        assertEquals(4, back.getTabCount());
        for (int i = 0; i < 4; i++) {
            TabStop expected = StyleConstants.getTabSet(p).getTab(i);
            assertEquals(expected.getPosition(), back.getTab(i).getPosition(), 0.05f);
            assertEquals(expected.getAlignment(), back.getTab(i).getAlignment());
            assertEquals(expected.getLeader(), back.getTab(i).getLeader());
        }
    }

    @Test
    void writesListsThatReadBack() throws IOException, BadLocationException {
        StyledDocument doc = Documents.newDocument();
        doc.insertString(0, "a\nb\nc\nd\ne\nf\ng\nh\ni", null);
        Element root = doc.getDefaultRootElement();
        Object[][] items = {{1, 0, ListFormat.BULLET}, {1, 1, ListFormat.BULLET}, {0, 0, ListFormat.BULLET},
                {2, 0, ListFormat.UPPER_ROMAN}, {2, 2, ListFormat.LOWER_ROMAN}, {2, 0, ListFormat.UPPER_ROMAN},
                {0, 0, ListFormat.BULLET}, {2, 0, ListFormat.UPPER_ROMAN}, {3, 0, ListFormat.DECIMAL}};
        for (int i = 0; i < items.length; i++) {
            SimpleAttributeSet a = new SimpleAttributeSet();
            int level = (Integer) items[i][1];
            ListAttributes.set(a, (Integer) items[i][0], level, (ListFormat) items[i][2]);
            if ((Integer) items[i][0] != 0) {
                StyleConstants.setLeftIndent(a, ListAttributes.leftIndent(level));
                StyleConstants.setFirstLineIndent(a, -ListAttributes.STEP);
            }
            doc.setParagraphAttributes(root.getElement(i).getStartOffset(), 1, a, false);
        }
        byte[] docx = format.write(new DocumentContent(doc, DocumentSettings.DEFAULT));
        assertTrue(part(docx, "word/numbering.xml").contains("<w:numFmt w:val=\"upperRoman\"/>"));
        DocumentContent back = format.read(docx);
        assertEquals("a\nb\nc\nd\ne\nf\ng\nh\ni", text(back));
        assertEquals("• ◦ - I. i. II. - III. 1.", labels(back), "list 2 goes on after g, list 3 starts at 1");
        AttributeSet e = paragraphAt(back, "e");
        assertEquals(2, ListAttributes.level(e));
        assertEquals(ListAttributes.leftIndent(2), StyleConstants.getLeftIndent(e), 0.01);
        assertEquals(-ListAttributes.STEP, StyleConstants.getFirstLineIndent(e), 0.01);
    }

    /** Written by LibreOffice 24.2 (soffice --convert-to docx) from the RTF fixture it wrote itself. */
    @Test
    void readsLibreOfficeFile() throws IOException {
        DocumentContent c = fixture("libreoffice.docx");
        assertEquals("Titel\nEin Absatz mit kursivem und blauem fetten Text. Euro: € und „Zitat“. Grüße, weg, Привет.",
                text(c));
        AttributeSet title = charAt(c, "Titel");
        assertEquals("Liberation Sans", StyleConstants.getFontFamily(title));
        assertEquals(18, StyleConstants.getFontSize(title));
        assertTrue(StyleConstants.isBold(title));
        AttributeSet titleParagraph = paragraphAt(c, "Titel");
        assertEquals(StyleConstants.ALIGN_CENTER, StyleConstants.getAlignment(titleParagraph));
        assertEquals(12f, StyleConstants.getSpaceAbove(titleParagraph), 0.05);
        assertEquals(6f, StyleConstants.getSpaceBelow(titleParagraph), 0.05);
        AttributeSet body = charAt(c, "Ein Absatz");
        assertEquals("Liberation Serif", StyleConstants.getFontFamily(body));
        assertEquals(12, StyleConstants.getFontSize(body));
        assertFalse(StyleConstants.isBold(body));
        assertNull(body.getAttribute(StyleConstants.Foreground));
        assertTrue(StyleConstants.isItalic(charAt(c, "kursivem")));
        assertEquals(Color.BLUE, StyleConstants.getForeground(charAt(c, "blauem")));
        assertTrue(StyleConstants.isBold(charAt(c, "blauem")));
        assertTrue(StyleConstants.isStrikeThrough(charAt(c, "weg")) && StyleConstants.isUnderline(charAt(c, "weg")));
        AttributeSet bodyParagraph = paragraphAt(c, "Ein Absatz");
        assertEquals(StyleConstants.ALIGN_JUSTIFIED, StyleConstants.getAlignment(bodyParagraph));
        assertEquals(36f, StyleConstants.getLeftIndent(bodyParagraph), 0.05);
        assertEquals(-18f, StyleConstants.getFirstLineIndent(bodyParagraph), 0.05);
        assertEquals(PaperSize.A4, c.settings().paper());
        assertEquals(Orientation.PORTRAIT, c.settings().orientation());
        assertEquals(Units.mmToPoints(20), c.settings().margins().left(), 0.1);
        assertEquals(Units.mmToPoints(20), c.settings().margins().top(), 0.1);
    }

    /** LibreOffice's DOCX of a bulleted, a nested numbered and a Roman list. */
    @Test
    void readsLibreOfficeLists() throws IOException {
        DocumentContent c = fixture("libreoffice-lists.docx");
        assertEquals("Intro\nApfel\nBirne\nErstens\nUnterpunkt\nNoch einer\nZweitens\nKapitel\nKapitel", text(c));
        assertEquals("- • • 1. a. b. 2. I. II.", labels(c));
        AttributeSet sub = paragraphAt(c, "Unterpunkt");
        assertEquals(1, ListAttributes.level(sub));
        assertEquals(54f, StyleConstants.getLeftIndent(sub), 0.1f);
        assertEquals(-18f, StyleConstants.getFirstLineIndent(sub), 0.1f);
    }

    @Test
    void resolvesStylesThemeFontsAndDefaultsAsWordDoes() throws IOException {
        String styles = "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:asciiTheme=\"minorHAnsi\"/>"
                + "<w:sz w:val=\"22\"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr>"
                + "<w:spacing w:after=\"160\" w:line=\"276\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>"
                + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>"
                + "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:basedOn w:val=\"Normal\"/><w:pPr>"
                + "<w:spacing w:before=\"240\" w:after=\"0\"/><w:jc w:val=\"center\"/></w:pPr><w:rPr>"
                + "<w:rFonts w:asciiTheme=\"majorHAnsi\"/><w:b/><w:sz w:val=\"32\"/><w:color w:val=\"2F5496\"/></w:rPr></w:style>"
                + "<w:style w:type=\"paragraph\" w:styleId=\"Quiet\"><w:basedOn w:val=\"Heading1\"/><w:rPr><w:b w:val=\"0\"/>"
                + "</w:rPr></w:style>"
                + "<w:style w:type=\"character\" w:styleId=\"Strong\"><w:rPr><w:b/><w:i/></w:rPr></w:style>";
        String theme = "<a:majorFont><a:latin typeface=\"Calibri Light\"/></a:majorFont>"
                + "<a:minorFont><a:latin typeface=\"Calibri\"/></a:minorFont>";
        DocumentContent c = format.read(docx("<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:t>Head</w:t>"
                + "</w:r></w:p><w:p><w:r><w:t xml:space=\"preserve\">Body </w:t></w:r><w:r><w:rPr>"
                + "<w:rStyle w:val=\"Strong\"/></w:rPr><w:t>strong</w:t></w:r></w:p>"
                + "<w:p><w:pPr><w:pStyle w:val=\"Quiet\"/></w:pPr><w:r><w:t>Quiet</w:t></w:r></w:p>", styles, null, theme));
        assertEquals("Head\nBody strong\nQuiet", text(c));
        AttributeSet head = charAt(c, "Head");
        assertEquals("Calibri Light", StyleConstants.getFontFamily(head));
        assertEquals(16, StyleConstants.getFontSize(head));
        assertTrue(StyleConstants.isBold(head));
        assertEquals(new Color(0x2F5496), StyleConstants.getForeground(head));
        AttributeSet headParagraph = paragraphAt(c, "Head");
        assertEquals(StyleConstants.ALIGN_CENTER, StyleConstants.getAlignment(headParagraph));
        assertEquals(12f, StyleConstants.getSpaceAbove(headParagraph), 0.01);
        assertEquals(0f, StyleConstants.getSpaceBelow(headParagraph), 0.01);
        AttributeSet body = charAt(c, "Body");
        assertEquals("Calibri", StyleConstants.getFontFamily(body));
        assertEquals(11, StyleConstants.getFontSize(body));
        assertFalse(StyleConstants.isBold(body));
        assertEquals(8f, StyleConstants.getSpaceBelow(paragraphAt(c, "Body")), 0.01);
        assertEquals(0.15f, StyleConstants.getLineSpacing(paragraphAt(c, "Body")), 0.01);
        assertTrue(StyleConstants.isBold(charAt(c, "strong")) && StyleConstants.isItalic(charAt(c, "strong")));
        assertFalse(StyleConstants.isBold(charAt(c, "Quiet")), "a style can switch off what its parent switched on");
        assertEquals(16, StyleConstants.getFontSize(charAt(c, "Quiet")));
    }

    @Test
    void keepsTheTextOfTablesFieldsAndLinksAndSkipsTheRest() throws IOException {
        DocumentContent c = format.read(docx("<w:p><w:r><w:t xml:space=\"preserve\">See </w:t></w:r>"
                + "<w:hyperlink><w:r><w:t>the link</w:t></w:r></w:hyperlink><w:r><w:footnoteReference w:id=\"1\"/>"
                + "</w:r><w:r><w:t xml:space=\"preserve\">, page </w:t></w:r><w:r><w:fldChar w:fldCharType=\"begin\"/>"
                + "</w:r><w:r><w:instrText xml:space=\"preserve\"> PAGE </w:instrText></w:r><w:r>"
                + "<w:fldChar w:fldCharType=\"separate\"/></w:r><w:r><w:t>3</w:t></w:r><w:r>"
                + "<w:fldChar w:fldCharType=\"end\"/></w:r><w:del><w:r><w:delText>gone</w:delText></w:r></w:del>"
                + "<w:ins><w:r><w:t>.</w:t></w:r></w:ins><w:r><w:drawing>picture</w:drawing></w:r></w:p>"
                + "<w:tbl><w:tblGrid><w:gridCol w:w=\"100\"/></w:tblGrid><w:tr><w:tc><w:p><w:r><w:t>A1</w:t></w:r></w:p>"
                + "</w:tc><w:tc><w:p><w:r><w:t>B1</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"
                + "<w:p><w:r><w:t>One</w:t><w:br/><w:t>two</w:t><w:br w:type=\"page\"/><w:tab/><w:t>three</w:t></w:r></w:p>",
                null, null, null));
        assertEquals("See the link, page 3.\nA1\nB1\nOne two\tthree", text(c));
        assertEquals("Times New Roman", StyleConstants.getFontFamily(charAt(c, "See")), "Word's default font");
        assertEquals(10, StyleConstants.getFontSize(charAt(c, "See")), "Word's default size");
    }

    @Test
    void countsListsAsWordDoes() throws IOException {
        String levels = "<w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"decimal\"/><w:lvlText w:val=\"%1.\"/>"
                + "<w:pPr><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr></w:lvl>"
                + "<w:lvl w:ilvl=\"1\"><w:numFmt w:val=\"none\"/><w:pPr><w:ind w:left=\"1440\" w:hanging=\"360\"/></w:pPr></w:lvl>";
        String numbering = "<w:abstractNum w:abstractNumId=\"0\">" + levels + "</w:abstractNum>"
                + "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num>"
                + "<w:num w:numId=\"2\"><w:abstractNumId w:val=\"0\"/></w:num>"
                + "<w:num w:numId=\"3\"><w:abstractNumId w:val=\"0\"/><w:lvlOverride w:ilvl=\"0\">"
                + "<w:startOverride w:val=\"1\"/></w:lvlOverride></w:num>";
        String styles = "<w:style w:type=\"paragraph\" w:styleId=\"ListNumber\"><w:pPr><w:numPr>"
                + "<w:numId w:val=\"1\"/></w:numPr></w:pPr></w:style>";
        DocumentContent c = format.read(docx(item(1, 0) + item(1, 0) + "<w:p><w:r><w:t>-</w:t></w:r></w:p>"
                + item(2, 0) + item(3, 0) + item(1, 1) + "<w:p><w:pPr><w:pStyle w:val=\"ListNumber\"/></w:pPr>"
                + "<w:r><w:t>styled</w:t></w:r></w:p><w:p><w:pPr><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/>"
                + "</w:numPr><w:ind w:left=\"0\" w:firstLine=\"0\"/></w:pPr><w:r><w:t>direct</w:t></w:r></w:p>",
                styles, numbering, null));
        assertEquals("1. 2. - 3. 1. - 4. 5.", labels(c), "numIds of one list count on, a restart counts alone");
        assertEquals(36f, StyleConstants.getLeftIndent(paragraphAt(c, "styled")), 0.01, "the list's indent");
        assertEquals(0f, StyleConstants.getLeftIndent(paragraphAt(c, "direct")), 0.01, "direct indent wins");
        assertEquals(72f, StyleConstants.getLeftIndent(c.document().getDefaultRootElement().getElement(5)
                .getAttributes()), 0.01, "a level without numbers keeps its indent");
    }

    private static String item(int numId, int level) {
        return "<w:p><w:pPr><w:numPr><w:ilvl w:val=\"" + level + "\"/><w:numId w:val=\"" + numId + "\"/></w:numPr>"
                + "</w:pPr><w:r><w:t>x</w:t></w:r></w:p>";
    }
}
