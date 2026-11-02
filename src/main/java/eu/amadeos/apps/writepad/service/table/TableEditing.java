package eu.amadeos.apps.writepad.service.table;

import eu.amadeos.apps.writepad.domain.TableFormat;
import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import eu.amadeos.apps.writepad.service.list.ListAttributes;
import eu.amadeos.apps.writepad.service.picture.PictureAttributes;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.Position;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The table commands on a document: insert a table, insert and delete rows and columns, change the table's
 * format and the cells' backgrounds. Each command is one undo step. Offsets name a place in the table, usually
 * the caret; the commands return where the caret should go afterwards.
 */
public final class TableEditing {
    /** Grey of a header row, as in LibreOffice's "Table Heading" examples. */
    public static final Color HEADER_BACKGROUND = new Color(0xDDDDDD);

    private TableEditing() {
    }

    // --8<-- [start:insert]
    /**
     * Inserts an empty table at an offset outside tables. At the start of a paragraph the table goes in front
     * of it; elsewhere the paragraph is split and the table goes between the halves. So there is always a
     * paragraph after a table, where the text goes on.
     *
     * @param header whether the first row is a header: bold, grey, and repeated on every page if the format
     *               says so
     * @return the offset of the first cell
     */
    public static int insertTable(WritepadDocument document, int offset, TableFormat format, int rows,
                                  boolean header) throws BadLocationException {
        if (Tables.inTable(document, offset)) throw new IllegalArgumentException("No tables in tables");
        int columns = format.columns();
        int[] at = new int[1];
        document.structurally(() -> {
            Element paragraph = document.getParagraphElement(offset);
            int start = offset;
            if (offset != paragraph.getStartOffset()) {
                document.insertString(offset, "\n", characterAttributes(document, offset - 1));
                start = offset + 1;
            }
            at[0] = start;
            SimpleAttributeSet chars = characterAttributes(document, start);
            document.insertString(start, "\n".repeat(rows * columns), chars);
            AttributeSet base = document.getParagraphElement(start).getAttributes();
            for (int r = 0; r < rows; r++) {
                Object row = TableAttributes.newRow();
                for (int c = 0; c < columns; c++) {
                    int p = start + r * columns + c;
                    SimpleAttributeSet a = cellParagraph(base, format, row, c);
                    if (header && r == 0) TableAttributes.setBackground(a, HEADER_BACKGROUND);
                    document.setParagraphAttributes(p, 0, a, true);
                    if (header && r == 0) {
                        SimpleAttributeSet bold = new SimpleAttributeSet();
                        StyleConstants.setBold(bold, true);
                        document.setCharacterAttributes(p, 1, bold, false);
                    }
                }
            }
        });
        return at[0];
    }
    // --8<-- [end:insert]

    /**
     * The paragraph attributes of a new cell: those of the paragraph it came from, out of any list and without
     * indents or spacing, in the given cell.
     */
    static SimpleAttributeSet cellParagraph(AttributeSet from, TableFormat format, Object row, int column) {
        SimpleAttributeSet a = new SimpleAttributeSet(from);
        TableAttributes.clear(a);
        a.removeAttribute(ListAttributes.LIST);
        a.removeAttribute(ListAttributes.LEVEL);
        a.removeAttribute(ListAttributes.FORMAT);
        StyleConstants.setLeftIndent(a, 0);
        StyleConstants.setRightIndent(a, 0);
        StyleConstants.setFirstLineIndent(a, 0);
        StyleConstants.setSpaceAbove(a, 0);
        StyleConstants.setSpaceBelow(a, 0);
        TableAttributes.set(a, format, row, column);
        return a;
    }

    private static SimpleAttributeSet characterAttributes(WritepadDocument document, int offset) {
        SimpleAttributeSet a = new SimpleAttributeSet(
                document.getCharacterElement(Math.max(0, offset)).getAttributes().copyAttributes());
        a.removeAttribute(PictureAttributes.PICTURE);
        a.removeAttribute(WritepadDocument.BREAK);
        a.removeAttribute(StyleConstants.Bold);
        return a;
    }

    // ---------------------------------------------------------------- rows

    /**
     * Inserts an empty row above or below the row at an offset, each new cell formatted like the one it
     * copies (background, alignment).
     *
     * @return the offset of the new row's first cell
     */
    public static int insertRow(WritepadDocument document, int offset, boolean below) throws BadLocationException {
        Tables.Table table = require(document, offset);
        Tables.Row row = table.row(offset);
        int at = below ? row.end() : row.start();
        document.structurally(() -> {
            List<AttributeSet> sources = new ArrayList<>();
            List<SimpleAttributeSet> chars = new ArrayList<>();
            for (Tables.Cell c : row.cells()) {
                sources.add(c.first().getAttributes().copyAttributes());
                chars.add(characterAttributes(document, c.start()));
            }
            AttributeSet after = keep(document, at);
            int n = row.cells().size();
            document.insertString(at, "\n".repeat(n), chars.getFirst());
            Object id = TableAttributes.newRow();
            for (int i = 0; i < n; i++) {
                SimpleAttributeSet a = new SimpleAttributeSet(sources.get(i));
                TableAttributes.set(a, table.format(), id, row.cells().get(i).column());
                document.setParagraphAttributes(at + i, 0, a, true);
                document.setCharacterAttributes(at + i, 1, chars.get(i), true);
            }
            restore(document, at + n, after);
        });
        return at;
    }

    /** Deletes the row at an offset; the last row takes the table with it. */
    public static int deleteRow(WritepadDocument document, int offset) throws BadLocationException {
        Tables.Table table = require(document, offset);
        if (table.rows().size() == 1) return deleteTable(document, offset);
        Tables.Row row = table.row(offset);
        int start = row.start();
        removeParagraphs(document, start, row.end());
        return start;
    }

    // ---------------------------------------------------------------- columns

    /**
     * Inserts an empty column left or right of the column at an offset. The new column takes half of that
     * column's width.
     *
     * @return the offset of the new cell in the row of the offset
     */
    public static int insertColumn(WritepadDocument document, int offset, boolean right) throws BadLocationException {
        Tables.Table table = require(document, offset);
        int column = table.cell(offset).column();
        int newColumn = right ? column + 1 : column;
        TableFormat old = table.format();
        double[] w = old.widths();
        int source = Math.min(column, w.length - 1);
        double[] widths = new double[w.length + 1];
        for (int i = 0, j = 0; i < widths.length; i++) {
            if (i == newColumn) widths[i] = w[source] / 2;
            else widths[i] = j == source ? w[j++] / 2 : w[j++];
        }
        TableFormat format = old.withWidths(widths);
        int rowIndex = table.rowIndex(offset);
        int[] caret = new int[1];
        Position tableStart = document.createPosition(table.start());
        document.structurally(() -> {
            setFormat(document, table, format);
            List<Tables.Row> rows = table.rows();
            List<Position> found = new ArrayList<>();
            for (int r = rows.size() - 1; r >= 0; r--) {
                Tables.Row row = Tables.at(document, tableStart.getOffset()).rows().get(r);
                Tables.Cell next = null;
                Tables.Cell before = null;
                for (Tables.Cell c : row.cells()) {
                    if (c.column() >= newColumn && next == null) next = c;
                    if (c.column() < newColumn) before = c;
                }
                // Renumber the cells right of the new one first, so that it stays a cell of its own.
                for (Tables.Cell c : row.cells()) {
                    if (c.column() >= newColumn) setColumn(document, c, c.column() + 1);
                }
                int at = next != null ? next.start() : row.end();
                Tables.Cell like = right || next == null ? (before != null ? before : next) : next;
                AttributeSet source1 = like.first().getAttributes().copyAttributes();
                SimpleAttributeSet chars = characterAttributes(document, like.start());
                AttributeSet after = keep(document, at);
                document.insertString(at, "\n", chars);
                SimpleAttributeSet a = new SimpleAttributeSet(source1);
                TableAttributes.set(a, format, row.id(), newColumn);
                document.setParagraphAttributes(at, 0, a, true);
                restore(document, at + 1, after);
                if (r == rowIndex) found.add(document.createPosition(at));
            }
            caret[0] = found.isEmpty() ? tableStart.getOffset() : found.getFirst().getOffset();
        });
        return caret[0];
    }

    /** Deletes the column at an offset; the last column takes the table with it. */
    public static int deleteColumn(WritepadDocument document, int offset) throws BadLocationException {
        Tables.Table table = require(document, offset);
        int column = table.cell(offset).column();
        TableFormat old = table.format();
        if (old.columns() <= 1 && table.columns() <= 1) return deleteTable(document, offset);
        double[] w = old.widths();
        double[] widths = new double[Math.max(1, w.length - 1)];
        for (int i = 0, j = 0; i < w.length && j < widths.length; i++) {
            if (i != column) widths[j++] = w[i];
        }
        TableFormat format = old.withWidths(widths);
        Position tableStart = document.createPosition(table.start());
        int rowIndex = table.rowIndex(offset);
        int[] caret = new int[1];
        document.structurally(() -> {
            setFormat(document, table, format);
            int rows = table.rows().size();
            Position here = null;
            for (int r = rows - 1; r >= 0; r--) {
                Tables.Row row = Tables.at(document, tableStart.getOffset()).rows().get(r);
                Tables.Cell gone = null;
                for (Tables.Cell c : row.cells()) {
                    if (c.column() == column) gone = c;
                }
                if (gone == null || row.cells().size() == 1) continue;
                int start = gone.start();
                removeParagraphs(document, start, gone.end());
                Tables.Row now = Tables.at(document, tableStart.getOffset()).rows().get(r);
                for (Tables.Cell c : now.cells()) {
                    if (c.column() > column) setColumn(document, c, c.column() - 1);
                }
                if (r == rowIndex) {
                    Tables.Row after = Tables.at(document, tableStart.getOffset()).rows().get(r);
                    here = document.createPosition(after.cell(Math.max(0, column - 1)).start());
                }
            }
            caret[0] = here != null ? here.getOffset() : tableStart.getOffset();
        });
        return caret[0];
    }

    // ---------------------------------------------------------------- table

    /** Deletes the table at an offset. @return where its first cell was, now the paragraph after it */
    public static int deleteTable(WritepadDocument document, int offset) throws BadLocationException {
        Tables.Table table = require(document, offset);
        int start = table.start();
        removeParagraphs(document, start, table.end());
        return start;
    }

    /** Gives the table at an offset another format (widths, lines, padding, header, alignment). */
    public static void setFormat(WritepadDocument document, int offset, TableFormat format)
            throws BadLocationException {
        Tables.Table table = require(document, offset);
        document.grouped(() -> setFormat(document, table, format));
    }

    private static void setFormat(WritepadDocument document, Tables.Table table, TableFormat format) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        a.addAttribute(TableAttributes.TABLE, format);
        document.setParagraphAttributes(table.start(), table.end() - table.start(), a, false);
    }

    /** Sets the background of the cells from start to end (a selection; equal for the caret's cell). */
    public static void setBackground(WritepadDocument document, int start, int end, Color color)
            throws BadLocationException {
        forCells(document, start, end, a -> TableAttributes.setBackground(a, color));
    }

    public static void setVerticalAlignment(WritepadDocument document, int start, int end,
                                            TableAttributes.VerticalAlignment alignment) throws BadLocationException {
        forCells(document, start, end, a -> a.addAttribute(TableAttributes.CELL_VALIGN, alignment));
    }

    private static void forCells(WritepadDocument document, int start, int end,
                                 java.util.function.Consumer<MutableAttributeSet> change) throws BadLocationException {
        document.grouped(() -> {
            for (Tables.Cell c : cells(document, start, end)) {
                SimpleAttributeSet a = new SimpleAttributeSet();
                change.accept(a);
                if (a.getAttributeCount() == 0) {
                    // Removing an attribute: replace each paragraph's set by a copy without it.
                    for (Element p : c.paragraphs()) {
                        SimpleAttributeSet copy = new SimpleAttributeSet(p.getAttributes().copyAttributes());
                        change.accept(copy);
                        document.setParagraphAttributes(p.getStartOffset(), 0, copy, true);
                    }
                } else {
                    document.setParagraphAttributes(c.start(), c.end() - c.start(), a, false);
                }
            }
        });
    }

    /** The cells a range touches, in reading order; the caret's cell for an empty range. */
    public static List<Tables.Cell> cells(WritepadDocument document, int start, int end) {
        List<Tables.Cell> cells = new ArrayList<>();
        Tables.Table table = Tables.at(document, start);
        if (table == null) return cells;
        int last = Math.max(start, end - 1);
        for (Tables.Cell c : table.cells()) {
            if (c.end() > start && c.start() <= last) cells.add(c);
        }
        return cells;
    }

    // ---------------------------------------------------------------- moving between cells

    /** The start of the next cell's text after the cell at an offset, -1 in the last cell. */
    public static int nextCell(WritepadDocument document, int offset) {
        Tables.Table table = Tables.at(document, offset);
        if (table == null) return -1;
        List<Tables.Cell> cells = table.cells();
        for (int i = 0; i < cells.size() - 1; i++) {
            if (cells.get(i).contains(offset)) return cells.get(i + 1).start();
        }
        return -1;
    }

    /** The start of the cell before the one at an offset, -1 in the first cell. */
    public static int previousCell(WritepadDocument document, int offset) {
        Tables.Table table = Tables.at(document, offset);
        if (table == null) return -1;
        List<Tables.Cell> cells = table.cells();
        for (int i = 1; i < cells.size(); i++) {
            if (cells.get(i).contains(offset)) return cells.get(i - 1).start();
        }
        return -1;
    }

    /**
     * Inserts an empty paragraph in front of the table at an offset: the way out of a table at the very
     * start of a document.
     */
    public static int paragraphBefore(WritepadDocument document, int offset) throws BadLocationException {
        Tables.Table table = require(document, offset);
        int at = table.start();
        document.structurally(() -> {
            Element first = document.getParagraphElement(at);
            AttributeSet cell = first.getAttributes().copyAttributes();
            document.insertString(at, "\n", characterAttributes(document, at));
            SimpleAttributeSet plain = new SimpleAttributeSet(cell);
            TableAttributes.clear(plain);
            document.setParagraphAttributes(at, 0, plain, true);
            document.setParagraphAttributes(at + 1, 0, cell, true);
        });
        return at;
    }

    // ---------------------------------------------------------------- helpers

    private static Tables.Table require(WritepadDocument document, int offset) {
        Tables.Table table = Tables.at(document, offset);
        if (table == null) throw new IllegalArgumentException("No table at " + offset);
        return table;
    }

    private static void setColumn(WritepadDocument document, Tables.Cell cell, int column) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        a.addAttribute(TableAttributes.COLUMN, column);
        document.setParagraphAttributes(cell.start(), cell.end() - cell.start(), a, false);
    }

    /** The paragraph attributes at an offset, to restore after an edit that moves them. */
    private static AttributeSet keep(WritepadDocument document, int offset) {
        return document.getParagraphElement(Math.min(offset, document.getLength()))
                .getAttributes().copyAttributes();
    }

    private static void restore(WritepadDocument document, int offset, AttributeSet attributes) {
        document.setParagraphAttributes(Math.min(offset, document.getLength()), 0, attributes, true);
    }

    /**
     * Removes whole paragraphs [start, end). Swing's document would leave the paragraph after them with the
     * attributes of the first removed one, so they are put back.
     */
    private static void removeParagraphs(WritepadDocument document, int start, int end) throws BadLocationException {
        document.structurally(() -> {
            AttributeSet after = keep(document, end);
            document.remove(start, end - start);
            restore(document, start, after);
        });
    }
}
