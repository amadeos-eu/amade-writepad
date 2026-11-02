package eu.amadeos.apps.writepad.service.format;

import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.service.style.ParagraphStyle;
import eu.amadeos.apps.writepad.service.style.ParagraphStyles;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A document with every paragraph style, and the checks every format's round trip makes. */
public final class StyleSamples {
    /** One paragraph per style, named after it; Heading 1's has an italic word. */
    private static final List<ParagraphStyle> ORDER = List.of(ParagraphStyle.TITLE, ParagraphStyle.SUBTITLE,
            ParagraphStyle.HEADING_1, ParagraphStyle.NORMAL, ParagraphStyle.HEADING_2, ParagraphStyle.HEADING_3,
            ParagraphStyle.HEADING_4, ParagraphStyle.HEADING_5, ParagraphStyle.HEADING_6, ParagraphStyle.QUOTE,
            ParagraphStyle.CODE);

    private StyleSamples() {
    }

    private static String text(ParagraphStyle style) {
        return style == ParagraphStyle.HEADING_1 ? "Heading one with emphasis" : "Text in " + style.name();
    }

    /**
     * Every style once; Heading 1 redefined (20 pt, centred), with "emphasis" italic by hand; the Normal
     * paragraph right-aligned by hand.
     */
    public static DocumentContent document() {
        StyledDocument doc = Documents.newDocument();
        try {
            StringBuilder all = new StringBuilder();
            for (ParagraphStyle s : ORDER) all.append(all.isEmpty() ? "" : "\n").append(text(s));
            doc.insertString(0, all.toString(), null);
            Element root = doc.getDefaultRootElement();
            for (int i = 0; i < ORDER.size(); i++) {
                ParagraphStyles.apply(doc, List.of(root.getElement(i)), ORDER.get(i));
            }
            SimpleAttributeSet heading = ParagraphStyles.definition(doc, ParagraphStyle.HEADING_1);
            StyleConstants.setFontSize(heading, 20);
            StyleConstants.setAlignment(heading, StyleConstants.ALIGN_CENTER);
            ParagraphStyles.define(doc, ParagraphStyle.HEADING_1, heading);
            SimpleAttributeSet italic = new SimpleAttributeSet();
            StyleConstants.setItalic(italic, true);
            doc.setCharacterAttributes(doc.getText(0, doc.getLength()).indexOf("emphasis"), 8, italic, false);
            SimpleAttributeSet right = new SimpleAttributeSet();
            StyleConstants.setAlignment(right, StyleConstants.ALIGN_RIGHT);
            doc.setParagraphAttributes(doc.getText(0, doc.getLength()).indexOf(text(ParagraphStyle.NORMAL)), 1,
                    right, false);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        return new DocumentContent(doc, DocumentSettings.DEFAULT);
    }

    /** The styles came back, with Heading 1's new look and the formatting set by hand. */
    public static void check(DocumentContent content) {
        StyledDocument doc = content.document();
        String text;
        try {
            text = doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            throw new AssertionError(e);
        }
        for (ParagraphStyle s : ORDER) {
            int at = text.indexOf(text(s));
            assertTrue(at >= 0, text(s));
            assertEquals(s, ParagraphStyles.of(doc.getParagraphElement(at)), text(s));
        }
        AttributeSet heading = ParagraphStyles.definition(doc, ParagraphStyle.HEADING_1);
        assertEquals(20, StyleConstants.getFontSize(heading));
        assertEquals(StyleConstants.ALIGN_CENTER, StyleConstants.getAlignment(heading));
        assertTrue(StyleConstants.isBold(heading));
        AttributeSet emphasis = doc.getCharacterElement(text.indexOf("emphasis")).getAttributes();
        assertTrue(StyleConstants.isItalic(emphasis));
        assertEquals(20, StyleConstants.getFontSize(emphasis));
        assertFalse(StyleConstants.isItalic(doc.getCharacterElement(text.indexOf("Heading one")).getAttributes()));
        assertFalse(doc.getCharacterElement(text.indexOf("Heading one")).getAttributes()
                .isDefined(StyleConstants.FontSize), "the heading's size is the style's");
        int normal = text.indexOf(text(ParagraphStyle.NORMAL));
        assertEquals(StyleConstants.ALIGN_RIGHT, StyleConstants.getAlignment(doc.getParagraphElement(normal).getAttributes()));
        assertEquals(StyleConstants.ALIGN_LEFT, StyleConstants.getAlignment(
                ParagraphStyles.definition(doc, ParagraphStyle.NORMAL)));
        AttributeSet quote = doc.getCharacterElement(text.indexOf(text(ParagraphStyle.QUOTE))).getAttributes();
        assertTrue(StyleConstants.isItalic(quote));
        assertEquals(15, StyleConstants.getFontSize(
                doc.getCharacterElement(text.indexOf(text(ParagraphStyle.HEADING_2))).getAttributes()));
    }

    /** The paragraphs of the LibreOffice fixture (styles.*, made from HTML) have their styles. */
    public static void checkLibreOffice(DocumentContent content) {
        StyledDocument doc = content.document();
        String text;
        try {
            text = doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            throw new AssertionError(e);
        }
        String[][] expected = {{"Kapitel eins", "HEADING_1"}, {"Ein Absatz", "NORMAL"}, {"Abschnitt", "HEADING_2"},
                {"Unterabschnitt", "HEADING_3"}, {"Ein Zitat", "QUOTE"}, {"code()", "CODE"},
                {"Vierte Ebene", "HEADING_4"}};
        for (String[] e : expected) {
            int at = text.indexOf(e[0]);
            assertTrue(at >= 0, e[0]);
            assertEquals(ParagraphStyle.valueOf(e[1]), ParagraphStyles.of(doc.getParagraphElement(at)), e[0]);
        }
        AttributeSet chapter = doc.getCharacterElement(text.indexOf("Kapitel")).getAttributes();
        assertTrue(StyleConstants.isBold(chapter));
        assertEquals(24, StyleConstants.getFontSize(chapter));
        assertFalse(chapter.isDefined(StyleConstants.FontSize), "from the style");
        assertTrue(StyleConstants.isBold(doc.getCharacterElement(text.indexOf("fettem")).getAttributes()));
        assertFalse(StyleConstants.isBold(doc.getCharacterElement(text.indexOf("Ein Absatz")).getAttributes()));
        assertEquals(12, StyleConstants.getFontSize(doc.getCharacterElement(text.indexOf("Ein Absatz")).getAttributes()));
    }
}
