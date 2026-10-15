package org.datazuul.amadeos.writepad.service.format.rtf;

import org.datazuul.amadeos.writepad.domain.DocumentSettings;
import org.datazuul.amadeos.writepad.domain.Margins;
import org.datazuul.amadeos.writepad.domain.Orientation;
import org.datazuul.amadeos.writepad.domain.PaperSize;
import org.datazuul.amadeos.writepad.domain.Units;
import org.datazuul.amadeos.writepad.service.format.DocumentContent;
import org.datazuul.amadeos.writepad.service.format.Documents;
import org.datazuul.amadeos.writepad.service.list.ListAttributes;
import org.datazuul.amadeos.writepad.service.list.ListFormat;
import org.datazuul.amadeos.writepad.service.list.ListNumbering;
import org.junit.jupiter.api.Test;

import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtfFormatTest {
    private final RtfFormat format = new RtfFormat();

    private DocumentContent read(String rtf) throws IOException {
        return format.read(rtf.getBytes(StandardCharsets.ISO_8859_1));
    }

    private DocumentContent fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/rtf/" + name)) {
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

    @Test
    void readsBasicFormatting() throws IOException {
        DocumentContent c = read("{\\rtf1\\ansi{\\fonttbl{\\f0 Arial;}{\\f1 Courier New;}}"
                + "{\\colortbl;\\red255\\green0\\blue0;}"
                + "\\f0\\fs20 plain {\\b bold} {\\i\\ul it} {\\strike gone} {\\cf1 red} {\\f1\\fs36 big}"
                + " x{\\super 2} H{\\sub 2}O}");
        assertEquals("plain bold it gone red big x2 H2O", text(c));
        assertEquals("Arial", StyleConstants.getFontFamily(charAt(c, "plain")));
        assertEquals(10, StyleConstants.getFontSize(charAt(c, "plain")));
        assertTrue(StyleConstants.isBold(charAt(c, "bold")));
        assertFalse(StyleConstants.isBold(charAt(c, " it")));
        assertTrue(StyleConstants.isItalic(charAt(c, "it")) && StyleConstants.isUnderline(charAt(c, "it")));
        assertTrue(StyleConstants.isStrikeThrough(charAt(c, "gone")));
        assertEquals(Color.RED, StyleConstants.getForeground(charAt(c, "red")));
        assertEquals("Courier New", StyleConstants.getFontFamily(charAt(c, "big")));
        assertEquals(18, StyleConstants.getFontSize(charAt(c, "big")));
        assertTrue(StyleConstants.isSuperscript(charAt(c, "2 H")));
        assertTrue(StyleConstants.isSubscript(charAt(c, "2O")));
    }

    @Test
    void readsParagraphs() throws IOException {
        DocumentContent c = read("{\\rtf1 \\pard\\qc\\sb240\\sa120 Centre\\par"
                + "\\pard\\qj\\li720\\ri360\\fi-360 Justified\\par \\pard Last}");
        assertEquals("Centre\nJustified\nLast", text(c));
        AttributeSet centre = paragraphAt(c, "Centre");
        assertEquals(StyleConstants.ALIGN_CENTER, StyleConstants.getAlignment(centre));
        assertEquals(12f, StyleConstants.getSpaceAbove(centre));
        assertEquals(6f, StyleConstants.getSpaceBelow(centre));
        AttributeSet justified = paragraphAt(c, "Justified");
        assertEquals(StyleConstants.ALIGN_JUSTIFIED, StyleConstants.getAlignment(justified));
        assertEquals(36f, StyleConstants.getLeftIndent(justified));
        assertEquals(18f, StyleConstants.getRightIndent(justified));
        assertEquals(-18f, StyleConstants.getFirstLineIndent(justified));
        assertEquals(StyleConstants.ALIGN_LEFT, StyleConstants.getAlignment(paragraphAt(c, "Last")));
    }

    @Test
    void lastParagraphKeepsItsFormattingAndTrailingParIsDropped() throws IOException {
        assertEquals(StyleConstants.ALIGN_RIGHT,
                StyleConstants.getAlignment(paragraphAt(read("{\\rtf1 \\pard\\qr Right}"), "Right")));
        DocumentContent c = read("{\\rtf1 \\pard\\qc One\\par}");
        assertEquals("One", text(c));
        assertEquals(StyleConstants.ALIGN_CENTER, StyleConstants.getAlignment(paragraphAt(c, "One")));
    }

    @Test
    void decodesCharacterSets() throws IOException {
        DocumentContent c = read("{\\rtf1\\ansi\\ansicpg1252{\\fonttbl{\\f0 A;}{\\f1\\fcharset204 B;}"
                + "{\\f2\\fcharset128 C;}}\\f0 M\\'fcnchen \\f1\\'cf\\'f0\\'e8\\'e2\\'e5\\'f2 \\f2\\'93\\'8c\\'8b\\'9e"
                + " \\f0\\uc1\\u8364?\\u-10179?\\u-8704? \\uc2\\u8220\\'93\\'93x}");
        assertEquals("München Привет 東京 €😀 “x", text(c));
    }

    @Test
    void skipsWhatIsNotText() throws IOException {
        DocumentContent c = read("{\\rtf1{\\fonttbl{\\f0 A{\\*\\panose 0}{\\*\\falt B};}}{\\stylesheet{\\s1 Heading;}}"
                + "{\\info{\\title T}{\\author X}}{\\header Head}{\\footer Foot}"
                + "Before{\\*\\unknowndestination junk}{\\pict\\pngblip 89504e47}"
                + "{\\field{\\*\\fldinst HYPERLINK \"x\"}{\\fldrslt Link}} after{\\footnote note}\\bin3 abcx}");
        assertEquals("BeforeLink afterx", text(c));
        assertEquals("A", StyleConstants.getFontFamily(charAt(c, "Before")));
    }

    @Test
    void controlSymbolsAndSpecialCharacters() throws IOException {
        DocumentContent c = read("{\\rtf1 a\\{b\\}c\\\\d\\tab e\\~f\\-g\\_h \\emdash\\endash\\bullet"
                + "\\lquote\\rquote\\ldblquote\\rdblquote}");
        assertEquals("a{b}c\\d\te fg‑h —–•‘’“”", text(c));
    }

    @Test
    void readsPageSetup() throws IOException {
        DocumentSettings portrait = read("{\\rtf1\\paperw11906\\paperh16838\\margl1134\\margr1134"
                + "\\margt1417\\margb1417 x}").settings();
        assertEquals(PaperSize.A4, portrait.paper());
        assertEquals(Orientation.PORTRAIT, portrait.orientation());
        assertEquals(Units.mmToPoints(20), portrait.margins().left(), 0.1);
        assertEquals(Units.mmToPoints(25), portrait.margins().top(), 0.1);

        DocumentSettings landscape = read("{\\rtf1\\paperw11906\\paperh8391\\landscape x}").settings();
        assertEquals(PaperSize.A5, landscape.paper());
        assertEquals(Orientation.LANDSCAPE, landscape.orientation());

        // WordPad writes no paper size: our default page, not RTF's US Letter.
        assertEquals(DocumentSettings.DEFAULT, read("{\\rtf1 x}").settings());
        // Margins that leave no room are ignored.
        assertEquals(Margins.DEFAULT, read("{\\rtf1\\paperw11906\\paperh16838\\margl9000\\margr9000 x}")
                .settings().margins());
    }

    @Test
    void rejectsWhatIsNotRtfAndSurvivesBrokenFiles() throws IOException {
        assertThrows(IOException.class, () -> read("Hello"));
        assertEquals("cut off", text(read("{\\rtf1 {\\b cut off")));
        assertEquals("x", text(read("{\\rtf1 x}}}} trailing")).substring(0, 1));
        assertEquals("", text(read("{\\rtf1 \\")));
    }

    @Test
    void readsWordPadFile() throws IOException {
        DocumentContent c = fixture("wordpad.rtf");
        assertEquals("Überschrift\nNormaler Text mit kursiv und rot. Grüße aus München.\nPunkt eins\nŽluva",
                text(c));
        AttributeSet title = charAt(c, "Überschrift");
        assertEquals("Calibri", StyleConstants.getFontFamily(title));
        assertEquals(16, StyleConstants.getFontSize(title));
        assertTrue(StyleConstants.isBold(title));
        assertEquals(StyleConstants.ALIGN_CENTER, StyleConstants.getAlignment(paragraphAt(c, "Überschrift")));
        assertEquals(10f, StyleConstants.getSpaceBelow(paragraphAt(c, "Normaler")));
        assertEquals(0.15f, StyleConstants.getLineSpacing(paragraphAt(c, "Normaler")), 0.001);
        assertFalse(StyleConstants.isBold(charAt(c, "Normaler")));
        assertEquals(11, StyleConstants.getFontSize(charAt(c, "Normaler")));
        assertTrue(StyleConstants.isItalic(charAt(c, "kursiv")));
        assertEquals(Color.RED, StyleConstants.getForeground(charAt(c, "rot")));
        assertEquals(36f, StyleConstants.getLeftIndent(paragraphAt(c, "Punkt")));
        assertEquals(DocumentSettings.DEFAULT, c.settings());
    }

    /** Written by LibreOffice 24.2 (soffice --convert-to rtf) from a flat ODT. */
    @Test
    void readsLibreOfficeFile() throws IOException {
        DocumentContent c = fixture("libreoffice.rtf");
        assertEquals("Titel\nEin Absatz mit kursivem und blauem fetten Text. Euro: € und „Zitat“. Grüße, weg, Привет.",
                text(c));
        AttributeSet title = charAt(c, "Titel");
        assertEquals("Liberation Sans", StyleConstants.getFontFamily(title));
        assertEquals(18, StyleConstants.getFontSize(title));
        assertTrue(StyleConstants.isBold(title));
        AttributeSet titleParagraph = paragraphAt(c, "Titel");
        assertEquals(StyleConstants.ALIGN_CENTER, StyleConstants.getAlignment(titleParagraph));
        assertEquals(12f, StyleConstants.getSpaceAbove(titleParagraph));
        assertEquals(6f, StyleConstants.getSpaceBelow(titleParagraph));
        AttributeSet body = charAt(c, "Ein Absatz");
        assertEquals("Liberation Serif", StyleConstants.getFontFamily(body));
        assertEquals(12, StyleConstants.getFontSize(body));
        assertFalse(StyleConstants.isBold(body));
        assertEquals(null, body.getAttribute(StyleConstants.Foreground), "\\cf0 is the automatic colour");
        assertTrue(StyleConstants.isItalic(charAt(c, "kursivem")));
        assertEquals(Color.BLUE, StyleConstants.getForeground(charAt(c, "blauem")));
        assertTrue(StyleConstants.isBold(charAt(c, "blauem")));
        assertTrue(StyleConstants.isStrikeThrough(charAt(c, "weg")) && StyleConstants.isUnderline(charAt(c, "weg")));
        AttributeSet bodyParagraph = paragraphAt(c, "Ein Absatz");
        assertEquals(StyleConstants.ALIGN_JUSTIFIED, StyleConstants.getAlignment(bodyParagraph));
        assertEquals(36f, StyleConstants.getLeftIndent(bodyParagraph));
        assertEquals(-18f, StyleConstants.getFirstLineIndent(bodyParagraph));
        assertEquals(PaperSize.A4, c.settings().paper());
        assertEquals(Orientation.PORTRAIT, c.settings().orientation());
        assertEquals(Units.mmToPoints(20), c.settings().margins().left(), 0.1);
        assertEquals(Units.mmToPoints(20), c.settings().margins().top(), 0.1);
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
        doc.insertString(0, "Title\n", bold);
        doc.insertString(doc.getLength(), "Größe {braces} \\ back\tslash € Привет 😀 ", null);
        doc.insertString(doc.getLength(), "special", special);
        doc.insertString(doc.getLength(), "\n\nAfter an empty paragraph", null);
        SimpleAttributeSet centre = new SimpleAttributeSet();
        StyleConstants.setAlignment(centre, StyleConstants.ALIGN_CENTER);
        StyleConstants.setSpaceBelow(centre, 6);
        StyleConstants.setLineSpacing(centre, 0.5f);
        doc.setParagraphAttributes(0, 1, centre, false);
        SimpleAttributeSet indented = new SimpleAttributeSet();
        StyleConstants.setLeftIndent(indented, 36);
        StyleConstants.setFirstLineIndent(indented, -18);
        StyleConstants.setAlignment(indented, StyleConstants.ALIGN_JUSTIFIED);
        doc.setParagraphAttributes(8, 1, indented, false);
        DocumentSettings settings = new DocumentSettings(PaperSize.A5, Orientation.LANDSCAPE,
                Margins.ofMm(10, 15, 12, 18));

        byte[] rtf = format.write(new DocumentContent(doc, settings));
        String written = new String(rtf, StandardCharsets.US_ASCII);
        assertTrue(written.startsWith("{\\rtf1\\ansi"), written);
        DocumentContent back = format.read(rtf);

        assertEquals(text(new DocumentContent(doc, settings)), text(back));
        AttributeSet title = charAt(back, "Title");
        assertEquals("Liberation Sans", StyleConstants.getFontFamily(title));
        assertEquals(20, StyleConstants.getFontSize(title));
        assertTrue(StyleConstants.isBold(title));
        assertEquals(new Color(0x1F3A5F), StyleConstants.getForeground(title));
        AttributeSet titleParagraph = paragraphAt(back, "Title");
        assertEquals(StyleConstants.ALIGN_CENTER, StyleConstants.getAlignment(titleParagraph));
        assertEquals(6f, StyleConstants.getSpaceBelow(titleParagraph));
        assertEquals(0.5f, StyleConstants.getLineSpacing(titleParagraph), 0.01);
        AttributeSet plain = charAt(back, "Größe");
        assertEquals(Documents.DEFAULT_FAMILY, StyleConstants.getFontFamily(plain));
        assertEquals(Documents.DEFAULT_SIZE, StyleConstants.getFontSize(plain));
        assertFalse(StyleConstants.isBold(plain));
        AttributeSet s = charAt(back, "special");
        assertTrue(StyleConstants.isItalic(s) && StyleConstants.isUnderline(s) && StyleConstants.isStrikeThrough(s)
                && StyleConstants.isSuperscript(s));
        AttributeSet second = paragraphAt(back, "Größe");
        assertEquals(36f, StyleConstants.getLeftIndent(second));
        assertEquals(-18f, StyleConstants.getFirstLineIndent(second));
        assertEquals(StyleConstants.ALIGN_JUSTIFIED, StyleConstants.getAlignment(second));
        assertEquals(StyleConstants.ALIGN_LEFT, StyleConstants.getAlignment(paragraphAt(back, "After")));
        assertEquals(settings.paper(), back.settings().paper());
        assertEquals(settings.orientation(), back.settings().orientation());
        assertEquals(settings.margins().left(), back.settings().margins().left(), 0.1);
        assertEquals(settings.margins().bottom(), back.settings().margins().bottom(), 0.1);
    }

    @Test
    void writesPureAscii() throws IOException, BadLocationException {
        StyledDocument doc = Documents.newDocument();
        doc.insertString(0, "äöü€Ж😀", null);
        byte[] rtf = format.write(new DocumentContent(doc, DocumentSettings.DEFAULT));
        for (byte b : rtf) assertTrue(b >= 0, "non-ASCII byte in output");
        String written = new String(rtf, StandardCharsets.US_ASCII);
        assertTrue(written.contains("\\'e4\\'f6\\'fc\\'80"), written);
        assertTrue(written.contains("\\u1046?"), written);
        assertTrue(written.contains("\\u-10179?\\u-8704?"), written);
    }

    @Test
    void readsTabStops() throws IOException {
        DocumentContent c = read("{\\rtf1 \\pard\\tx1134\\tqc\\tx2835\\tqr\\tldot\\tx4536\\tqdec\\tx5670\\tb6000 One\\par"
                + "\\pard Two{\\tx100 ignored in a group that ends before the paragraph}\\par\\pard\\tx720 Three}");
        TabSet one = StyleConstants.getTabSet(paragraphAt(c, "One"));
        assertEquals(5, one.getTabCount());
        assertEquals(56.7f, one.getTab(0).getPosition(), 0.01f);
        assertEquals(TabStop.ALIGN_LEFT, one.getTab(0).getAlignment());
        assertEquals(TabStop.ALIGN_CENTER, one.getTab(1).getAlignment());
        assertEquals(TabStop.ALIGN_RIGHT, one.getTab(2).getAlignment());
        assertEquals(TabStop.LEAD_DOTS, one.getTab(2).getLeader());
        assertEquals(TabStop.LEAD_NONE, one.getTab(3).getLeader());
        assertEquals(TabStop.ALIGN_DECIMAL, one.getTab(3).getAlignment());
        assertEquals(300f, one.getTab(4).getPosition(), 0.01f, "bar tab");
        assertEquals(null, StyleConstants.getTabSet(paragraphAt(c, "Two")), "\\pard clears the stops");
        assertEquals(36f, StyleConstants.getTabSet(paragraphAt(c, "Three")).getTab(0).getPosition());
    }

    @Test
    void writesTabStopsAndIndentsBack() throws IOException, BadLocationException {
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
        byte[] rtf = format.write(new DocumentContent(doc, DocumentSettings.DEFAULT));
        assertTrue(new String(rtf, StandardCharsets.US_ASCII).contains(
                "\\li1134\\fi-567\\tx1134\\tqc\\tx2835\\tqr\\tldot\\tx6000\\tqdec\\tlhyph\\tx8000"));
        TabSet back = StyleConstants.getTabSet(paragraphAt(format.read(rtf), "a"));
        assertEquals(4, back.getTabCount());
        for (int i = 0; i < 4; i++) {
            TabStop expected = StyleConstants.getTabSet(p).getTab(i);
            assertEquals(expected.getPosition(), back.getTab(i).getPosition(), 0.05f);
            assertEquals(expected.getAlignment(), back.getTab(i).getAlignment());
            assertEquals(expected.getLeader(), back.getTab(i).getLeader());
        }
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

    /** Written by LibreOffice 24.2 from a flat ODT with a bulleted, a nested numbered and a Roman list. */
    @Test
    void readsLibreOfficeLists() throws IOException {
        DocumentContent c = fixture("libreoffice-lists.rtf");
        assertEquals("Intro\nApfel\nBirne\nErstens\nUnterpunkt\nNoch einer\nZweitens\nKapitel\nKapitel", text(c),
                "the labels are not text");
        assertEquals("- • • 1. a. b. 2. I. II.", labels(c));
        AttributeSet sub = paragraphAt(c, "Unterpunkt");
        assertEquals(1, ListAttributes.level(sub));
        assertEquals(54f, StyleConstants.getLeftIndent(sub), 0.1f);
        assertEquals(-18f, StyleConstants.getFirstLineIndent(sub), 0.1f);
    }

    @Test
    void readsWordPadLists() throws IOException {
        assertEquals("- - • -", labels(fixture("wordpad.rtf")), "{\\*\\pn\\pnlvlblt} bullets");
        DocumentContent numbered = fixture("wordpad-numbered.rtf");
        assertEquals("Vorher\nEins\nZwei\nDrei\nDazwischen\nNeu", text(numbered));
        assertEquals("- 1. 2. 3. - I.", labels(numbered), "a list after a paragraph in between starts again");
    }

    @Test
    void writesListsThatReadBack() throws IOException, BadLocationException {
        StyledDocument doc = Documents.newDocument();
        doc.insertString(0, "a\nb\nc\nd\ne\nf", null);
        Element root = doc.getDefaultRootElement();
        Object[][] items = {{1, 0, ListFormat.BULLET}, {1, 1, ListFormat.BULLET}, {0, 0, ListFormat.BULLET},
                {2, 0, ListFormat.UPPER_ROMAN}, {2, 1, ListFormat.LOWER_LETTER}, {2, 0, ListFormat.UPPER_ROMAN}};
        for (int i = 0; i < items.length; i++) {
            SimpleAttributeSet a = new SimpleAttributeSet();
            ListAttributes.set(a, (Integer) items[i][0], (Integer) items[i][1], (ListFormat) items[i][2]);
            doc.setParagraphAttributes(root.getElement(i).getStartOffset(), 1, a, false);
        }
        byte[] rtf = format.write(new DocumentContent(doc, DocumentSettings.DEFAULT));
        String written = new String(rtf, StandardCharsets.US_ASCII);
        assertTrue(written.contains("{\\*\\listtable"), written);
        assertTrue(written.contains("{\\listtext\\pard\\plain II.\\tab}\\pard"), written);
        assertTrue(written.contains("\\ls2\\ilvl1"), written);
        DocumentContent back = format.read(rtf);
        assertEquals("a\nb\nc\nd\ne\nf", text(back), "list text written for old programs is skipped again");
        assertEquals("• ◦ - I. a. II.", labels(back));
    }

    @Test
    void namesTheFontFamilyForProgramsWithoutTheFont() {
        assertEquals("\\froman", RtfWriter.familyClass("Serif"));
        assertEquals("\\fswiss", RtfWriter.familyClass("SansSerif"));
        assertEquals("\\fmodern", RtfWriter.familyClass("Monospaced"));
        assertEquals("\\fnil", RtfWriter.familyClass("Some Font"));
    }
}
