package eu.amadeos.apps.writepad.ui.main;

import eu.amadeos.apps.writepad.domain.Picture;
import eu.amadeos.apps.writepad.domain.TableFormat;
import eu.amadeos.apps.writepad.service.table.TableAttributes;
import eu.amadeos.apps.writepad.service.table.TableEditing;
import eu.amadeos.apps.writepad.service.table.Tables;
import eu.amadeos.apps.writepad.ui.dialog.CellBackgroundDialog;
import eu.amadeos.apps.writepad.ui.dialog.InsertTableDialog;
import eu.amadeos.apps.writepad.ui.dialog.TablePropertiesDialog;
import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import eu.amadeos.apps.writepad.service.picture.PictureAttributes;
import eu.amadeos.apps.writepad.ui.dialog.PictureDialog;
import eu.amadeos.apps.writepad.ui.editor.PictureEditing;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.text.BadLocationException;
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
    public final Action insertPicture;
    public final Action pictureProperties;
    public final Action replacePicture;
    public final Action originalSize;
    public final Action savePictureAs;
    public final Action insertTable;
    /** The toolbar's table button: the size grid below the button. */
    public final Action insertTableGrid;
    public final Action insertRowAbove;
    public final Action insertRowBelow;
    public final Action insertColumnLeft;
    public final Action insertColumnRight;
    public final Action deleteRow;
    public final Action deleteColumn;
    public final Action deleteTable;
    public final Action selectRow;
    public final Action selectTable;
    public final Action cellBackground;
    public final Action tableProperties;
    private final List<Action> tableOnly = new ArrayList<>();
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

        /** Asks for an image file. */
        default Optional<Path> choosePicture() {
            return Optional.empty();
        }

        /** The Picture dialog; {@code replacement} asks for another image. */
        default Optional<PictureDialog.Values> picture(Picture current, PictureEditing.Layout layout,
                                                       Supplier<Optional<Picture>> replacement) {
            return Optional.empty();
        }

        /** Asks where to save a picture's image file. */
        default Optional<Path> chooseSavePicture(String suggestedName) {
            return Optional.empty();
        }

        default void showError(String message) {
        }

        /** The Insert Table dialog; {@code contentWidth} is the text column's width in points. */
        default Optional<InsertTableDialog.Values> insertTable(double contentWidth) {
            return Optional.empty();
        }

        default Optional<TablePropertiesDialog.Values> tableProperties(TablePropertiesDialog.Current current,
                                                                       double contentWidth) {
            return Optional.empty();
        }

        default Optional<CellBackgroundDialog.Choice> cellBackground(Color current) {
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
        insertPicture = action("menu.insert.picture", "tip.insert-picture", WritepadGlyph.PICTURE, 0,
                e -> insertPictureFromFile());
        pictureProperties = action("menu.picture.properties", "tip.picture-properties", null, 0, e -> pictureDialog());
        replacePicture = action("menu.picture.replace", "tip.picture-replace", null, 0, e -> replacePicture());
        originalSize = action("menu.picture.original-size", "tip.picture-original-size", null, 0,
                e -> originalSize());
        savePictureAs = action("menu.picture.save-as", "tip.picture-save-as", null, 0, e -> savePictureAs());
        insertTable = action("menu.insert.table", "tip.insert-table", WritepadGlyph.TABLE, 0,
                e -> insertTableDialog());
        insertTable.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F12, CTRL));
        TableGridPicker picker = new TableGridPicker(msg, (columns, rows) -> insertTable(
                TableFormat.of(columns, editor.pageLayout().contentWidth()).withRepeatHeader(true), rows, true),
                this::insertTableDialog);
        insertTableGrid = action("menu.insert.table", "tip.insert-table", WritepadGlyph.TABLE, 0, e -> {
            if (e.getSource() instanceof java.awt.Component c) picker.showBelow(c);
            else insertTableDialog();
        });
        insertRowAbove = tableAction("menu.table.row-above", "tip.table.row-above",
                (doc, at) -> TableEditing.insertRow(doc, at, false));
        insertRowBelow = tableAction("menu.table.row-below", "tip.table.row-below",
                (doc, at) -> TableEditing.insertRow(doc, at, true));
        insertColumnLeft = tableAction("menu.table.column-left", "tip.table.column-left",
                (doc, at) -> TableEditing.insertColumn(doc, at, false));
        insertColumnRight = tableAction("menu.table.column-right", "tip.table.column-right",
                (doc, at) -> TableEditing.insertColumn(doc, at, true));
        deleteRow = tableAction("menu.table.delete-row", "tip.table.delete-row", TableEditing::deleteRow);
        deleteColumn = tableAction("menu.table.delete-column", "tip.table.delete-column", TableEditing::deleteColumn);
        deleteTable = tableAction("menu.table.delete-table", "tip.table.delete-table", TableEditing::deleteTable);
        selectRow = action("menu.table.select-row", "tip.table.select-row", null, 0, e -> selectRow());
        selectTable = action("menu.table.select-table", "tip.table.select-table", null, 0, e -> selectTable());
        cellBackground = action("menu.table.cell-background", "tip.table.cell-background", null, 0,
                e -> cellBackgroundDialog());
        tableProperties = action("menu.table.properties", "tip.table.properties", null, 0,
                e -> tablePropertiesDialog());
        tableOnly.addAll(List.of(insertRowAbove, insertRowBelow, insertColumnLeft, insertColumnRight, deleteRow,
                deleteColumn, deleteTable, selectRow, selectTable, cellBackground, tableProperties));
        installListKeys();
        installTableKeys();
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
            if (tableTab(true)) return true;
            if (!formatting.isListItem() || !(atParagraphStart() || severalParagraphs())) return false;
            edit(() -> formatting.changeLevel(1));
            return true;
        });
        listKey(keys, actions, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK),
                "writepad-shift-tab", null, () -> {
                    if (tableTab(false)) return true;
                    if (!formatting.isListItem()) return false;
                    edit(() -> formatting.changeLevel(-1));
                    return true;
                });
        listKey(keys, actions, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "writepad-enter", insertBreak, () -> {
            if (paragraphAboveTable()) return true;
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

    // ---------------------------------------------------------------- pictures

    private void insertPictureFromFile() {
        dialogs.choosePicture().flatMap(this::readPicture).ifPresent(this::insertPicture);
        editor.requestFocusInWindow();
    }

    /** Puts a picture in place of the selection, fitted to the text column, as one undo step. */
    public void insertPicture(Picture picture) {
        edit(() -> {
            try {
                PictureEditing.insert(editor, PictureEditing.fitted(picture, editor.pageLayout()));
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private Optional<Picture> readPicture(Path path) {
        try {
            return Optional.of(Picture.of(Files.readAllBytes(path), path.getFileName().toString()));
        } catch (IOException | RuntimeException e) {
            dialogs.showError(msg.format("error.picture", path.getFileName(), e.getMessage()));
            return Optional.empty();
        }
    }

    private Picture selected() {
        int at = PictureEditing.selectedPicture(editor);
        return at < 0 ? null : PictureAttributes.at(editor.getStyledDocument(), at);
    }

    /** The Picture dialog for the selected picture. */
    public void pictureDialog() {
        int at = PictureEditing.selectedPicture(editor);
        Picture current = selected();
        if (current == null) return;
        StyledDocument doc = editor.getStyledDocument();
        dialogs.picture(current, PictureEditing.layoutOf(doc, at),
                () -> dialogs.choosePicture().flatMap(this::readPicture)).ifPresent(values -> edit(() -> {
            try {
                PictureAttributes.replace(doc, at, values.picture());
                PictureEditing.select(editor, PictureEditing.setLayout(doc, at, values.layout()));
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
        }));
    }

    private void replacePicture() {
        int at = PictureEditing.selectedPicture(editor);
        Picture current = selected();
        if (current == null) return;
        dialogs.choosePicture().flatMap(this::readPicture).ifPresent(other -> edit(() -> {
            PictureAttributes.replace(editor.getStyledDocument(), at, current.replacedBy(other));
            PictureEditing.select(editor, at);
        }));
    }

    private void originalSize() {
        int at = PictureEditing.selectedPicture(editor);
        Picture current = selected();
        if (current == null) return;
        Picture natural = current.withSize(current.naturalWidth(), current.naturalHeight())
                .fittedInto(editor.pageLayout().contentWidth(), Double.MAX_VALUE);
        edit(() -> {
            PictureAttributes.replace(editor.getStyledDocument(), at, natural);
            PictureEditing.select(editor, at);
        });
    }

    private void savePictureAs() {
        Picture current = selected();
        if (current == null) return;
        dialogs.chooseSavePicture(current.name()).ifPresent(path -> {
            try {
                Files.write(path, current.data());
            } catch (IOException e) {
                dialogs.showError(msg.format("error.save", path.getFileName(), e.getMessage()));
            }
        });
    }

    // ---------------------------------------------------------------- tables

    /** A table command: changes the document at the caret, returns where the caret goes (-1: stays). */
    @FunctionalInterface
    private interface TableChange {
        int apply(WritepadDocument document, int offset) throws BadLocationException;
    }

    private Action tableAction(String nameKey, String tipKey, TableChange change) {
        return action(nameKey, tipKey, null, 0, e -> changeTable(change));
    }

    private void changeTable(TableChange change) {
        if (!(editor.getDocument() instanceof WritepadDocument doc) || !inTable()) return;
        edit(() -> {
            try {
                int caret = change.apply(doc, editor.getSelectionStart());
                if (caret >= 0) editor.setCaretPosition(Math.min(caret, doc.getLength()));
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /** Whether the caret (the selection's start) is in a table. */
    public boolean inTable() {
        return Tables.inTable(editor.getStyledDocument(), editor.getSelectionStart());
    }

    private void insertTableDialog() {
        if (inTable()) return;
        dialogs.insertTable(editor.pageLayout().contentWidth())
                .ifPresent(v -> insertTable(v.format(), v.rows(), v.header()));
        editor.requestFocusInWindow();
    }

    /** Inserts a table at the caret (the selection's start), the caret in its first cell, as one undo step. */
    public void insertTable(TableFormat format, int rows, boolean header) {
        if (!(editor.getDocument() instanceof WritepadDocument doc) || inTable()) return;
        edit(() -> {
            try {
                int at = TableEditing.insertTable(doc, editor.getSelectionStart(), format, rows, header);
                editor.setCaretPosition(at);
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private void selectRow() {
        Tables.Table table = Tables.at(editor.getDocument(), editor.getSelectionStart());
        if (table == null) return;
        Tables.Row row = table.row(editor.getSelectionStart());
        editor.setCaretPosition(row.start());
        editor.moveCaretPosition(row.end() - 1);
        editor.requestFocusInWindow();
    }

    private void selectTable() {
        Tables.Table table = Tables.at(editor.getDocument(), editor.getSelectionStart());
        if (table == null) return;
        editor.setCaretPosition(table.start());
        editor.moveCaretPosition(table.end() - 1);
        editor.requestFocusInWindow();
    }

    private Element caretCell() {
        Tables.Table table = Tables.at(editor.getDocument(), editor.getSelectionStart());
        Tables.Cell cell = table == null ? null : table.cell(editor.getSelectionStart());
        return cell == null ? null : cell.first();
    }

    private void cellBackgroundDialog() {
        Element cell = caretCell();
        if (cell == null || !(editor.getDocument() instanceof WritepadDocument doc)) return;
        dialogs.cellBackground(TableAttributes.background(cell.getAttributes())).ifPresent(choice -> edit(() -> {
            try {
                TableEditing.setBackground(doc, editor.getSelectionStart(), editor.getSelectionEnd(), choice.color());
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
        }));
    }

    /** The Table Properties dialog for the table at the caret. */
    public void tablePropertiesDialog() {
        Tables.Table table = Tables.at(editor.getDocument(), editor.getSelectionStart());
        Element cell = caretCell();
        if (table == null || cell == null || !(editor.getDocument() instanceof WritepadDocument doc)) return;
        TablePropertiesDialog.Current current = new TablePropertiesDialog.Current(table.format(),
                TableAttributes.background(cell.getAttributes()), TableAttributes.verticalAlignment(cell.getAttributes()));
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        dialogs.tableProperties(current, editor.pageLayout().contentWidth()).ifPresent(v -> edit(() -> {
            try {
                if (v.backgroundChanged()) TableEditing.setBackground(doc, start, end, v.background().orElse(null));
                if (v.verticalAlignment() != null) TableEditing.setVerticalAlignment(doc, start, end, v.verticalAlignment());
                TableEditing.setFormat(doc, start, v.format());
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
        }));
    }

    /**
     * Keys in tables: Tab goes to the next cell (in the last cell it adds a row), Shift+Tab to the one before,
     * Ctrl+Tab types a tab. Enter at the very start of a table that begins the document makes a paragraph in
     * front of it, the only way to write above such a table.
     */
    private void installTableKeys() {
        InputMap keys = editor.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actions = editor.getActionMap();
        // Ctrl+Tab is the text component's focus key; here it is the tab within a cell.
        editor.setFocusTraversalKeys(java.awt.KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, java.util.Set.of());
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, CTRL), "writepad-real-tab");
        actions.put("writepad-real-tab", new AbstractAction("writepad-real-tab") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (editor.isEditable()) edit(() -> editor.replaceSelection("\t"));
            }
        });
    }

    /** Tab or Shift+Tab in a table; false outside tables. */
    private boolean tableTab(boolean forward) {
        if (!inTable() || !(editor.getDocument() instanceof WritepadDocument doc)) return false;
        int at = editor.getSelectionStart();
        int target = forward ? TableEditing.nextCell(doc, at) : TableEditing.previousCell(doc, at);
        if (target < 0 && forward) {
            changeTable((d, offset) -> TableEditing.insertRow(d, offset, true));
            return true;
        }
        if (target < 0) return true;
        Tables.Cell cell = Tables.at(doc, target).cell(target);
        editor.setCaretPosition(cell.start());
        // As in Word: the cell's text is selected, so typing replaces it.
        if (cell.end() - 1 > cell.start()) editor.moveCaretPosition(cell.end() - 1);
        return true;
    }

    /** Enter at the start of a table with nothing above it. */
    private boolean paragraphAboveTable() {
        if (!inTable() || editor.getSelectionStart() != editor.getSelectionEnd()
                || !(editor.getDocument() instanceof WritepadDocument doc)) {
            return false;
        }
        int at = editor.getCaretPosition();
        Tables.Table table = Tables.at(doc, at);
        if (table.start() != at || at > 0 && !Tables.inTable(doc, at - 1)) return false;
        changeTable(TableEditing::paragraphBefore);
        return true;
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
        boolean picture = PictureEditing.selectedPicture(editor) >= 0;
        for (Action pictureOnly : new Action[]{pictureProperties, replacePicture, originalSize, savePictureAs}) {
            pictureOnly.setEnabled(picture);
        }
        insertPicture.setEnabled(editor.isEditable());
        boolean table = inTable();
        for (Action a : tableOnly) a.setEnabled(table && (editor.isEditable() || a == selectRow || a == selectTable));
        insertTable.setEnabled(!table && editor.isEditable());
        insertTableGrid.setEnabled(!table && editor.isEditable());
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
