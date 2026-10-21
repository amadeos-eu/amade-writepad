package eu.amadeos.apps.writepad.service.format.odt;

import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.domain.Margins;
import eu.amadeos.apps.writepad.domain.Orientation;
import eu.amadeos.apps.writepad.domain.Units;
import eu.amadeos.apps.writepad.service.format.DocumentContent;
import eu.amadeos.apps.writepad.service.format.Documents;
import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import eu.amadeos.apps.writepad.service.list.ListAttributes;
import eu.amadeos.apps.writepad.service.list.ListFormat;
import eu.amadeos.apps.writepad.service.list.ListNumbering;
import eu.amadeos.commons.print.PaperSize;
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
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OdtFormatTest {
    private final OdtFormat format = new OdtFormat();

    private DocumentContent fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/odt/" + name)) {
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

    private static Map<String, byte[]> unzip(byte[] odt) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(odt))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) files.put(entry.getName(), zip.readAllBytes());
        }
        return files;
    }

    private static String content(byte[] odt) throws IOException {
        return new String(unzip(odt).get("content.xml"), StandardCharsets.UTF_8);
    }

    /** An ODT with the given body, as other programs could write it. */
    private static byte[] odt(String body) throws IOException {
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><office:document-content"
                + " xmlns:office=\"" + Odf.OFFICE + "\" xmlns:style=\"" + Odf.STYLE + "\" xmlns:text=\"" + Odf.TEXT
                + "\" xmlns:fo=\"" + Odf.FO + "\" xmlns:table=\"" + Odf.TABLE + "\" xmlns:draw=\"" + Odf.DRAW
                + "\" office:version=\"1.3\">" + body + "</office:document-content>";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    // --8<-- [start:package]
    @Test
    void writesAPackageOtherProgramsRecognise() throws IOException, BadLocationException {
        StyledDocument doc = Documents.newDocument();
        doc.insertString(0, "Hallo", null);
        byte[] odt = format.write(new DocumentContent(doc, DocumentSettings.DEFAULT));
        assertTrue(format.recognizes(odt));
        String head = new String(odt, 30, 8 + Odf.MIME_TYPE.length(), StandardCharsets.US_ASCII);
        assertEquals("mimetype" + Odf.MIME_TYPE, head, "mimetype first and stored");
        Map<String, byte[]> files = unzip(odt);
        assertTrue(files.keySet().containsAll(java.util.List.of("content.xml", "styles.xml", "meta.xml",
                "META-INF/manifest.xml")), files.keySet().toString());
        assertTrue(new String(files.get("META-INF/manifest.xml"), StandardCharsets.UTF_8).contains(Odf.MIME_TYPE));
    }
    // --8<-- [end:package]

    @Test
    void recognisesOnlyOpenDocumentText() throws IOException {
        assertFalse(format.recognizes("{\\rtf1 x}".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(format.recognizes(odt("<office:body/>")), "a ZIP without mimetype, like DOCX");
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
        doc.insertString(doc.getLength(), "Größe <tags> & \"quotes\"\tafter tab € Привет 😀 ", null);
        doc.insertString(doc.getLength(), "special", special);
        doc.insertString(doc.getLength(), " H", null);
        doc.insertString(doc.getLength(), "2", sub);
        doc.insertString(doc.getLength(), "O\n\nAfter an empty paragraph", null);
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
        assertFalse(StyleConstants.isSubscript(charAt(back, "O\n")));
        AttributeSet second = paragraphAt(back, "Größe");
        assertEquals(36f, StyleConstants.getLeftIndent(second), 0.01);
        assertEquals(12f, StyleConstants.getRightIndent(second), 0.01);
        assertEquals(-18f, StyleConstants.getFirstLineIndent(second), 0.01);
        assertEquals(StyleConstants.ALIGN_JUSTIFIED, StyleConstants.getAlignment(second));
        assertEquals(StyleConstants.ALIGN_LEFT, StyleConstants.getAlignment(paragraphAt(back, "After")));
        assertEquals(settings.paper(), back.settings().paper());
        assertEquals(settings.orientation(), back.settings().orientation());
        assertEquals(settings.margins().top(), back.settings().margins().top(), 0.01);
        assertEquals(settings.margins().left(), back.settings().margins().left(), 0.01);
        assertEquals(settings.margins().bottom(), back.settings().margins().bottom(), 0.01);
        assertEquals(settings.margins().right(), back.settings().margins().right(), 0.01);
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
    void keepsSpacesTabsAndLineBreaks() throws IOException, BadLocationException {
        StyledDocument doc = Documents.newDocument();
        String text = "  two leading,   three inside, trailing  \nTab\there new line   spaced";
        doc.insertString(0, text, null);
        byte[] odt = format.write(new DocumentContent(doc, DocumentSettings.DEFAULT));
        String xml = content(odt);
        assertTrue(xml.contains("<text:s text:c=\"2\"/>two leading, <text:s text:c=\"2\"/>three"), xml);
        assertTrue(xml.contains("Tab<text:tab/>here<text:line-break/>new line <text:line-break/> spaced"), xml);
        DocumentContent back = format.read(odt);
        assertEquals(text, text(back));
        assertTrue(WritepadDocument.isLineBreak(charAt(back, " new")));
    }

    @Test
    void collapsesWhiteSpaceAsOdfSays() throws IOException {
        DocumentContent c = format.read(odt("<office:body><office:text>"
                + "<text:p>  Leading\n   and   <text:span> spread </text:span> out<text:s/>x<text:s text:c=\"3\"/>y</text:p>"
                + "<text:p>a<text:tab/> b<text:line-break/> c</text:p></office:text></office:body>"));
        assertEquals("Leading and spread out x   y\na\t b  c", text(c));
    }

    @Test
    void writesTabStopsRelativeToTheIndent() throws IOException, BadLocationException {
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
        byte[] odt = format.write(new DocumentContent(doc, DocumentSettings.DEFAULT));
        assertTrue(content(odt).contains("<style:tab-stop style:position=\"0cm\"/>"), content(odt));
        TabSet back = StyleConstants.getTabSet(paragraphAt(format.read(odt), "a"));
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
        byte[] odt = format.write(new DocumentContent(doc, DocumentSettings.DEFAULT));
        String xml = content(odt);
        assertTrue(xml.contains("text:continue-list=\"list2-2\""), xml);
        DocumentContent back = format.read(odt);
        assertEquals("a\nb\nc\nd\ne\nf\ng\nh\ni", text(back));
        assertEquals("• ◦ - I. i. II. - III. 1.", labels(back), "list 2 goes on after g, list 3 starts at 1");
        AttributeSet e = paragraphAt(back, "e");
        assertEquals(2, ListAttributes.level(e));
        assertEquals(ListAttributes.leftIndent(2), StyleConstants.getLeftIndent(e), 0.01);
        assertEquals(-ListAttributes.STEP, StyleConstants.getFirstLineIndent(e), 0.01);
    }

    /** Written by LibreOffice 24.2 (soffice --convert-to odt) from the RTF fixture it wrote itself. */
    @Test
    void readsLibreOfficeFile() throws IOException {
        DocumentContent c = fixture("libreoffice.odt");
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

    /** LibreOffice's ODT of a bulleted, a nested numbered and a Roman list. */
    @Test
    void readsLibreOfficeLists() throws IOException {
        DocumentContent c = fixture("libreoffice-lists.odt");
        assertEquals("Intro\nApfel\nBirne\nErstens\nUnterpunkt\nNoch einer\nZweitens\nKapitel\nKapitel", text(c));
        assertEquals("- • • 1. a. b. 2. I. II.", labels(c));
        AttributeSet sub = paragraphAt(c, "Unterpunkt");
        assertEquals(1, ListAttributes.level(sub));
        assertEquals(54f, StyleConstants.getLeftIndent(sub), 0.1f);
        assertEquals(-18f, StyleConstants.getFirstLineIndent(sub), 0.1f);
    }

    @Test
    void keepsTheTextOfTablesFieldsAndLinksAndSkipsTheRest() throws IOException {
        DocumentContent c = format.read(odt("<office:body><office:text>"
                + "<text:sequence-decls><text:sequence-decl text:name=\"Table\"/></text:sequence-decls>"
                + "<text:p>See <text:a>the link</text:a><text:note><text:note-body><text:p>note</text:p>"
                + "</text:note-body></text:note>, page <text:page-number>3</text:page-number>"
                + "<draw:frame><draw:text-box><text:p>frame</text:p></draw:text-box></draw:frame>.</text:p>"
                + "<table:table><table:table-column/><table:table-row><table:table-cell><text:p>A1</text:p>"
                + "</table:table-cell><table:table-cell><text:p>B1</text:p></table:table-cell></table:table-row>"
                + "</table:table><text:h text:outline-level=\"1\">Heading</text:h>"
                + "</office:text></office:body>"));
        assertEquals("See the link, page 3.\nA1\nB1\nHeading", text(c));
    }

    @Test
    void continuesNumberingAsAsked() throws IOException {
        String item = "<text:list-item><text:p>x</text:p></text:list-item>";
        DocumentContent c = format.read(odt("<office:automatic-styles><text:list-style style:name=\"N\">"
                + "<text:list-level-style-number text:level=\"1\" style:num-format=\"1\"/></text:list-style>"
                + "</office:automatic-styles><office:body><office:text>"
                + "<text:list text:style-name=\"N\">" + item + item + "</text:list><text:p>-</text:p>"
                + "<text:list text:style-name=\"N\" text:continue-numbering=\"true\">" + item + "</text:list>"
                + "<text:list text:style-name=\"N\">" + item + "</text:list>"
                + "<text:list text:style-name=\"N\"><text:list-item><text:p>x</text:p><text:p>more</text:p>"
                + "</text:list-item><text:list-header><text:p>header</text:p></text:list-header></text:list>"
                + "</office:text></office:body>"));
        assertEquals("1. 2. - 3. 1. 1. - -", labels(c));
        assertEquals(ListAttributes.leftIndent(0), StyleConstants.getLeftIndent(paragraphAt(c, "more")), 0.01);
        assertEquals(0f, StyleConstants.getFirstLineIndent(paragraphAt(c, "more")));
    }
}
