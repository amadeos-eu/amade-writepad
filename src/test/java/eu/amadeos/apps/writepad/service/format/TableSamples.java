package eu.amadeos.apps.writepad.service.format;

import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.domain.TableFormat;
import eu.amadeos.apps.writepad.service.table.TableAttributes;
import eu.amadeos.apps.writepad.service.table.TableEditing;
import eu.amadeos.apps.writepad.service.table.Tables;

import javax.swing.text.BadLocationException;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A document with a table, and the checks every format's round trip makes. */
public final class TableSamples {
    public static final Color YELLOW = new Color(0xFFF2CC);

    private TableSamples() {
    }

    /**
     * "Before", a table of three columns (100, 140, 160 pt) and three rows with a repeated heading, a yellow
     * cell, a centred cell and a cell of two paragraphs, then "After".
     */
    public static DocumentContent document() {
        WritepadDocument doc = (WritepadDocument) Documents.newDocument();
        try {
            doc.insertString(0, "Before\nAfter", null);
            TableFormat format = TableFormat.of(new double[]{100, 140, 160}).withRepeatHeader(true);
            int at = TableEditing.insertTable(doc, 7, format, 3, true);
            String[] texts = {"Item", "Price", "Note", "Apples", "1.20", "fresh", "Pears", "2.10", "ripe"};
            javax.swing.text.SimpleAttributeSet bold = new javax.swing.text.SimpleAttributeSet();
            StyleConstants.setBold(bold, true);
            for (int i = texts.length - 1; i >= 0; i--) doc.insertString(at + i, texts[i], i < 3 ? bold : null);
            String text = doc.getText(0, doc.getLength());
            doc.insertString(text.indexOf("Pears") + 5, "\nWilliams", null);
            text = doc.getText(0, doc.getLength());
            int fresh = text.indexOf("fresh");
            TableEditing.setBackground(doc, fresh, fresh, YELLOW);
            int price = text.indexOf("2.10");
            TableEditing.setVerticalAlignment(doc, price, price, TableAttributes.VerticalAlignment.CENTER);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        return new DocumentContent(doc, DocumentSettings.DEFAULT);
    }

    /** The texts of the table's cells, rows by '/', cells by '|', paragraphs in a cell by '~'. */
    public static String grid(StyledDocument doc, Tables.Table t) {
        StringBuilder b = new StringBuilder();
        try {
            for (Tables.Row r : t.rows()) {
                if (!b.isEmpty()) b.append('/');
                for (int i = 0; i < r.cells().size(); i++) {
                    Tables.Cell c = r.cells().get(i);
                    if (i > 0) b.append('|');
                    b.append(doc.getText(c.start(), c.end() - c.start() - 1).replace('\n', '~'));
                }
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        return b.toString();
    }

    /** The one table of a document read back. */
    public static Tables.Table table(DocumentContent read) {
        List<Tables.Block> blocks = Tables.blocks(read.document());
        Tables.Table found = null;
        for (Tables.Block b : blocks) {
            if (b instanceof Tables.Table t) {
                assertTrue(found == null, "one table");
                found = t;
            }
        }
        assertNotNull(found, "a table");
        return found;
    }

    /**
     * Checks what a format kept of {@link #document()}.
     *
     * @param cellFormat whether the format keeps the background and vertical alignment of cells
     */
    public static void check(DocumentContent read, boolean cellFormat) {
        check(read, cellFormat, true);
    }

    /** @param repeat whether the format kept the heading row's repeat */
    public static void check(DocumentContent read, boolean cellFormat, boolean repeat) {
        StyledDocument doc = read.document();
        Tables.Table t = table(read);
        assertEquals("Item|Price|Note/Apples|1.20|fresh/Pears~Williams|2.10|ripe", grid(doc, t));
        TableFormat f = t.format();
        assertEquals(3, f.columns());
        assertEquals(100, f.width(0), 1);
        assertEquals(140, f.width(1), 1);
        assertEquals(160, f.width(2), 1);
        assertEquals(TableFormat.Lines.ALL, f.lines());
        assertEquals(0.5, f.lineWidth(), 0.06);
        assertEquals(repeat, f.repeatHeader(), "the heading repeats");
        assertEquals(TableFormat.DEFAULT_PADDING, f.padding(), 0.3);
        Tables.Cell item = t.rows().getFirst().cells().getFirst();
        assertTrue(StyleConstants.isBold(doc.getCharacterElement(item.start()).getAttributes()), "heading is bold");
        if (cellFormat) {
            assertEquals(TableEditing.HEADER_BACKGROUND, TableAttributes.background(item.first().getAttributes()));
            Tables.Cell fresh = t.rows().get(1).cells().get(2);
            assertEquals(YELLOW, TableAttributes.background(fresh.first().getAttributes()));
            Tables.Cell price = t.rows().get(2).cells().get(1);
            assertEquals(TableAttributes.VerticalAlignment.CENTER,
                    TableAttributes.verticalAlignment(price.first().getAttributes()));
        }
        try {
            String text = doc.getText(0, doc.getLength());
            assertTrue(text.startsWith("Before\n"), text);
            assertTrue(text.contains("After"), text);
            assertFalse(Tables.inTable(doc, 0));
            assertFalse(Tables.inTable(doc, text.indexOf("After")));
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }
}
