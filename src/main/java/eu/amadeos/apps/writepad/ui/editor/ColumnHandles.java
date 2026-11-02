package eu.amadeos.apps.writepad.ui.editor;

import eu.amadeos.apps.writepad.domain.TableFormat;
import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import eu.amadeos.apps.writepad.service.layout.PagedSectionView;
import eu.amadeos.apps.writepad.service.table.TableEditing;

import javax.swing.text.BadLocationException;
import java.awt.Cursor;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

/**
 * Dragging a table's column lines in the text: between two columns the line moves and the two columns
 * share their width differently; the table's right edge makes the last column wider or narrower. While the
 * mouse is down a dotted guide shows where the line goes; the widths change when it is let go, as one undo
 * step.
 */
final class ColumnHandles {
    /** How close (pixels) the mouse has to be to a line. */
    private static final int REACH = 3;

    private final PageEditor editor;
    private PagedSectionView.TableBox table;
    private int edge = -1;
    private double startX;
    private double dragX;

    ColumnHandles(PageEditor editor) {
        this.editor = editor;
    }

    private Rectangle allocation() {
        Insets i = editor.getInsets();
        return new Rectangle(i.left, i.top, editor.getWidth() - i.left - i.right,
                editor.getHeight() - i.top - i.bottom);
    }

    /** The table and line (1 = between the first and second column) at a point; null if none. */
    private Object[] lineAt(Point p) {
        PagedSectionView section = editor.section();
        if (section == null || !editor.isEditable()) return null;
        Rectangle a = allocation();
        for (PagedSectionView.TableBox box : section.tableBoxes()) {
            double[] edges = box.columns();
            for (PagedSectionView.RowBox row : box.rows()) {
                Point2D top = section.toScreen(edges[0], row.top(), a);
                Point2D bottom = section.toScreen(edges[0], row.bottom(), a);
                if (p.y < top.getY() || p.y > bottom.getY()) continue;
                for (int c = 1; c < edges.length; c++) {
                    double x = section.toScreen(edges[c], row.top(), a).getX();
                    if (Math.abs(x - p.x) <= REACH) return new Object[]{box, c};
                }
            }
        }
        return null;
    }

    /** The resize cursor over a column line, null elsewhere. */
    Cursor cursorAt(Point p) {
        return lineAt(p) != null ? Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR) : null;
    }

    /** A press on a column line starts a drag the caret must not see; true if it did. */
    boolean pressed(MouseEvent e) {
        Object[] hit = lineAt(e.getPoint());
        if (hit == null) return false;
        table = (PagedSectionView.TableBox) hit[0];
        edge = (Integer) hit[1];
        startX = e.getX();
        dragX = e.getX();
        editor.setGuide(dragX);
        return true;
    }

    boolean dragging() {
        return table != null;
    }

    void dragged(MouseEvent e) {
        dragX = e.getX();
        editor.setGuide(dragX);
    }

    /** Sets the new widths. */
    void released() {
        if (table == null) return;
        PagedSectionView.TableBox box = table;
        table = null;
        editor.setGuide(Double.NaN);
        double moved = (dragX - startX) / editor.pageLayout().zoom();
        if (Math.abs(moved) < 0.5 || !(editor.getDocument() instanceof WritepadDocument doc)) return;
        TableFormat format = box.format();
        double[] edges = box.columns();
        // Edges are scaled when the table is wider than the text column; widths are not.
        double scale = (edges[edges.length - 1] - edges[0]) / format.totalWidth();
        double delta = moved / scale;
        double[] w = format.widths();
        int left = edge - 1;
        if (edge < w.length) {
            double pair = w[left] + w[edge];
            double a = Math.max(TableFormat.MIN_COLUMN, Math.min(pair - TableFormat.MIN_COLUMN, w[left] + delta));
            w[left] = a;
            w[edge] = pair - a;
        } else {
            double others = format.totalWidth() - w[left];
            double max = Math.max(TableFormat.MIN_COLUMN, editor.pageLayout().contentWidth() / scale - others);
            w[left] = Math.max(TableFormat.MIN_COLUMN, Math.min(max, w[left] + delta));
        }
        TableFormat changed = format.withWidths(w);
        editor.groupEdit(() -> {
            try {
                TableEditing.setFormat(doc, box.offset(), changed);
            } catch (BadLocationException ex) {
                throw new IllegalStateException(ex);
            }
        });
    }
}
