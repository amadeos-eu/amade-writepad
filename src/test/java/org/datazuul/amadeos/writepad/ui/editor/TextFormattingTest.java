package org.datazuul.amadeos.writepad.ui.editor;

import org.datazuul.amadeos.writepad.OnEdt;
import org.datazuul.amadeos.writepad.domain.DocumentSettings;
import org.datazuul.amadeos.writepad.service.layout.PageLayout;
import org.junit.jupiter.api.Test;

import javax.swing.text.BadLocationException;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;

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
