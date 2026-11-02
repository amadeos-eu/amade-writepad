package eu.amadeos.apps.writepad.domain;

import java.awt.Color;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * How a table looks: its column widths, lines, cell padding, whether its first row repeats at the top of
 * every page it continues on, and where it sits between the margins.
 *
 * <p>A table format is immutable; the {@code with...} methods return changed copies. Every paragraph of a
 * table carries the same format object, and that object is what makes the paragraphs one table: two formats
 * are never equal unless they are the same object, so two tables next to each other stay two tables.
 */
public final class TableFormat {
    /** 0.1 cm, LibreOffice's cell padding. */
    public static final double DEFAULT_PADDING = Units.mmToPoints(1);
    public static final float DEFAULT_LINE_WIDTH = 0.5f;
    /** Columns are never narrower than this, padding included. */
    public static final double MIN_COLUMN = 12;

    /** Which lines are drawn. */
    public enum Lines {
        /** Around every cell. */
        ALL,
        /** Around the table only. */
        OUTER,
        /** Between the rows and above and below the table, none between the columns. */
        ROWS,
        /** None; the screen shows light guides that are not printed. */
        NONE
    }

    /** Where a table narrower than the text column sits. */
    public enum Alignment {
        LEFT, CENTER, RIGHT
    }

    private final double[] widths;
    private final Lines lines;
    private final float lineWidth;
    private final Color lineColor;
    private final double padding;
    private final boolean repeatHeader;
    private final Alignment alignment;

    private TableFormat(double[] widths, Lines lines, float lineWidth, Color lineColor, double padding,
                        boolean repeatHeader, Alignment alignment) {
        if (widths.length == 0) throw new IllegalArgumentException("A table has at least one column");
        this.widths = widths.clone();
        for (int i = 0; i < this.widths.length; i++) this.widths[i] = Math.max(MIN_COLUMN, this.widths[i]);
        this.lines = Objects.requireNonNull(lines, "lines");
        this.lineWidth = Math.max(0, lineWidth);
        this.lineColor = Objects.requireNonNull(lineColor, "lineColor");
        this.padding = Math.max(0, padding);
        this.repeatHeader = repeatHeader;
        this.alignment = Objects.requireNonNull(alignment, "alignment");
    }

    /** A table of equal columns filling the given width, lines around every cell, no repeated header. */
    public static TableFormat of(int columns, double width) {
        double[] w = new double[Math.max(1, columns)];
        Arrays.fill(w, width / w.length);
        return new TableFormat(w, Lines.ALL, DEFAULT_LINE_WIDTH, Color.BLACK, DEFAULT_PADDING, false,
                Alignment.LEFT);
    }

    /** A table with the given column widths in points and the default look. */
    public static TableFormat of(double[] widths) {
        return new TableFormat(widths, Lines.ALL, DEFAULT_LINE_WIDTH, Color.BLACK, DEFAULT_PADDING, false,
                Alignment.LEFT);
    }

    public int columns() {
        return widths.length;
    }

    /** Width of a column in points, padding included; columns beyond the last get the last one's width. */
    public double width(int column) {
        return widths[Math.max(0, Math.min(widths.length - 1, column))];
    }

    public double[] widths() {
        return widths.clone();
    }

    public double totalWidth() {
        double sum = 0;
        for (double w : widths) sum += w;
        return sum;
    }

    public Lines lines() {
        return lines;
    }

    /** Line width in points. */
    public float lineWidth() {
        return lineWidth;
    }

    public Color lineColor() {
        return lineColor;
    }

    /** Space between a cell's lines and its text, on all four sides, in points. */
    public double padding() {
        return padding;
    }

    /** Whether the first row repeats at the top of every page the table continues on. */
    public boolean repeatHeader() {
        return repeatHeader;
    }

    public Alignment alignment() {
        return alignment;
    }

    // ---------------------------------------------------------------- copies

    public TableFormat withWidths(double[] newWidths) {
        return new TableFormat(newWidths, lines, lineWidth, lineColor, padding, repeatHeader, alignment);
    }

    /** All columns as wide as the given total divided by their number. */
    public TableFormat withEqualWidths(double total) {
        double[] w = new double[widths.length];
        Arrays.fill(w, total / w.length);
        return withWidths(w);
    }

    /** The same column proportions at another total width. */
    public TableFormat withTotalWidth(double total) {
        double scale = total / totalWidth();
        double[] w = widths.clone();
        for (int i = 0; i < w.length; i++) w[i] *= scale;
        return withWidths(w);
    }

    public TableFormat withLines(Lines newLines) {
        return new TableFormat(widths, newLines, lineWidth, lineColor, padding, repeatHeader, alignment);
    }

    public TableFormat withLineWidth(float newWidth) {
        return new TableFormat(widths, lines, newWidth, lineColor, padding, repeatHeader, alignment);
    }

    public TableFormat withLineColor(Color newColor) {
        return new TableFormat(widths, lines, lineWidth, newColor, padding, repeatHeader, alignment);
    }

    public TableFormat withPadding(double newPadding) {
        return new TableFormat(widths, lines, lineWidth, lineColor, newPadding, repeatHeader, alignment);
    }

    public TableFormat withRepeatHeader(boolean repeat) {
        return new TableFormat(widths, lines, lineWidth, lineColor, padding, repeat, alignment);
    }

    public TableFormat withAlignment(Alignment newAlignment) {
        return new TableFormat(widths, lines, lineWidth, lineColor, padding, repeatHeader, newAlignment);
    }

    /** An equal copy that is a table of its own (for a pasted or split table). */
    public TableFormat copy() {
        return new TableFormat(widths, lines, lineWidth, lineColor, padding, repeatHeader, alignment);
    }

    /** Whether two formats look the same, although they are two tables. */
    public boolean sameLook(TableFormat other) {
        return other != null && Arrays.equals(widths, other.widths) && lines == other.lines
                && lineWidth == other.lineWidth && lineColor.equals(other.lineColor) && padding == other.padding
                && repeatHeader == other.repeatHeader && alignment == other.alignment;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "TableFormat[%s, %s %.2fpt, padding %.2f%s, %s]", Arrays.toString(widths),
                lines, lineWidth, padding, repeatHeader ? ", header" : "", alignment);
    }
}
