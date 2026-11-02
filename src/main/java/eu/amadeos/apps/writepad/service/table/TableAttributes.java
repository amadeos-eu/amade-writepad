package eu.amadeos.apps.writepad.service.table;

import eu.amadeos.apps.writepad.domain.TableFormat;

import javax.swing.text.AttributeSet;
import javax.swing.text.MutableAttributeSet;
import java.awt.Color;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Table membership as paragraph attributes. A table is a run of paragraphs that carry the same
 * {@link TableFormat} under {@link #TABLE}; each of them also names its row ({@link #ROW}, an object that
 * only equals itself) and its column ({@link #COLUMN}, from 0). A cell is the run of paragraphs with the same
 * table, row and column, so Enter in a cell simply makes a new paragraph in that cell, and everything that
 * works on paragraphs (lists, alignment, the formats) keeps working inside tables.
 *
 * <p>A cell's background colour ({@link #CELL_BACKGROUND}) and vertical alignment ({@link #CELL_VALIGN}) sit
 * on every paragraph of the cell; the first paragraph's ones count.
 */
public final class TableAttributes {
    public static final Object TABLE = new Key("table");
    public static final Object ROW = new Key("table-row");
    public static final Object COLUMN = new Key("table-column");
    public static final Object CELL_BACKGROUND = new Key("cell-background");
    public static final Object CELL_VALIGN = new Key("cell-valign");

    /** Vertical alignment of a cell's text. */
    public enum VerticalAlignment {
        TOP, CENTER, BOTTOM
    }

    private static final AtomicLong ROWS = new AtomicLong();

    private TableAttributes() {
    }

    /** A new row marker: equal only to itself. */
    public static Object newRow() {
        return new RowId(ROWS.incrementAndGet());
    }

    /** The table a paragraph belongs to, null if it is not in a table. */
    public static TableFormat table(AttributeSet paragraph) {
        return paragraph != null && paragraph.getAttribute(TABLE) instanceof TableFormat t ? t : null;
    }

    public static boolean inTable(AttributeSet paragraph) {
        return table(paragraph) != null;
    }

    public static Object row(AttributeSet paragraph) {
        return paragraph == null ? null : paragraph.getAttribute(ROW);
    }

    public static int column(AttributeSet paragraph) {
        return paragraph != null && paragraph.getAttribute(COLUMN) instanceof Integer c ? Math.max(0, c) : 0;
    }

    /** A cell's background, null for none. */
    public static Color background(AttributeSet paragraph) {
        return paragraph != null && paragraph.getAttribute(CELL_BACKGROUND) instanceof Color c ? c : null;
    }

    public static VerticalAlignment verticalAlignment(AttributeSet paragraph) {
        return paragraph != null && paragraph.getAttribute(CELL_VALIGN) instanceof VerticalAlignment v
                ? v : VerticalAlignment.TOP;
    }

    /** Puts a paragraph into a cell. */
    public static void set(MutableAttributeSet attributes, TableFormat table, Object row, int column) {
        attributes.addAttribute(TABLE, Objects.requireNonNull(table, "table"));
        attributes.addAttribute(ROW, Objects.requireNonNull(row, "row"));
        attributes.addAttribute(COLUMN, column);
    }

    /** Takes a paragraph out of its table, cell format included. */
    public static void clear(MutableAttributeSet attributes) {
        attributes.removeAttribute(TABLE);
        attributes.removeAttribute(ROW);
        attributes.removeAttribute(COLUMN);
        attributes.removeAttribute(CELL_BACKGROUND);
        attributes.removeAttribute(CELL_VALIGN);
    }

    /** Sets a cell's background; null removes it. */
    public static void setBackground(MutableAttributeSet attributes, Color color) {
        if (color == null) attributes.removeAttribute(CELL_BACKGROUND);
        else attributes.addAttribute(CELL_BACKGROUND, color);
    }

    /**
     * Whether two paragraphs belong to the same cell; two paragraphs outside tables count as the same (no)
     * cell.
     */
    public static boolean sameCell(AttributeSet a, AttributeSet b) {
        TableFormat ta = table(a);
        TableFormat tb = table(b);
        if (ta == null || tb == null) return ta == tb;
        return ta == tb && row(a) == row(b) && column(a) == column(b);
    }

    /** Whether two paragraphs belong to the same table row. */
    public static boolean sameRow(AttributeSet a, AttributeSet b) {
        TableFormat ta = table(a);
        return ta != null && ta == table(b) && row(a) == row(b);
    }

    private record RowId(long n) {
        @Override
        public boolean equals(Object o) {
            return this == o;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public String toString() {
            return "row-" + n;
        }
    }

    private record Key(String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
