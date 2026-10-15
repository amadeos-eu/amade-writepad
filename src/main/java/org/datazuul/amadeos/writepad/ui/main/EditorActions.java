package org.datazuul.amadeos.writepad.ui.main;

import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.commons.swing.EdtExecutor;
import org.datazuul.amadeos.writepad.ui.editor.PageEditor;
import org.datazuul.amadeos.writepad.ui.editor.TextFormatting;
import org.datazuul.amadeos.writepad.ui.icons.ToolIcon;
import org.datazuul.amadeos.writepad.ui.icons.ToolIcon.Glyph;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JColorChooser;
import javax.swing.KeyStroke;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The actions of menu bar, toolbar and format bar. Menu items and buttons share one action each, so text,
 * shortcut, enabled state and checkmark always agree. {@link #update()} refreshes them from the editor:
 * the formatting at the caret, whether something is selected, what can be undone.
 *
 * <p>Icons are set as {@link Action#LARGE_ICON_KEY} only: buttons show them, menus stay without icons as
 * in CDE.
 */
public final class EditorActions {
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;

    private final PageEditor editor;
    private final TextFormatting formatting;
    private final Messages msg;
    private final UndoManager undo = new UndoManager();
    private final List<Runnable> listeners = new ArrayList<>();
    private Color lastColor = Color.RED;

    public final Action newDocument;
    public final Action open;
    public final Action save;
    public final Action saveAs;
    public final Action print;
    public final Action printPreview;
    public final Action undoAction;
    public final Action redoAction;
    public final Action cut;
    public final Action copy;
    public final Action paste;
    public final Action find;
    public final Action bold;
    public final Action italic;
    public final Action underline;
    public final Action strikeThrough;
    public final Action textColor;
    public final Action alignLeft;
    public final Action alignCenter;
    public final Action alignRight;
    public final Action alignJustify;
    public final Action bullets;
    public final Action numbering;

    public EditorActions(PageEditor editor, MainPresenter presenter, Messages msg) {
        EdtExecutor.requireEdt();
        this.editor = Objects.requireNonNull(editor, "editor");
        this.formatting = new TextFormatting(editor);
        this.msg = msg;

        newDocument = action("menu.file.new", "tip.new", Glyph.NEW, KeyEvent.VK_N, e -> presenter.newDocument());
        open = action("menu.file.open", "tip.open", Glyph.OPEN, KeyEvent.VK_O, e -> presenter.chooseOpen());
        save = action("menu.file.save", "tip.save", Glyph.SAVE, KeyEvent.VK_S, e -> presenter.save());
        saveAs = action("menu.file.save-as", "tip.save-as", Glyph.SAVE, 0, e -> presenter.saveAs());
        saveAs.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL | InputEvent.SHIFT_DOWN_MASK));
        print = action("menu.file.print", "tip.print", Glyph.PRINT, KeyEvent.VK_P, e -> presenter.print());
        printPreview = disabled("menu.file.print-preview", "tip.print-preview", Glyph.PREVIEW, 0);
        undoAction = action("menu.edit.undo", "tip.undo", Glyph.UNDO, KeyEvent.VK_Z, e -> undo());
        redoAction = action("menu.edit.redo", "tip.redo", Glyph.REDO, KeyEvent.VK_Y, e -> redo());
        cut = action("menu.edit.cut", "tip.cut", Glyph.CUT, KeyEvent.VK_X, e -> edit(editor::cut));
        copy = action("menu.edit.copy", "tip.copy", Glyph.COPY, KeyEvent.VK_C, e -> edit(editor::copy));
        paste = action("menu.edit.paste", "tip.paste", Glyph.PASTE, KeyEvent.VK_V, e -> edit(editor::paste));
        find = disabled("menu.edit.find", "tip.find", Glyph.FIND, KeyEvent.VK_F);

        bold = toggle("menu.format.bold", "tip.bold", Glyph.BOLD, KeyEvent.VK_B, formatting::toggleBold);
        italic = toggle("menu.format.italic", "tip.italic", Glyph.ITALIC, KeyEvent.VK_I, formatting::toggleItalic);
        underline = toggle("menu.format.underline", "tip.underline", Glyph.UNDERLINE, KeyEvent.VK_U,
                formatting::toggleUnderline);
        strikeThrough = toggle("menu.format.strike-through", "tip.strike-through", Glyph.STRIKE_THROUGH, 0,
                formatting::toggleStrikeThrough);
        textColor = action("menu.format.text-color", "tip.text-color", Glyph.TEXT_COLOR, 0, e -> chooseColor());
        textColor.putValue(Action.LARGE_ICON_KEY, new ToolIcon(Glyph.TEXT_COLOR, ToolIcon.SIZE, () -> lastColor));
        alignLeft = alignment("menu.format.align-left", "tip.align-left", Glyph.ALIGN_LEFT, KeyEvent.VK_L,
                StyleConstants.ALIGN_LEFT);
        alignCenter = alignment("menu.format.align-center", "tip.align-center", Glyph.ALIGN_CENTER, KeyEvent.VK_E,
                StyleConstants.ALIGN_CENTER);
        alignRight = alignment("menu.format.align-right", "tip.align-right", Glyph.ALIGN_RIGHT, KeyEvent.VK_R,
                StyleConstants.ALIGN_RIGHT);
        alignJustify = alignment("menu.format.align-justify", "tip.align-justify", Glyph.ALIGN_JUSTIFY,
                KeyEvent.VK_J, StyleConstants.ALIGN_JUSTIFIED);
        // Lists come with the ruler (step 3 of the plan).
        bullets = disabled("menu.format.bullets", "tip.bullets", Glyph.BULLETS, 0);
        bullets.putValue(Action.SELECTED_KEY, false);
        numbering = disabled("menu.format.numbering", "tip.numbering", Glyph.NUMBERING, 0);
        numbering.putValue(Action.SELECTED_KEY, false);
    }

    public TextFormatting formatting() {
        return formatting;
    }

    /** Called after {@link #update()}, e.g. by the format bar to show font and size. */
    public void addUpdateListener(Runnable listener) {
        listeners.add(listener);
    }

    /** Starts the undo history of a new document. */
    public void attach(StyledDocument document) {
        undo.discardAllEdits();
        document.addUndoableEditListener(e -> {
            undo.addEdit(e.getEdit());
            updateUndo();
        });
        update();
    }

    /** Refreshes enabled states and checkmarks from the editor; call after caret moves and edits. */
    public void update() {
        TextFormatting.State state = formatting.state();
        bold.putValue(Action.SELECTED_KEY, state.bold());
        italic.putValue(Action.SELECTED_KEY, state.italic());
        underline.putValue(Action.SELECTED_KEY, state.underline());
        strikeThrough.putValue(Action.SELECTED_KEY, state.strikeThrough());
        alignLeft.putValue(Action.SELECTED_KEY, state.alignment() == StyleConstants.ALIGN_LEFT);
        alignCenter.putValue(Action.SELECTED_KEY, state.alignment() == StyleConstants.ALIGN_CENTER);
        alignRight.putValue(Action.SELECTED_KEY, state.alignment() == StyleConstants.ALIGN_RIGHT);
        alignJustify.putValue(Action.SELECTED_KEY, state.alignment() == StyleConstants.ALIGN_JUSTIFIED);
        boolean selection = editor.getSelectionStart() != editor.getSelectionEnd();
        cut.setEnabled(selection && editor.isEditable());
        copy.setEnabled(selection);
        updateUndo();
        for (Runnable listener : listeners) listener.run();
    }

    private void updateUndo() {
        undoAction.setEnabled(undo.canUndo());
        redoAction.setEnabled(undo.canRedo());
    }

    private void undo() {
        try {
            if (undo.canUndo()) undo.undo();
        } catch (CannotUndoException ignored) {
            // nothing left to undo
        }
        update();
    }

    private void redo() {
        try {
            if (undo.canRedo()) undo.redo();
        } catch (CannotRedoException ignored) {
            // nothing left to redo
        }
        update();
    }

    /** Toolbar buttons do not take the focus, but menu clicks may; the editor gets it back. */
    private void edit(Runnable change) {
        change.run();
        editor.requestFocusInWindow();
        update();
    }

    private void chooseColor() {
        Color chosen = JColorChooser.showDialog(editor, msg.get("dialog.text-color.title"),
                formatting.state().foreground());
        if (chosen != null) applyColor(chosen);
    }

    /** Colours the selection (or what is typed next); the button keeps the colour for the next click. */
    public void applyColor(Color color) {
        lastColor = color;
        edit(() -> formatting.setForeground(color));
    }

    private Action action(String nameKey, String tipKey, Glyph glyph, int key, Consumer<ActionEvent> perform) {
        Action action = new AbstractAction(msg.get(nameKey)) {
            @Override
            public void actionPerformed(ActionEvent e) {
                perform.accept(e);
            }
        };
        action.putValue(Action.ACTION_COMMAND_KEY, nameKey);
        action.putValue(Action.SHORT_DESCRIPTION, msg.get(tipKey));
        action.putValue(Action.LARGE_ICON_KEY, new ToolIcon(glyph));
        if (key != 0) action.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(key, CTRL));
        return action;
    }

    private Action disabled(String nameKey, String tipKey, Glyph glyph, int key) {
        Action action = action(nameKey, tipKey, glyph, key, e -> {
        });
        action.setEnabled(false);
        return action;
    }

    /**
     * A formatting switch: the new state comes from the text, not from the button that was clicked, and the
     * checkmark is set again from the text afterwards.
     */
    private Action toggle(String nameKey, String tipKey, Glyph glyph, int key, Runnable apply) {
        Action action = action(nameKey, tipKey, glyph, key, e -> edit(apply));
        action.putValue(Action.SELECTED_KEY, false);
        return action;
    }

    private Action alignment(String nameKey, String tipKey, Glyph glyph, int key, int alignment) {
        return toggle(nameKey, tipKey, glyph, key, () -> formatting.setAlignment(alignment));
    }
}
