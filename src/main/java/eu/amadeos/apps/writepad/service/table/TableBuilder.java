package eu.amadeos.apps.writepad.service.table;

import eu.amadeos.apps.writepad.domain.TableFormat;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns paragraphs a reader appended into a table. The reader announces rows and cells as it reads them and
 * appends each cell's paragraphs to the end of the document; at the end of the table, {@link #finish} gives
 * the paragraphs their table, row and column. A cell without paragraphs gets an empty one, as every cell
 * needs one.
 */
public final class TableBuilder {
    private final StyledDocument document;
    private final List<List<Cell>> rows = new ArrayList<>();
    private int cellStart = -1;

    private record Cell(int start, int end, Color background, TableAttributes.VerticalAlignment alignment) {
    }

    public TableBuilder(StyledDocument document) {
        this.document = document;
    }

    /** Starts a row. */
    public void row() {
        rows.add(new ArrayList<>());
    }

    /** Starts a cell: its paragraphs follow at the end of the document. */
    public void cellStart() {
        cellStart(document.getLength());
    }

    /** Starts a cell whose paragraphs begin at the given offset and reach to the end of the document. */
    public void cellStart(int offset) {
        if (rows.isEmpty()) row();
        cellStart = offset;
    }

    /**
     * Ends the cell started last.
     *
     * @param background its background, null for none
     * @param emptyText  character format of the empty paragraph an empty cell gets
     */
    public void cellEnd(Color background, TableAttributes.VerticalAlignment alignment, AttributeSet emptyText) {
        if (cellStart < 0) cellStart();
        int end = document.getLength();
        if (end == cellStart) {
            try {
                document.insertString(end, "\n", emptyText);
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
            end = document.getLength();
        }
        rows.getLast().add(new Cell(cellStart, end, background,
                alignment == null ? TableAttributes.VerticalAlignment.TOP : alignment));
        cellStart = -1;
    }

    /** An empty cell (one that a merged cell covers, or a missing one at the end of a short row). */
    public void emptyCell(AttributeSet emptyText) {
        cellStart();
        cellEnd(null, null, emptyText);
    }

    /** The number of cells of the longest row so far. */
    public int columns() {
        int n = 0;
        for (List<Cell> r : rows) n = Math.max(n, r.size());
        return n;
    }

    public int rows() {
        return rows.size();
    }

    /** The cells of the current row so far. */
    public int cellsInRow() {
        return rows.isEmpty() ? 0 : rows.getLast().size();
    }

    /**
     * Makes the paragraphs a table of the given format; columns without a width in it get the average width.
     *
     * @return the format the table got, null if there were no cells
     */
    public TableFormat finish(TableFormat format) {
        rows.removeIf(List::isEmpty);
        if (rows.isEmpty()) return null;
        int columns = columns();
        TableFormat table = format;
        if (format.columns() != columns) {
            double[] w = new double[columns];
            double average = format.totalWidth() / format.columns();
            for (int c = 0; c < columns; c++) w[c] = c < format.columns() ? format.width(c) : average;
            table = format.withWidths(w);
        }
        for (List<Cell> row : rows) {
            Object id = TableAttributes.newRow();
            for (int c = 0; c < row.size(); c++) {
                Cell cell = row.get(c);
                SimpleAttributeSet a = new SimpleAttributeSet();
                TableAttributes.set(a, table, id, c);
                if (cell.background() != null) TableAttributes.setBackground(a, cell.background());
                if (cell.alignment() != TableAttributes.VerticalAlignment.TOP) {
                    a.addAttribute(TableAttributes.CELL_VALIGN, cell.alignment());
                }
                document.setParagraphAttributes(cell.start(), cell.end() - cell.start(), a, false);
            }
        }
        return table;
    }
}
