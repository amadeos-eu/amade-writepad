package eu.amadeos.apps.writepad.ui.main;

import eu.amadeos.apps.writepad.OnEdt;
import eu.amadeos.apps.writepad.QueueExecutor;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.apps.writepad.bootstrap.WritepadModule;
import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import eu.amadeos.apps.writepad.service.layout.PageLayout;
import eu.amadeos.apps.writepad.service.list.ListNumbering;
import eu.amadeos.apps.writepad.ui.dialog.ParagraphDialog;
import eu.amadeos.apps.writepad.ui.editor.PageEditor;
import eu.amadeos.apps.writepad.ui.editor.TextFormatting;
import eu.amadeos.commons.swing.ToolButtons;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.JToggleButton;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.TabStop;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
            MainPresenter presenter = TestPresenters.create(null, new PageLayout(DocumentSettings.DEFAULT, 1), config,
                    new QueueExecutor());
            actions = new EditorActions(editor, presenter, msg);
            DefaultStyledDocument doc = new WritepadDocument();
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
    void paragraphAndTabsDialogsChangeTheParagraph() throws Exception {
        withEditor("text", () -> {
            actions.setDialogs(new EditorActions.Dialogs() {
                @Override
                public Optional<ParagraphDialog.Values> paragraph(ParagraphDialog.Values current) {
                    assertEquals(StyleConstants.ALIGN_LEFT, current.alignment());
                    return Optional.of(new ParagraphDialog.Values(new TextFormatting.Indents(20, 30, 0),
                            new TextFormatting.Spacing(0, 6, 2), StyleConstants.ALIGN_RIGHT));
                }

                @Override
                public Optional<List<TabStop>> tabs(List<TabStop> current) {
                    return Optional.of(List.of(new TabStop(99)));
                }
            });
            perform(actions.paragraph);
            perform(actions.tabs);
            assertEquals(new TextFormatting.Indents(20, 30, 0), actions.formatting().indents());
            assertEquals(StyleConstants.ALIGN_RIGHT, actions.formatting().state().alignment());
            assertTrue(selected(actions.alignRight));
            assertEquals(99f, actions.formatting().tabs().get(0).getPosition());
            perform(actions.undoAction);
            assertEquals(List.of(), actions.formatting().tabs(), "undoable");
        });
    }

    private void key(int code, int modifiers) {
        KeyStroke stroke = KeyStroke.getKeyStroke(code, modifiers);
        Object name = editor.getInputMap(JComponent.WHEN_FOCUSED).get(stroke);
        Action action = editor.getActionMap().get(name);
        action.actionPerformed(new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, ""));
    }

    private String text() {
        try {
            return editor.getDocument().getText(0, editor.getDocument().getLength());
        } catch (BadLocationException e) {
            throw new AssertionError(e);
        }
    }

    private String labels() {
        StringBuilder s = new StringBuilder();
        Element root = editor.getStyledDocument().getDefaultRootElement();
        for (int i = 0; i < root.getElementCount(); i++) {
            String label = ListNumbering.label(editor.getStyledDocument(), root.getElement(i));
            if (i > 0) s.append(' ');
            s.append(label == null ? "-" : label);
        }
        return s.toString();
    }

    @Test
    void listButtonsShowTheParagraphAndUndoInOneStep() throws Exception {
        withEditor("a\nb\nc", () -> {
            editor.select(0, 5);
            perform(actions.numbering);
            assertEquals("1. 2. 3.", labels());
            assertTrue(selected(actions.numbering));
            assertFalse(selected(actions.bullets));
            assertTrue(actions.increaseLevel.isEnabled());
            perform(actions.undoAction);
            assertEquals("- - -", labels(), "three paragraphs, one undo step");
            actions.update();
            assertFalse(selected(actions.numbering));
            assertFalse(actions.restartNumbering.isEnabled());
        });
    }

    @Test
    void tabShiftTabEnterAndBackspaceInLists() throws Exception {
        withEditor("one\ntwo", () -> {
            editor.select(0, 7);
            perform(actions.bullets);
            editor.setCaretPosition(4);
            key(KeyEvent.VK_TAB, 0);
            assertEquals("• ◦", labels(), "Tab at the start of an item: one level deeper");
            assertEquals("one\ntwo", text());
            editor.setCaretPosition(5);
            key(KeyEvent.VK_TAB, 0);
            assertEquals("one\nt\two", text(), "Tab inside the text inserts a tab");
            key(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK);
            assertEquals("• •", labels());
            editor.setCaretPosition(editor.getDocument().getLength());
            key(KeyEvent.VK_ENTER, 0);
            assertEquals("• • •", labels(), "Enter in an item: a new item");
            key(KeyEvent.VK_ENTER, 0);
            assertEquals("• • -", labels(), "Enter on an empty item: the list ends");
            assertEquals(3, editor.getStyledDocument().getDefaultRootElement().getElementCount());
            editor.setCaretPosition(4);
            key(KeyEvent.VK_BACK_SPACE, 0);
            assertEquals("• - -", labels(), "Backspace at the start: no longer an item");
            key(KeyEvent.VK_BACK_SPACE, 0);
            assertEquals("•", labels().substring(0, 1));
            assertEquals("onet\two\n", text(), "then Backspace deletes as usual");
        });
    }

    /** Shift+Enter: a new line in the same paragraph or list item, one undo step; typing goes on as text. */
    @Test
    void shiftEnterBreaksTheLine() throws Exception {
        withEditor("one two\nthree", () -> {
            editor.select(0, 13);
            perform(actions.bullets);
            editor.select(3, 4);
            key(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK);
            assertEquals("one\u2028two\nthree", text(), "the space is replaced by the break");
            assertEquals("• •", labels(), "still two items: no new label");
            assertEquals(4, editor.getCaretPosition());
            Element brk = editor.getStyledDocument().getCharacterElement(3);
            assertTrue(WritepadDocument.isLineBreak(brk.getAttributes()));
            assertEquals(4, brk.getEndOffset(), "the break is an element of its own");
            editor.replaceSelection("x");
            assertFalse(WritepadDocument.isLineBreak(editor.getStyledDocument().getCharacterElement(4)
                    .getAttributes()), "text typed after the break is no break");
            assertEquals("one\u2028xtwo\nthree", text());
            perform(actions.undoAction);
            perform(actions.undoAction);
            assertEquals("one two\nthree", text(), "selection and break come back in one step");
        });
    }

    @Test
    void pastedLineSeparatorsBecomeBreaks() throws Exception {
        withEditor("", () -> {
            editor.replaceSelection("a\u2028b\u2028\u2028c");
            int breaks = 0;
            for (int i = 0; i < editor.getDocument().getLength(); i++) {
                boolean isBreak = WritepadDocument.isLineBreak(editor.getStyledDocument().getCharacterElement(i)
                        .getAttributes());
                assertEquals(text().charAt(i) == WritepadDocument.LINE_BREAK, isBreak, "at " + i);
                if (isBreak) breaks++;
            }
            assertEquals(3, breaks);
            assertEquals(1, editor.getStyledDocument().getDefaultRootElement().getElementCount(),
                    "still one paragraph");
        });
    }

    @Test
    void filesWork() throws Exception {
        withEditor("", () -> {
            assertTrue(actions.bullets.isEnabled());
            assertTrue(actions.numbering.isEnabled());
            assertTrue(actions.open.isEnabled());
            assertTrue(actions.save.isEnabled());
            assertTrue(actions.saveAs.isEnabled());
            assertTrue(actions.print.isEnabled());
        });
    }
}
