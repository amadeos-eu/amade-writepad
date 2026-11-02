package eu.amadeos.apps.writepad.service.layout;

import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.domain.TableFormat;
import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import eu.amadeos.apps.writepad.service.table.TableEditing;
import eu.amadeos.apps.writepad.service.table.Tables;
import eu.amadeos.apps.writepad.ui.editor.PageEditor;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableLayoutTest {

    interface Setup {
        void fill(WritepadDocument doc) throws BadLocationException;
    }

    private static PageEditor editor(Setup setup) throws Exception {
        AtomicReference<PageEditor> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                PageEditor editor = new PageEditor(new PageLayout(DocumentSettings.DEFAULT, 1));
                setup.fill((WritepadDocument) editor.getDocument());
                for (int i = 0; i < 3; i++) {
                    editor.setSize(editor.getPreferredSize());
                    editor.layOut();
                }
                ref.set(editor);
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
        });
        return ref.get();
    }

    private static Rectangle2D caret(PageEditor editor, int offset) throws Exception {
        AtomicReference<Rectangle2D> r = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                r.set(editor.modelToView2D(offset));
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
        });
        return r.get();
    }

    private static int offset(PageEditor editor, double x, double y) throws Exception {
        AtomicReference<Integer> r = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> r.set(editor.viewToModel2D(new java.awt.geom.Point2D.Double(x, y))));
        return r.get();
    }

    private static String text(PageEditor editor) throws BadLocationException {
        return editor.getDocument().getText(0, editor.getDocument().getLength());
    }

    @Test
    void cellsSitSideBySide() throws Exception {
        PageEditor editor = editor(doc -> {
            doc.insertString(0, "Before\nAfter", null);
            int at = TableEditing.insertTable(doc, 7, TableFormat.of(3, 300), 2, false);
            String[] texts = {"A1", "B1", "C1", "A2", "B2", "C2"};
            for (int i = texts.length - 1; i >= 0; i--) doc.insertString(at + i, texts[i], null);
        });
        String t = text(editor);
        Rectangle2D a1 = caret(editor, t.indexOf("A1"));
        Rectangle2D b1 = caret(editor, t.indexOf("B1"));
        Rectangle2D c1 = caret(editor, t.indexOf("C1"));
        Rectangle2D a2 = caret(editor, t.indexOf("A2"));
        Rectangle2D after = caret(editor, t.indexOf("After"));
        Rectangle2D before = caret(editor, 0);
        double zoom = editor.pageLayout().zoom();
        assertEquals(a1.getY(), b1.getY(), 0.5);
        assertEquals(a1.getY(), c1.getY(), 0.5);
        assertEquals(100 * zoom, b1.getX() - a1.getX(), 1.5);
        assertEquals(100 * zoom, c1.getX() - b1.getX(), 1.5);
        assertEquals(a1.getX(), a2.getX(), 0.5);
        assertTrue(a2.getY() > a1.getY());
        assertTrue(after.getY() > a2.getY());
        assertEquals(before.getX(), after.getX(), 0.5);
        // Clicking into B2 puts the caret there.
        int hit = offset(editor, b1.getX() + 3, a2.getCenterY());
        assertEquals(t.indexOf("B2"), hit, 1);
    }

    @Test
    void longTablesBreakBetweenRowsAndRepeatTheHeader() throws Exception {
        PageEditor editor = editor(doc -> {
            TableFormat format = TableFormat.of(2, 300).withRepeatHeader(true);
            TableEditing.insertTable(doc, 0, format, 80, true);
        });
        PagedSectionView section = editor.section();
        List<PagedSectionView.TableBox> boxes = section.tableBoxes();
        assertEquals(1, boxes.size());
        PagedSectionView.TableBox box = boxes.getFirst();
        assertTrue(section.pageCount() >= 2);
        PageLayout layout = editor.pageLayout();
        long repeats = box.rows().stream().filter(PagedSectionView.RowBox::repeat).count();
        assertEquals(section.pageCount() - 1, repeats);
        for (PagedSectionView.RowBox row : box.rows()) {
            int page = layout.pageAt(row.top());
            assertTrue(row.bottom() <= layout.contentBottom(page), "no row crosses a page");
            if (row.repeat()) assertEquals(layout.contentTop(page), row.top());
        }
        assertEquals(80, Tables.at(editor.getDocument(), 0).rows().size());
    }

    // --8<-- [start:clicks]
    /** A click in a cell finds that cell, in a table narrower and in one wider than the text (drawn narrower). */
    @Test
    void clicksFindTheirCell() throws Exception {
        for (double width : new double[]{300, 700}) {
            PageEditor editor = editor(doc -> {
                doc.insertString(0, "Before\nAfter", null);
                int at = TableEditing.insertTable(doc, 7, TableFormat.of(4, width), 1, false);
                String[] texts = {"A1", "B1", "C1", "D1"};
                for (int i = texts.length - 1; i >= 0; i--) doc.insertString(at + i, texts[i], null);
            });
            String t = text(editor);
            for (String cell : new String[]{"A1", "B1", "C1", "D1"}) {
                Rectangle2D r = caret(editor, t.indexOf(cell) + 2);
                int found = offset(editor, r.getX() + 1, r.getCenterY());
                assertEquals(t.indexOf(cell) + 2, found, cell + " in a table " + width + " wide");
            }
        }
    }
    // --8<-- [end:clicks]
}
