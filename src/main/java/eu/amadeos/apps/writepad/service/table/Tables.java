package eu.amadeos.apps.writepad.service.table;

import eu.amadeos.apps.writepad.domain.TableFormat;

import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;
import java.util.ArrayList;
import java.util.List;

/**
 * The tables of a document as structure: rows of cells of paragraphs, found from the paragraph attributes
 * (see {@link TableAttributes}). The readers of a structure are the writers of the file formats and the
 * table commands; the structure is a snapshot, valid until the document changes.
 */
public final class Tables {
    private Tables() {
    }

    /** A part of the document's flow: a paragraph outside tables, or a whole table. */
    public sealed interface Block permits Paragraph, Table {
        int start();

        int end();
    }

    /** A paragraph outside tables. */
    public record Paragraph(Element element) implements Block {
        @Override
        public int start() {
            return element.getStartOffset();
        }

        @Override
        public int end() {
            return element.getEndOffset();
        }
    }

    /** A cell: its column and its paragraphs (at least one). */
    public record Cell(int column, List<Element> paragraphs) {
        public int start() {
            return paragraphs.getFirst().getStartOffset();
        }

        /** End offset, after the last paragraph's line end. */
        public int end() {
            return paragraphs.getLast().getEndOffset();
        }

        /** The attributes of the cell: its first paragraph's. */
        public Element first() {
            return paragraphs.getFirst();
        }

        public boolean contains(int offset) {
            return offset >= start() && offset < end();
        }
    }

    /** A row: its marker and its cells in column order. */
    public record Row(Object id, List<Cell> cells) {
        public int start() {
            return cells.getFirst().start();
        }

        public int end() {
            return cells.getLast().end();
        }

        public boolean contains(int offset) {
            return offset >= start() && offset < end();
        }

        /** The cell of a column; the last one for a column beyond the row's end. */
        public Cell cell(int column) {
            for (Cell c : cells) {
                if (c.column() >= column) return c;
            }
            return cells.getLast();
        }
    }

    /** A table: its format and its rows (at least one). */
    public record Table(TableFormat format, List<Row> rows) implements Block {
        @Override
        public int start() {
            return rows.getFirst().start();
        }

        @Override
        public int end() {
            return rows.getLast().end();
        }

        public int rowIndex(int offset) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).contains(offset)) return i;
            }
            return -1;
        }

        public Row row(int offset) {
            int i = rowIndex(offset);
            return i < 0 ? null : rows.get(i);
        }

        public Cell cell(int offset) {
            Row r = row(offset);
            if (r == null) return null;
            for (Cell c : r.cells()) {
                if (c.contains(offset)) return c;
            }
            return null;
        }

        /** The most cells any row has. */
        public int columns() {
            int n = 0;
            for (Row r : rows) n = Math.max(n, r.cells().getLast().column() + 1);
            return Math.max(n, 1);
        }

        /** The cells in reading order. */
        public List<Cell> cells() {
            List<Cell> all = new ArrayList<>();
            for (Row r : rows) all.addAll(r.cells());
            return all;
        }
    }

    /** The table at an offset, null if the offset is not in a table. */
    public static Table at(Document document, int offset) {
        Element root = document.getDefaultRootElement();
        int index = root.getElementIndex(offset);
        TableFormat format = TableAttributes.table(root.getElement(index).getAttributes());
        if (format == null) return null;
        int first = index;
        while (first > 0 && TableAttributes.table(root.getElement(first - 1).getAttributes()) == format) first--;
        int last = index;
        while (last + 1 < root.getElementCount()
                && TableAttributes.table(root.getElement(last + 1).getAttributes()) == format) {
            last++;
        }
        return build(root, first, last);
    }

    /** The document's flow as paragraphs and tables. */
    public static List<Block> blocks(Document document) {
        Element root = document.getDefaultRootElement();
        List<Block> blocks = new ArrayList<>();
        int i = 0;
        while (i < root.getElementCount()) {
            TableFormat format = TableAttributes.table(root.getElement(i).getAttributes());
            if (format == null) {
                blocks.add(new Paragraph(root.getElement(i)));
                i++;
                continue;
            }
            int last = i;
            while (last + 1 < root.getElementCount()
                    && TableAttributes.table(root.getElement(last + 1).getAttributes()) == format) {
                last++;
            }
            blocks.add(build(root, i, last));
            i = last + 1;
        }
        return blocks;
    }

    /** Whether the document has a table at all. */
    public static boolean any(Document document) {
        Element root = document.getDefaultRootElement();
        for (int i = 0; i < root.getElementCount(); i++) {
            if (TableAttributes.inTable(root.getElement(i).getAttributes())) return true;
        }
        return false;
    }

    /** The paragraphs root.getElement(first..last) grouped into rows (by row marker) and cells (by column). */
    private static Table build(Element root, int first, int last) {
        TableFormat format = TableAttributes.table(root.getElement(first).getAttributes());
        List<Row> rows = new ArrayList<>();
        Object rowId = null;
        List<Cell> cells = null;
        List<Element> paragraphs = null;
        int column = -1;
        for (int i = first; i <= last; i++) {
            Element p = root.getElement(i);
            Object id = TableAttributes.row(p.getAttributes());
            int c = TableAttributes.column(p.getAttributes());
            if (cells == null || id != rowId) {
                cells = new ArrayList<>();
                rows.add(new Row(id, cells));
                rowId = id;
                paragraphs = null;
            }
            if (paragraphs == null || c != column) {
                paragraphs = new ArrayList<>();
                cells.add(new Cell(c, paragraphs));
                column = c;
            }
            paragraphs.add(p);
        }
        List<Row> frozen = new ArrayList<>();
        for (Row r : rows) {
            List<Cell> fc = new ArrayList<>();
            for (Cell c : r.cells()) fc.add(new Cell(c.column(), List.copyOf(c.paragraphs())));
            frozen.add(new Row(r.id(), List.copyOf(fc)));
        }
        return new Table(format, List.copyOf(frozen));
    }

    /** Whether the paragraph at an offset is in a table. */
    public static boolean inTable(StyledDocument document, int offset) {
        return TableAttributes.inTable(document.getParagraphElement(offset).getAttributes());
    }
}
