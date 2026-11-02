package eu.amadeos.apps.writepad.service.table;

import eu.amadeos.apps.writepad.domain.TableFormat;
import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import org.junit.jupiter.api.Test;

import javax.swing.event.UndoableEditEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.undo.UndoManager;
import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableEditingTest {

    /** "Before\nAfter" with a 3x2 table between, cells A1..C2. */
    private static WritepadDocument sample() throws BadLocationException {
        WritepadDocument doc = new WritepadDocument();
        doc.insertString(0, "Before\nAfter", null);
        int at = TableEditing.insertTable(doc, 7, TableFormat.of(3, 300), 2, true);
        assertEquals(7, at);
        String[] texts = {"A1", "B1", "C1", "A2", "B2", "C2"};
        for (int i = texts.length - 1; i >= 0; i--) doc.insertString(at + i, texts[i], null);
        return doc;
    }

    private static String text(WritepadDocument doc) throws BadLocationException {
        return doc.getText(0, doc.getLength());
    }

    /** The table as rows of cell texts joined by '|', rows by '/'. */
    private static String grid(WritepadDocument doc, int offset) throws BadLocationException {
        Tables.Table t = Tables.at(doc, offset);
        if (t == null) return null;
        StringBuilder b = new StringBuilder();
        for (Tables.Row r : t.rows()) {
            if (!b.isEmpty()) b.append('/');
            for (int i = 0; i < r.cells().size(); i++) {
                Tables.Cell c = r.cells().get(i);
                assertEquals(i, c.column(), "columns numbered in order");
                if (i > 0) b.append('|');
                b.append(doc.getText(c.start(), c.end() - c.start() - 1).replace('\n', '~'));
            }
        }
        return b.toString();
    }

    @Test
    void insertsATableBetweenTwoParagraphs() throws BadLocationException {
        WritepadDocument doc = sample();
        assertEquals("Before\nA1\nB1\nC1\nA2\nB2\nC2\nAfter", text(doc));
        assertEquals("A1|B1|C1/A2|B2|C2", grid(doc, 8));
        assertFalse(Tables.inTable(doc, 0));
        assertFalse(Tables.inTable(doc, doc.getLength() - 1));
        Element header = doc.getParagraphElement(7);
        assertEquals(TableEditing.HEADER_BACKGROUND, TableAttributes.background(header.getAttributes()));
        assertTrue(StyleConstants.isBold(doc.getCharacterElement(9).getAttributes()), "the header's line ends are bold");
        assertFalse(StyleConstants.isBold(doc.getCharacterElement(18).getAttributes()));
        assertEquals(2, Tables.blocks(doc).stream().filter(b -> b instanceof Tables.Paragraph).count());
    }

    @Test
    void insertingInTheMiddleOfAParagraphSplitsIt() throws BadLocationException {
        WritepadDocument doc = new WritepadDocument();
        doc.insertString(0, "LeftRight", null);
        int at = TableEditing.insertTable(doc, 4, TableFormat.of(2, 200), 1, false);
        assertEquals(5, at);
        assertEquals("Left\n\n\nRight", text(doc));
        assertEquals("|", grid(doc, 5));
        assertFalse(Tables.inTable(doc, 0));
        assertFalse(Tables.inTable(doc, 8));
    }

    @Test
    void deletingAcrossCellsKeepsTheCells() throws BadLocationException {
        WritepadDocument doc = sample();
        // From "1" of A1 to "B" of B2: everything in between goes, the cells stay.
        int from = text(doc).indexOf("A1") + 1;
        int to = text(doc).indexOf("B2") + 1;
        doc.remove(from, to - from);
        assertEquals("A||/|2|C2", grid(doc, 7));
        // Backspace at the start of a cell does nothing.
        int b2 = Tables.at(doc, 7).rows().get(1).cells().get(1).start();
        doc.remove(b2 - 1, 1);
        assertEquals("A||/|2|C2", grid(doc, 7));
        // Delete at the end of the paragraph before the table does nothing either.
        doc.remove(6, 1);
        assertEquals("Before\n", text(doc).substring(0, 7));
    }

    @Test
    void deletingAcrossCellsIsOneUndoStep() throws BadLocationException {
        WritepadDocument doc = sample();
        UndoManager undo = new UndoManager();
        doc.addUndoableEditListener(undo);
        String before = text(doc);
        doc.remove(2, text(doc).indexOf("After") + 2 - 2);
        assertEquals("Be\n\n\n\n\n\n\nter", text(doc));
        undo.undo();
        assertEquals(before, text(doc));
        assertEquals("A1|B1|C1/A2|B2|C2", grid(doc, 8));
    }

    @Test
    void enterInACellMakesAParagraphInTheSameCell() throws BadLocationException {
        WritepadDocument doc = sample();
        int b1 = text(doc).indexOf("B1") + 1;
        doc.insertString(b1, "\n", null);
        assertEquals("A1|B~1|C1/A2|B2|C2", grid(doc, 8));
    }

    @Test
    void insertsAndDeletesRows() throws BadLocationException {
        WritepadDocument doc = sample();
        int a2 = text(doc).indexOf("A2");
        int at = TableEditing.insertRow(doc, a2, false);
        assertEquals(a2, at);
        assertEquals("A1|B1|C1/||/A2|B2|C2", grid(doc, 8));
        at = TableEditing.insertRow(doc, text(doc).indexOf("C2"), true);
        assertEquals("A1|B1|C1/||/A2|B2|C2/||", grid(doc, 8));
        assertFalse(Tables.inTable(doc, doc.getLength()), "the paragraph after the table stays outside");
        assertEquals("After", text(doc).substring(text(doc).lastIndexOf('\n') + 1));
        TableEditing.deleteRow(doc, at);
        TableEditing.deleteRow(doc, text(doc).indexOf("A1"));
        assertEquals("||/A2|B2|C2", grid(doc, 8));
        assertFalse(Tables.inTable(doc, doc.getLength()));
        assertFalse(Tables.inTable(doc, 0));
    }

    @Test
    void newRowsDoNotShareTheHeaderRowMarker() throws BadLocationException {
        WritepadDocument doc = sample();
        TableEditing.insertRow(doc, 8, true);
        Tables.Table t = Tables.at(doc, 8);
        assertEquals(3, t.rows().size());
        assertEquals(3, t.rows().get(1).cells().size());
    }

    @Test
    void insertsAndDeletesColumns() throws BadLocationException {
        WritepadDocument doc = sample();
        int at = TableEditing.insertColumn(doc, text(doc).indexOf("B2"), true);
        assertEquals("A1|B1||C1/A2|B2||C2", grid(doc, 8));
        assertEquals(4, Tables.at(doc, 8).format().columns());
        assertEquals(text(doc).indexOf("C2") - 1, at);
        assertEquals(50, Tables.at(doc, 8).format().width(1), 1e-9);
        assertEquals(50, Tables.at(doc, 8).format().width(2), 1e-9);
        TableEditing.insertColumn(doc, text(doc).indexOf("A1"), false);
        assertEquals("|A1|B1||C1/|A2|B2||C2", grid(doc, 8));
        TableEditing.deleteColumn(doc, text(doc).indexOf("B1"));
        assertEquals("|A1||C1/|A2||C2", grid(doc, 8));
        TableEditing.deleteColumn(doc, 7);
        assertEquals("A1||C1/A2||C2", grid(doc, 8));
        TableEditing.deleteColumn(doc, text(doc).indexOf("A1") + 3);
        assertEquals("A1|C1/A2|C2", grid(doc, 8));
        assertEquals(2, Tables.at(doc, 8).format().columns());
        assertFalse(Tables.inTable(doc, doc.getLength()));
    }

    @Test
    void deletesTheTable() throws BadLocationException {
        WritepadDocument doc = sample();
        TableEditing.deleteTable(doc, 10);
        assertEquals("Before\nAfter", text(doc));
        assertFalse(Tables.any(doc));
    }

    @Test
    void changingTheFormatChangesEveryParagraph() throws BadLocationException {
        WritepadDocument doc = sample();
        TableFormat wide = Tables.at(doc, 8).format().withLines(TableFormat.Lines.OUTER);
        TableEditing.setFormat(doc, 12, wide);
        Tables.Table t = Tables.at(doc, 8);
        assertSame(wide, t.format());
        assertEquals(6, t.cells().size());
    }

    @Test
    void changesReachTheLastEmptyCell() throws BadLocationException {
        WritepadDocument doc = new WritepadDocument();
        doc.insertString(0, "Before\nAfter", null);
        int at = TableEditing.insertTable(doc, 7, TableFormat.of(2, 200), 2, false);
        TableFormat wider = Tables.at(doc, at).format().withWidths(new double[]{150, 50});
        TableEditing.setFormat(doc, at, wider);
        assertEquals(4, Tables.at(doc, at).cells().size());
        assertEquals(3, Tables.blocks(doc).size());
        int last = Tables.at(doc, at).cells().getLast().start();
        TableEditing.setBackground(doc, last, last, Color.PINK);
        assertEquals(Color.PINK, TableAttributes.background(doc.getParagraphElement(last).getAttributes()));
        assertNull(TableAttributes.background(doc.getParagraphElement(last + 1).getAttributes()));
        TableEditing.insertColumn(doc, last, true);
        assertEquals("||/||", grid(doc, at));
    }

    @Test
    void cellBackgrounds() throws BadLocationException {
        WritepadDocument doc = sample();
        int a2 = text(doc).indexOf("A2");
        int b2 = text(doc).indexOf("B2");
        TableEditing.setBackground(doc, a2, b2 + 1, Color.YELLOW);
        List<Tables.Cell> row = Tables.at(doc, 8).rows().get(1).cells();
        assertEquals(Color.YELLOW, TableAttributes.background(row.get(0).first().getAttributes()));
        assertEquals(Color.YELLOW, TableAttributes.background(row.get(1).first().getAttributes()));
        assertNull(TableAttributes.background(row.get(2).first().getAttributes()));
        TableEditing.setBackground(doc, a2, a2, null);
        row = Tables.at(doc, 8).rows().get(1).cells();
        assertNull(TableAttributes.background(row.get(0).first().getAttributes()));
        assertEquals("A1|B1|C1/A2|B2|C2", grid(doc, 8));
    }

    @Test
    void tabMovesBetweenCells() throws BadLocationException {
        WritepadDocument doc = sample();
        int b1 = text(doc).indexOf("B1");
        assertEquals(text(doc).indexOf("C1"), TableEditing.nextCell(doc, b1 + 1));
        assertEquals(text(doc).indexOf("A1"), TableEditing.previousCell(doc, b1));
        assertEquals(-1, TableEditing.nextCell(doc, text(doc).indexOf("C2")));
        assertEquals(-1, TableEditing.previousCell(doc, 7));
    }

    @Test
    void aParagraphCanGoBeforeATableAtTheStart() throws BadLocationException {
        WritepadDocument doc = new WritepadDocument();
        TableEditing.insertTable(doc, 0, TableFormat.of(2, 200), 1, false);
        doc.insertString(0, "X", null);
        assertTrue(Tables.inTable(doc, 0));
        int at = TableEditing.paragraphBefore(doc, 1);
        assertEquals(0, at);
        assertFalse(Tables.inTable(doc, 0));
        assertEquals("X|", grid(doc, 1));
    }

    @Test
    void structuralChangesAreSingleUndoSteps() throws BadLocationException {
        WritepadDocument doc = sample();
        int[] events = new int[1];
        doc.addUndoableEditListener((UndoableEditEvent e) -> events[0]++);
        TableEditing.insertColumn(doc, 8, true);
        assertEquals(1, events[0]);
        assertNotNull(Tables.at(doc, 8));
    }
}
