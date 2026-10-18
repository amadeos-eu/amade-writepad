package eu.amadeos.apps.writepad.ui.editor;

import eu.amadeos.apps.writepad.OnEdt;
import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.service.layout.PageLayout;
import org.junit.jupiter.api.Test;

import eu.amadeos.apps.writepad.service.list.ListFormat;
import eu.amadeos.apps.writepad.service.list.ListNumbering;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabStop;
import java.awt.Color;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFormattingTest {
    private PageEditor editor;
    private TextFormatting formatting;

    private void withText(String text, Runnable test) throws Exception {
        OnEdt.run(() -> {
            editor = new PageEditor(new PageLayout(DocumentSettings.DEFAULT, 1));
            formatting = new TextFormatting(editor);
            try {
                editor.getDocument().insertString(0, text, null);
            } catch (BadLocationException e) {
                throw new AssertionError(e);
            }
            test.run();
        });
    }

    private StyledDocument doc() {
        return editor.getStyledDocument();
    }

    private boolean boldAt(int offset) {
        return StyleConstants.isBold(doc().getCharacterElement(offset).getAttributes());
    }

    @Test
    void boldAppliesToTheSelectionAndToggles() throws Exception {
        withText("one two three", () -> {
            editor.select(4, 7);
            formatting.toggleBold();
            assertFalse(boldAt(2));
            assertTrue(boldAt(4) && boldAt(6));
            assertFalse(boldAt(8));
            assertTrue(formatting.state().bold());
            formatting.toggleBold();
            assertFalse(boldAt(5));
        });
    }

    @Test
    void withoutSelectionTheNextTypedTextIsFormatted() throws Exception {
        withText("plain", () -> {
            editor.setCaretPosition(5);
            formatting.toggleItalic();
            assertTrue(formatting.state().italic());
            editor.replaceSelection("!");
            assertTrue(StyleConstants.isItalic(doc().getCharacterElement(5).getAttributes()));
            assertFalse(StyleConstants.isItalic(doc().getCharacterElement(2).getAttributes()));
        });
    }

    @Test
    void stateFollowsTheFirstSelectedCharacter() throws Exception {
        withText("abc def", () -> {
            editor.select(0, 3);
            formatting.setFontFamily("Monospaced");
            formatting.setFontSize(20);
            formatting.setForeground(Color.BLUE);
            formatting.toggleUnderline();
            formatting.toggleStrikeThrough();
            editor.select(1, 6);
            TextFormatting.State state = formatting.state();
            assertEquals("Monospaced", state.family());
            assertEquals(20, state.size());
            assertEquals(Color.BLUE, state.foreground());
            assertTrue(state.underline());
            assertTrue(state.strikeThrough());
            editor.select(4, 6);
            assertEquals(12, formatting.state().size());
        });
    }

    @Test
    void indentsSpacingAndTabsApplyToAllSelectedParagraphs() throws Exception {
        withText("one\ntwo\nthree", () -> {
            editor.select(1, 6);
            formatting.setIndents(new TextFormatting.Indents(36, 18, 10));
            formatting.setSpacing(new TextFormatting.Spacing(6, 12, 1.5f));
            formatting.setTabs(List.of(new TabStop(200), new TabStop(100, TabStop.ALIGN_RIGHT, TabStop.LEAD_NONE),
                    new TabStop(100.001f)));
            editor.setCaretPosition(5);
            assertEquals(new TextFormatting.Indents(36, 18, 10), formatting.indents());
            assertEquals(-18f, StyleConstants.getFirstLineIndent(formatting.paragraphAttributes()));
            assertEquals(new TextFormatting.Spacing(6, 12, 1.5f), formatting.spacing());
            assertEquals(0.5f, StyleConstants.getLineSpacing(formatting.paragraphAttributes()));
            List<TabStop> tabs = formatting.tabs();
            assertEquals(2, tabs.size(), "stops at the same position merged");
            assertEquals(100f, tabs.get(0).getPosition(), 0.01f);
            assertEquals(200f, tabs.get(1).getPosition(), "sorted");
            editor.setCaretPosition(10);
            assertEquals(new TextFormatting.Indents(0, 0, 0), formatting.indents(), "third paragraph unchanged");
            assertEquals(List.of(), formatting.tabs());
        });
    }

    private String labels() {
        StringBuilder s = new StringBuilder();
        Element root = doc().getDefaultRootElement();
        for (int i = 0; i < root.getElementCount(); i++) {
            String label = ListNumbering.label(doc(), root.getElement(i));
            if (i > 0) s.append(' ');
            s.append(label == null ? "-" : label);
        }
        return s.toString();
    }

    private void select(String from, String to) {
        String text;
        try {
            text = doc().getText(0, doc().getLength());
        } catch (BadLocationException e) {
            throw new AssertionError(e);
        }
        editor.select(text.indexOf(from), text.indexOf(to) + to.length());
    }

    @Test
    void bulletsAndNumberingToggleOnTheSelection() throws Exception {
        withText("a\nb\nc\nd", () -> {
            select("b", "c");
            formatting.toggleBullets();
            assertEquals("- • • -", labels());
            AttributeSet b = doc().getParagraphElement(2).getAttributes();
            assertEquals(18f, StyleConstants.getLeftIndent(b));
            assertEquals(-18f, StyleConstants.getFirstLineIndent(b));
            assertEquals(Optional.of(ListFormat.BULLET), formatting.listFormat());
            formatting.toggleNumbering();
            assertEquals("- 1. 2. -", labels(), "bullets become numbers");
            formatting.toggleNumbering();
            assertEquals("- - - -", labels(), "all numbered: numbering off");
            assertEquals(0f, StyleConstants.getLeftIndent(doc().getParagraphElement(2).getAttributes()));
        });
    }

    @Test
    void newItemsJoinTheListNextToThem() throws Exception {
        withText("a\nb\nc", () -> {
            select("a", "b");
            formatting.toggleNumbering();
            select("c", "c");
            formatting.toggleNumbering();
            assertEquals("1. 2. 3.", labels());
            editor.setCaretPosition(4);
            formatting.toggleBullets();
            assertEquals("1. 2. •", labels(), "a bullet does not join a numbered list");
        });
    }

    @Test
    void levelsIndentAndChangeTheNumberingStyle() throws Exception {
        withText("a\nb\nc", () -> {
            select("a", "c");
            formatting.toggleNumbering();
            select("b", "b");
            assertTrue(formatting.changeLevel(1));
            assertEquals("1. a. 2.", labels());
            assertEquals(36f, StyleConstants.getLeftIndent(doc().getParagraphElement(2).getAttributes()));
            formatting.changeLevel(-5);
            assertEquals("1. 2. 3.", labels());
            editor.setCaretPosition(0);
            formatting.endListAtCaret(false);
            assertFalse(formatting.changeLevel(1), "not a list item");
        });
    }

    @Test
    void restartAndContinueNumbering() throws Exception {
        withText("a\nb\nc\nd", () -> {
            select("a", "d");
            formatting.toggleNumbering();
            editor.setCaretPosition(4);
            assertTrue(formatting.restartNumbering());
            assertEquals("1. 2. 1. 2.", labels());
            assertTrue(formatting.continueNumbering());
            assertEquals("1. 2. 3. 4.", labels());
            assertFalse(formatting.continueNumbering(), "no list before");
        });
    }

    @Test
    void endingAListKeepsOrResetsTheIndent() throws Exception {
        withText("a\nb", () -> {
            select("a", "b");
            formatting.toggleBullets();
            editor.setCaretPosition(0);
            formatting.endListAtCaret(true);
            AttributeSet a = doc().getParagraphElement(0).getAttributes();
            assertEquals("- •", labels());
            assertEquals(18f, StyleConstants.getLeftIndent(a), "Backspace keeps the text where it is");
            assertEquals(0f, StyleConstants.getFirstLineIndent(a));
            editor.setCaretPosition(2);
            formatting.endListAtCaret(false);
            assertEquals(0f, StyleConstants.getLeftIndent(doc().getParagraphElement(2).getAttributes()));
        });
    }

    @Test
    void alignmentAppliesToTheParagraph() throws Exception {
        withText("first\nsecond", () -> {
            editor.setCaretPosition(8);
            formatting.setAlignment(StyleConstants.ALIGN_CENTER);
            assertEquals(StyleConstants.ALIGN_CENTER, formatting.state().alignment());
            editor.setCaretPosition(1);
            assertEquals(StyleConstants.ALIGN_LEFT, formatting.state().alignment());
        });
    }
}
