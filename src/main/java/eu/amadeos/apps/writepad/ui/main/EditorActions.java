package eu.amadeos.apps.writepad.ui.main;

import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.apps.writepad.service.list.ListFormat;
import eu.amadeos.apps.writepad.ui.dialog.ParagraphDialog;
import eu.amadeos.apps.writepad.ui.editor.PageEditor;
import eu.amadeos.apps.writepad.ui.editor.TextFormatting;
import eu.amadeos.apps.writepad.ui.icons.TextColorGlyph;
import eu.amadeos.apps.writepad.ui.icons.WritepadGlyph;
import eu.amadeos.commons.swing.StandardGlyph;
import eu.amadeos.commons.swing.ToolIcon;
import eu.amadeos.commons.swing.ToolIcon.Glyph;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JColorChooser;
import javax.swing.KeyStroke;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabStop;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.function.BooleanSupplier;
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
    public final Action increaseLevel;
    public final Action decreaseLevel;
    public final Action restartNumbering;
    public final Action continueNumbering;
    public final Action paragraph;
    public final Action tabs;
    /** Edits collected into one undo step while {@link #apply} runs. */
    private CompoundEdit group;
    private Dialogs dialogs = new Dialogs() {
    };

    /** The dialogs the paragraph and tabs actions show; the window supplies them, tests replace them. */
    public interface Dialogs {
        default Optional<ParagraphDialog.Values> paragraph(ParagraphDialog.Values current) {
            return Optional.empty();
        }

        default Optional<List<TabStop>> tabs(List<TabStop> current) {
            return Optional.empty();
        }
    }

    public EditorActions(PageEditor editor, MainPresenter presenter, Messages msg) {
        EdtExecutor.requireEdt();
        this.editor = Objects.requireNonNull(editor, "editor");
        this.formatting = new TextFormatting(editor);
        this.msg = msg;

        newDocument = action("menu.file.new", "tip.new", StandardGlyph.NEW, KeyEvent.VK_N, e -> presenter.newDocument());
        open = action("menu.file.open", "tip.open", StandardGlyph.OPEN, KeyEvent.VK_O, e -> presenter.chooseOpen());
        save = action("menu.file.save", "tip.save", StandardGlyph.SAVE, KeyEvent.VK_S, e -> presenter.save());
        saveAs = action("menu.file.save-as", "tip.save-as", StandardGlyph.SAVE, 0, e -> presenter.saveAs());
        saveAs.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL | InputEvent.SHIFT_DOWN_MASK));
        print = action("menu.file.print", "tip.print", StandardGlyph.PRINT, KeyEvent.VK_P, e -> presenter.print());
        printPreview = disabled("menu.file.print-preview", "tip.print-preview", WritepadGlyph.PREVIEW, 0);
        undoAction = action("menu.edit.undo", "tip.undo", StandardGlyph.UNDO, KeyEvent.VK_Z, e -> undo());
        redoAction = action("menu.edit.redo", "tip.redo", StandardGlyph.REDO, KeyEvent.VK_Y, e -> redo());
        cut = action("menu.edit.cut", "tip.cut", StandardGlyph.CUT, KeyEvent.VK_X, e -> edit(editor::cut));
        copy = action("menu.edit.copy", "tip.copy", StandardGlyph.COPY, KeyEvent.VK_C, e -> edit(editor::copy));
        paste = action("menu.edit.paste", "tip.paste", StandardGlyph.PASTE, KeyEvent.VK_V, e -> edit(editor::paste));
        find = disabled("menu.edit.find", "tip.find", StandardGlyph.FIND, KeyEvent.VK_F);

        bold = toggle("menu.format.bold", "tip.bold", WritepadGlyph.BOLD, KeyEvent.VK_B, formatting::toggleBold);
        italic = toggle("menu.format.italic", "tip.italic", WritepadGlyph.ITALIC, KeyEvent.VK_I, formatting::toggleItalic);
        underline = toggle("menu.format.underline", "tip.underline", WritepadGlyph.UNDERLINE, KeyEvent.VK_U,
                formatting::toggleUnderline);
        strikeThrough = toggle("menu.format.strike-through", "tip.strike-through", WritepadGlyph.STRIKE_THROUGH, 0,
                formatting::toggleStrikeThrough);
        textColor = action("menu.format.text-color", "tip.text-color", new TextColorGlyph(() -> lastColor), 0,
                e -> chooseColor());
        alignLeft = alignment("menu.format.align-left", "tip.align-left", WritepadGlyph.ALIGN_LEFT, KeyEvent.VK_L,
                StyleConstants.ALIGN_LEFT);
        alignCenter = alignment("menu.format.align-center", "tip.align-center", WritepadGlyph.ALIGN_CENTER, KeyEvent.VK_E,
                StyleConstants.ALIGN_CENTER);
        alignRight = alignment("menu.format.align-right", "tip.align-right", WritepadGlyph.ALIGN_RIGHT, KeyEvent.VK_R,
                StyleConstants.ALIGN_RIGHT);
        alignJustify = alignment("menu.format.align-justify", "tip.align-justify", WritepadGlyph.ALIGN_JUSTIFY,
                KeyEvent.VK_J, StyleConstants.ALIGN_JUSTIFIED);
        bullets = toggle("menu.format.bullets", "tip.bullets", WritepadGlyph.BULLETS, 0, formatting::toggleBullets);
        numbering = toggle("menu.format.numbering", "tip.numbering", WritepadGlyph.NUMBERING, 0,
                formatting::toggleNumbering);
        increaseLevel = action("menu.format.increase-level", "tip.increase-level", null, 0,
                e -> edit(() -> formatting.changeLevel(1)));
        decreaseLevel = action("menu.format.decrease-level", "tip.decrease-level", null, 0,
                e -> edit(() -> formatting.changeLevel(-1)));
        restartNumbering = action("menu.format.restart-numbering", "tip.restart-numbering", null, 0,
                e -> edit(formatting::restartNumbering));
        continueNumbering = action("menu.format.continue-numbering", "tip.continue-numbering", null, 0,
                e -> edit(formatting::continueNumbering));
        paragraph = action("menu.format.paragraph", "tip.paragraph", null, 0, e -> paragraphDialog());
        tabs = action("menu.format.tabs", "tip.tabs", null, 0, e -> tabsDialog());
        installListKeys();
    }

    /**
     * Keys that act differently in lists, as in Word: Tab at the start of an item (or with several paragraphs
     * selected) indents it a level, Shift+Tab outdents it, Enter on an empty item and Backspace at the start
     * of an item end the list there. Elsewhere the keys do what they always do.
     */
    private void installListKeys() {
        InputMap keys = editor.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actions = editor.getActionMap();
        Action insertTab = actions.get(DefaultEditorKit.insertTabAction);
        Action insertBreak = actions.get(DefaultEditorKit.insertBreakAction);
        Action deleteBefore = actions.get(DefaultEditorKit.deletePrevCharAction);
        listKey(keys, actions, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "writepad-tab", insertTab, () -> {
            if (!formatting.isListItem() || !(atParagraphStart() || severalParagraphs())) return false;
            edit(() -> formatting.changeLevel(1));
            return true;
        });
        listKey(keys, actions, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK),
                "writepad-shift-tab", null, () -> {
                    if (!formatting.isListItem()) return false;
                    edit(() -> formatting.changeLevel(-1));
                    return true;
                });
        listKey(keys, actions, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "writepad-enter", insertBreak, () -> {
            if (!formatting.isListItem() || !emptyParagraph()) return false;
            edit(() -> formatting.endListAtCaret(false));
            return true;
        });
        listKey(keys, actions, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "writepad-backspace",
                deleteBefore, () -> {
                    if (!formatting.isListItem() || !atParagraphStart()) return false;
                    edit(() -> formatting.endListAtCaret(true));
                    return true;
                });
        // Shift+Enter: a new line in the same paragraph (or list item, without a new label).
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "writepad-line-break");
        actions.put("writepad-line-break", new AbstractAction("writepad-line-break") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (editor.isEditable()) insertLineBreak();
            }
        });
    }

    /** Replaces the selection with a line break, as one undo step. */
    public void insertLineBreak() {
        edit(() -> editor.replaceSelection(String.valueOf(WritepadDocument.LINE_BREAK)));
    }

    private void listKey(InputMap keys, ActionMap actions, KeyStroke key, String name, Action otherwise,
                         BooleanSupplier inList) {
        keys.put(key, name);
        actions.put(name, new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!editor.isEditable()) return;
                if (!inList.getAsBoolean() && otherwise != null) otherwise.actionPerformed(e);
            }
        });
    }

    private boolean atParagraphStart() {
        int caret = editor.getCaretPosition();
        return editor.getSelectionStart() == editor.getSelectionEnd()
                && editor.getStyledDocument().getParagraphElement(caret).getStartOffset() == caret;
    }

    private boolean severalParagraphs() {
        return formatting.selectedParagraphs().size() > 1;
    }

    private boolean emptyParagraph() {
        Element p = editor.getStyledDocument().getParagraphElement(editor.getCaretPosition());
        return editor.getSelectionStart() == editor.getSelectionEnd() && p.getEndOffset() - p.getStartOffset() <= 1;
    }

    public void setDialogs(Dialogs dialogs) {
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    private void paragraphDialog() {
        ParagraphDialog.Values current = new ParagraphDialog.Values(formatting.indents(), formatting.spacing(),
                formatting.state().alignment());
        dialogs.paragraph(current).ifPresent(values -> edit(() -> {
            formatting.setIndents(values.indents());
            formatting.setSpacing(values.spacing());
            formatting.setAlignment(values.alignment());
        }));
    }

    private void tabsDialog() {
        dialogs.tabs(formatting.tabs()).ifPresent(stops -> edit(() -> formatting.setTabs(stops)));
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
            if (group != null) group.addEdit(e.getEdit());
            else undo.addEdit(e.getEdit());
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
        Optional<ListFormat> list = formatting.listFormat();
        bullets.putValue(Action.SELECTED_KEY, list.map(ListFormat::isBullet).orElse(false));
        numbering.putValue(Action.SELECTED_KEY, list.map(f -> !f.isBullet()).orElse(false));
        for (Action listOnly : new Action[]{increaseLevel, decreaseLevel, restartNumbering, continueNumbering}) {
            listOnly.setEnabled(list.isPresent());
        }
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

    /**
     * Runs a change as one undo step, however many paragraphs or attributes it touches; the editor gets the
     * focus back (toolbar buttons do not take it, but menu clicks may) and the bars are updated.
     */
    public void apply(Runnable change) {
        edit(change);
    }

    private void edit(Runnable change) {
        boolean outermost = group == null;
        if (outermost) group = new CompoundEdit();
        try {
            change.run();
        } finally {
            if (outermost) {
                CompoundEdit done = group;
                group = null;
                done.end();
                if (done.isSignificant()) undo.addEdit(done);
            }
        }
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
        if (glyph != null) action.putValue(Action.LARGE_ICON_KEY, new ToolIcon(glyph));
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
