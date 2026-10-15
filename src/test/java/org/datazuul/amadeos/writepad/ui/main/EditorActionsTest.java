package org.datazuul.amadeos.writepad.ui.main;

import org.datazuul.amadeos.writepad.OnEdt;
import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.writepad.bootstrap.WritepadModule;
import org.datazuul.amadeos.writepad.domain.DocumentSettings;
import org.datazuul.amadeos.writepad.repository.WritepadPreferences;
import org.datazuul.amadeos.writepad.service.layout.PageLayout;
import org.datazuul.amadeos.writepad.ui.editor.PageEditor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Action;
import javax.swing.JToggleButton;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyleConstants;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorActionsTest {
    @TempDir
    Path config;
    private PageEditor editor;
    private EditorActions actions;

    private void withEditor(String text, Runnable test) throws Exception {
        OnEdt.run(() -> {
            Messages msg = Messages.load(WritepadModule.class, WritepadModule.MESSAGES, Locale.ENGLISH);
            editor = new PageEditor(new PageLayout(DocumentSettings.DEFAULT, 1));
            MainPresenter presenter = new MainPresenter(null, new PageLayout(DocumentSettings.DEFAULT, 1), msg, null,
                    () -> 1, new WritepadPreferences(config.resolve("p.properties")));
            actions = new EditorActions(editor, presenter, msg);
            DefaultStyledDocument doc = new DefaultStyledDocument();
            editor.setStyledDocument(doc);
            actions.attach(doc);
            try {
                doc.insertString(0, text, null);
            } catch (BadLocationException e) {
                throw new AssertionError(e);
            }
            actions.update();
            test.run();
        });
    }

    private static void perform(Action action) {
        action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, ""));
    }

    private static boolean selected(Action action) {
        return Boolean.TRUE.equals(action.getValue(Action.SELECTED_KEY));
    }

    @Test
    void cutAndCopyNeedASelection() throws Exception {
        withEditor("hello world", () -> {
            editor.setCaretPosition(3);
            actions.update();
            assertFalse(actions.cut.isEnabled());
            assertFalse(actions.copy.isEnabled());
            assertTrue(actions.paste.isEnabled());
            editor.select(0, 5);
            actions.update();
            assertTrue(actions.cut.isEnabled());
            assertTrue(actions.copy.isEnabled());
        });
    }

    @Test
    void undoFollowsTheHistory() throws Exception {
        withEditor("abc", () -> {
            assertTrue(actions.undoAction.isEnabled());
            assertFalse(actions.redoAction.isEnabled());
            perform(actions.undoAction);
            assertEquals("", editor.getText());
            assertFalse(actions.undoAction.isEnabled());
            assertTrue(actions.redoAction.isEnabled());
            perform(actions.redoAction);
            assertEquals("abc", editor.getText());
        });
    }

    @Test
    void newDocumentStartsANewHistory() throws Exception {
        withEditor("abc", () -> {
            actions.attach(new DefaultStyledDocument());
            assertFalse(actions.undoAction.isEnabled());
        });
    }

    @Test
    void boldButtonShowsAndChangesTheTextAtTheCaret() throws Exception {
        withEditor("one two", () -> {
            JToggleButton button = ToolButtons.toggle(actions.bold);
            editor.select(4, 7);
            button.doClick();
            assertTrue(StyleConstants.isBold(editor.getStyledDocument().getCharacterElement(5).getAttributes()));
            assertTrue(button.isSelected());
            editor.setCaretPosition(1);
            actions.update();
            assertFalse(selected(actions.bold));
            assertFalse(button.isSelected());
            // Undo restores the formatting, and the button follows.
            editor.select(4, 7);
            perform(actions.undoAction);
            actions.update();
            assertFalse(StyleConstants.isBold(editor.getStyledDocument().getCharacterElement(5).getAttributes()));
        });
    }

    @Test
    void clickingTheActiveAlignmentKeepsItSelected() throws Exception {
        withEditor("text", () -> {
            JToggleButton left = ToolButtons.toggle(actions.alignLeft);
            assertTrue(left.isSelected());
            left.doClick();
            assertTrue(left.isSelected());
            perform(actions.alignRight);
            assertTrue(selected(actions.alignRight));
            assertFalse(left.isSelected());
        });
    }

    @Test
    void colourButtonRemembersTheLastColour() throws Exception {
        withEditor("red", () -> {
            editor.select(0, 3);
            actions.applyColor(Color.RED);
            assertEquals(Color.RED, StyleConstants.getForeground(
                    editor.getStyledDocument().getCharacterElement(1).getAttributes()));
        });
    }

    @Test
    void listsAndFileActionsWaitForLaterSteps() throws Exception {
        withEditor("", () -> {
            assertFalse(actions.bullets.isEnabled());
            assertFalse(actions.numbering.isEnabled());
            assertFalse(actions.open.isEnabled());
            assertFalse(actions.save.isEnabled());
            assertTrue(actions.print.isEnabled());
        });
    }
}
