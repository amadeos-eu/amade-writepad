package eu.amadeos.apps.writepad.service.table;

import eu.amadeos.apps.writepad.domain.TableFormat;

/**
 * The lines of a table as the file formats store them: per cell edge. Writing turns the table's preset into
 * the edges of each cell; reading guesses the preset back from the edges of the cells.
 */
public final class TableLines {
    private TableLines() {
    }

    /** Which edges of a cell have a line. */
    public record Edges(boolean top, boolean right, boolean bottom, boolean left) {
        public boolean any() {
            return top || right || bottom || left;
        }
    }

    /** The edges of the cell in row r and column c of a table of the given size. */
    public static Edges edges(TableFormat.Lines lines, int r, int rows, int c, int columns) {
        boolean firstRow = r == 0;
        boolean lastRow = r == rows - 1;
        boolean firstColumn = c == 0;
        boolean lastColumn = c >= columns - 1;
        return switch (lines) {
            case ALL -> new Edges(true, true, true, true);
            case OUTER -> new Edges(firstRow, lastColumn, lastRow, firstColumn);
            case ROWS -> new Edges(true, false, true, false);
            case NONE -> new Edges(false, false, false, false);
        };
    }

    /**
     * The preset that fits lines found in a file: lines between columns mean lines everywhere; lines between
     * rows only mean rows, or everywhere if the sides have lines too; lines only around mean the outline.
     *
     * @param innerVertical   a line between two columns
     * @param innerHorizontal a line between two rows
     * @param outerVertical   a line at the left or right edge of the table
     * @param outerHorizontal a line at the top or bottom edge of the table
     */
    public static TableFormat.Lines guess(boolean innerVertical, boolean innerHorizontal, boolean outerVertical,
                                          boolean outerHorizontal) {
        if (innerVertical) return TableFormat.Lines.ALL;
        if (innerHorizontal) return outerVertical ? TableFormat.Lines.ALL : TableFormat.Lines.ROWS;
        if (outerVertical || outerHorizontal) return TableFormat.Lines.OUTER;
        return TableFormat.Lines.NONE;
    }
}
