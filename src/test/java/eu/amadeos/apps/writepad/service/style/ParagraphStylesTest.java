package eu.amadeos.apps.writepad.service.style;

import eu.amadeos.apps.writepad.service.format.Documents;
import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import org.junit.jupiter.api.Test;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParagraphStylesTest {
    private static StyledDocument document(String text) throws BadLocationException {
        StyledDocument doc = Documents.newDocument();
        doc.insertString(0, text, null);
        return doc;
    }

    private static Element paragraph(StyledDocument doc, int index) {
        return doc.getDefaultRootElement().getElement(index);
    }

    private static AttributeSet charAt(StyledDocument doc, String text) throws BadLocationException {
        int at = doc.getText(0, doc.getLength()).indexOf(text);
        assertTrue(at >= 0, text);
        return doc.getCharacterElement(at).getAttributes();
    }

    @Test
    void aNewDocumentHasAllStylesAndIsNormal() throws BadLocationException {
        StyledDocument doc = document("one\ntwo");
        for (ParagraphStyle s : ParagraphStyle.values()) assertNotNull(doc.getStyle(s.swingName()), s.name());
        assertEquals(ParagraphStyle.NORMAL, ParagraphStyles.of(paragraph(doc, 0)));
        assertEquals(ParagraphStyle.NORMAL, ParagraphStyles.of(paragraph(doc, 1)), "a new paragraph keeps the style");
        assertEquals(Documents.DEFAULT_SIZE, StyleConstants.getFontSize(charAt(doc, "two")));
    }

    @Test
    void namesFromOtherProgramsMapToTheBuiltInStyles() {
        assertEquals(Optional.of(ParagraphStyle.HEADING_1), ParagraphStyle.forName("Heading_20_1"));
        assertEquals(Optional.of(ParagraphStyle.HEADING_2), ParagraphStyle.forName("heading 2"));
        assertEquals(Optional.of(ParagraphStyle.HEADING_6), ParagraphStyle.forName("Heading6"));
        assertEquals(Optional.of(ParagraphStyle.QUOTE), ParagraphStyle.forName("Quotations"));
        assertEquals(Optional.of(ParagraphStyle.QUOTE), ParagraphStyle.forName("Block Quotation"));
        assertEquals(Optional.of(ParagraphStyle.CODE), ParagraphStyle.forName("Preformatted_20_Text"));
        assertEquals(Optional.of(ParagraphStyle.NORMAL), ParagraphStyle.forName("Text_20_body"));
        assertEquals(Optional.of(ParagraphStyle.NORMAL), ParagraphStyle.forName("Standard"));
        assertEquals(Optional.empty(), ParagraphStyle.forName("Heading 7"));
        assertEquals(Optional.empty(), ParagraphStyle.forName("Caption"));
        assertEquals(ParagraphStyle.NORMAL, ParagraphStyle.HEADING_3.next());
        assertEquals(ParagraphStyle.QUOTE, ParagraphStyle.QUOTE.next());
    }

    @Test
    void aStyleBringsItsLook() throws BadLocationException {
        StyledDocument doc = document("Chapter\nText");
        ParagraphStyles.apply(doc, List.of(paragraph(doc, 0)), ParagraphStyle.HEADING_1);
        assertEquals(ParagraphStyle.HEADING_1, ParagraphStyles.of(paragraph(doc, 0)));
        AttributeSet heading = charAt(doc, "Chapter");
        assertEquals(18, StyleConstants.getFontSize(heading));
        assertTrue(StyleConstants.isBold(heading));
        assertEquals(18f, StyleConstants.getSpaceAbove(paragraph(doc, 0).getAttributes()));
        assertEquals(ParagraphStyle.NORMAL, ParagraphStyles.of(paragraph(doc, 1)));
        assertFalse(StyleConstants.isBold(charAt(doc, "Text")));
    }

    // --8<-- [start:apply]
    @Test
    void formattingOfTheWholeParagraphGoesAWordKeepsItsOwn() throws BadLocationException {
        StyledDocument doc = document("A big title with one word");
        SimpleAttributeSet big = new SimpleAttributeSet();
        StyleConstants.setFontSize(big, 20);
        StyleConstants.setFontFamily(big, "Monospaced");
        doc.setCharacterAttributes(0, doc.getLength() + 1, big, false);
        SimpleAttributeSet italic = new SimpleAttributeSet();
        StyleConstants.setItalic(italic, true);
        doc.setCharacterAttributes(doc.getText(0, doc.getLength()).indexOf("word"), 4, italic, false);
        SimpleAttributeSet centred = new SimpleAttributeSet();
        StyleConstants.setAlignment(centred, StyleConstants.ALIGN_CENTER);
        doc.setParagraphAttributes(0, 1, centred, false);

        ParagraphStyles.apply(doc, List.of(paragraph(doc, 0)), ParagraphStyle.HEADING_2);

        assertEquals(15, StyleConstants.getFontSize(charAt(doc, "big")), "the size set on everything goes");
        assertEquals("SansSerif", StyleConstants.getFontFamily(charAt(doc, "word")));
        assertTrue(StyleConstants.isItalic(charAt(doc, "word")), "an italic word stays italic");
        assertFalse(StyleConstants.isItalic(charAt(doc, "big")));
        assertEquals(StyleConstants.ALIGN_LEFT, StyleConstants.getAlignment(paragraph(doc, 0).getAttributes()));
    }
    // --8<-- [end:apply]

    @Test
    void clearingDirectFormattingGoesBackToTheStyle() throws BadLocationException {
        StyledDocument doc = document("Some bold text");
        ParagraphStyles.apply(doc, List.of(paragraph(doc, 0)), ParagraphStyle.QUOTE);
        SimpleAttributeSet bold = new SimpleAttributeSet();
        StyleConstants.setBold(bold, true);
        StyleConstants.setForeground(bold, Color.RED);
        doc.setCharacterAttributes(5, 4, bold, false);
        SimpleAttributeSet right = new SimpleAttributeSet();
        StyleConstants.setAlignment(right, StyleConstants.ALIGN_RIGHT);
        doc.setParagraphAttributes(0, 1, right, false);

        ParagraphStyles.clearDirect(doc, 5, 9);

        AttributeSet word = charAt(doc, "bold");
        assertFalse(StyleConstants.isBold(word));
        assertTrue(StyleConstants.isItalic(word), "the quote's italics");
        assertEquals(StyleConstants.ALIGN_LEFT, StyleConstants.getAlignment(paragraph(doc, 0).getAttributes()));
        assertEquals(ParagraphStyle.QUOTE, ParagraphStyles.of(paragraph(doc, 0)));
    }

    @Test
    void aNewDefinitionChangesEveryParagraphInTheStyle() throws BadLocationException {
        StyledDocument doc = document("One\nbody\nTwo");
        ParagraphStyles.apply(doc, List.of(paragraph(doc, 0), paragraph(doc, 2)), ParagraphStyle.HEADING_1);
        SimpleAttributeSet own = new SimpleAttributeSet();
        StyleConstants.setFontSize(own, 30);
        doc.setCharacterAttributes(doc.getText(0, doc.getLength()).indexOf("Two"), 3, own, false);

        SimpleAttributeSet bigger = ParagraphStyles.definition(doc, ParagraphStyle.HEADING_1);
        StyleConstants.setFontSize(bigger, 22);
        StyleConstants.setAlignment(bigger, StyleConstants.ALIGN_CENTER);
        ParagraphStyles.define(doc, ParagraphStyle.HEADING_1, bigger);

        assertEquals(22, StyleConstants.getFontSize(charAt(doc, "One")));
        assertEquals(StyleConstants.ALIGN_CENTER, StyleConstants.getAlignment(paragraph(doc, 2).getAttributes()));
        assertEquals(30, StyleConstants.getFontSize(charAt(doc, "Two")), "a size set by hand stays");
        assertEquals(Documents.DEFAULT_SIZE, StyleConstants.getFontSize(charAt(doc, "body")));
    }

    @Test
    void aStyleChangeCanBeUndone() throws BadLocationException {
        StyledDocument doc = document("Title");
        ParagraphStyles.apply(doc, List.of(paragraph(doc, 0)), ParagraphStyle.TITLE);
        SimpleAttributeSet red = ParagraphStyles.definition(doc, ParagraphStyle.TITLE);
        StyleConstants.setForeground(red, Color.RED);
        StyleEdit edit = StyleEdit.define(doc, Map.of(ParagraphStyle.TITLE, red));
        assertNotNull(edit);
        assertEquals(Color.RED, StyleConstants.getForeground(charAt(doc, "Title")));
        edit.undo();
        assertEquals(new Color(0x1F, 0x4E, 0x79), StyleConstants.getForeground(charAt(doc, "Title")));
        edit.redo();
        assertEquals(Color.RED, StyleConstants.getForeground(charAt(doc, "Title")));
        assertNull(StyleEdit.define(doc, Map.of(ParagraphStyle.TITLE, red)), "nothing changed");
    }

    @Test
    void blackTextHasNoColourOfItsOwn() throws BadLocationException {
        StyledDocument doc = document("plain");
        assertNull(charAt(doc, "plain").getAttribute(StyleConstants.Foreground), "the automatic colour");
        assertFalse(ParagraphStyles.definition(doc, ParagraphStyle.NORMAL).isDefined(StyleConstants.Foreground));
    }

    @Test
    void adoptingStylesKeepsTheLook() throws BadLocationException {
        StyledDocument doc = new WritepadDocument();
        SimpleAttributeSet heading = new SimpleAttributeSet();
        StyleConstants.setFontFamily(heading, "Liberation Sans");
        StyleConstants.setFontSize(heading, 16);
        StyleConstants.setBold(heading, true);
        SimpleAttributeSet body = new SimpleAttributeSet();
        StyleConstants.setFontFamily(body, "Liberation Serif");
        StyleConstants.setFontSize(body, 12);
        doc.insertString(0, "Heading\n", heading);
        doc.insertString(doc.getLength(), "Body with ", body);
        SimpleAttributeSet bodyItalic = new SimpleAttributeSet(body);
        StyleConstants.setItalic(bodyItalic, true);
        doc.insertString(doc.getLength(), "italics", bodyItalic);
        SimpleAttributeSet spaced = new SimpleAttributeSet();
        StyleConstants.setSpaceAbove(spaced, 12);
        doc.setParagraphAttributes(0, 1, spaced, false);

        SimpleAttributeSet headingDefinition = new SimpleAttributeSet(heading);
        StyleConstants.setSpaceAbove(headingDefinition, 12);
        ParagraphStyles.adopt(doc, List.of(new ParagraphStyles.Assignment(ParagraphStyles.position(doc, 0),
                ParagraphStyle.HEADING_1)), Map.of(ParagraphStyle.HEADING_1, headingDefinition,
                ParagraphStyle.NORMAL, body));

        assertEquals(ParagraphStyle.HEADING_1, ParagraphStyles.of(paragraph(doc, 0)));
        assertEquals(ParagraphStyle.NORMAL, ParagraphStyles.of(paragraph(doc, 1)));
        AttributeSet h = charAt(doc, "Heading");
        assertEquals("Liberation Sans", StyleConstants.getFontFamily(h));
        assertEquals(16, StyleConstants.getFontSize(h));
        assertFalse(h.isDefined(StyleConstants.FontSize), "the size comes from the style");
        assertEquals(12f, StyleConstants.getSpaceAbove(paragraph(doc, 0).getAttributes()));
        assertFalse(StyleConstants.isItalic(charAt(doc, "Body")));
        assertTrue(StyleConstants.isItalic(charAt(doc, "italics")));
        assertTrue(charAt(doc, "italics").isDefined(StyleConstants.Italic));
        assertEquals("Liberation Serif", StyleConstants.getFontFamily(charAt(doc, "Body")));
        assertEquals(16, StyleConstants.getFontSize(ParagraphStyles.definition(doc, ParagraphStyle.HEADING_1)));
    }
}
