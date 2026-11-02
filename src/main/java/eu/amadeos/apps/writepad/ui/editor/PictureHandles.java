package eu.amadeos.apps.writepad.ui.editor;

import eu.amadeos.apps.writepad.domain.Picture;
import eu.amadeos.apps.writepad.service.layout.FloatingPicture;
import eu.amadeos.apps.writepad.service.layout.PagedSectionView;
import eu.amadeos.apps.writepad.service.picture.PictureAttributes;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;

/**
 * The selected picture's frame and its eight handles, and the mouse on pictures: a click selects a picture
 * (floating ones too, which have no place in the line to click), dragging a corner handle resizes it with its
 * proportions (Shift: freely), an edge handle changes width or height alone. The new size is set when the
 * mouse is let go, as one undo step; while dragging, the frame shows it.
 */
final class PictureHandles {
    /** Half the size of a handle in pixels. */
    private static final int HANDLE = 4;
    private static final Color FRAME = new Color(0x2F, 0x6F, 0xB8);

    /** Handle positions as directions: -1 left/top, 0 middle, 1 right/bottom. */
    private static final int[][] HANDLES = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};

    private final PageEditor editor;
    /** The drag in progress: the handle, where it started, the picture's offset and its starting box. */
    private int[] dragHandle;
    private Point dragStart;
    private int dragOffset;
    private Rectangle startBox;
    private Rectangle dragBox;

    PictureHandles(PageEditor editor) {
        this.editor = editor;
    }

    /** The selected picture's box on the screen, null if no picture is selected. */
    Rectangle selectedBox() {
        int offset = PictureEditing.selectedPicture(editor);
        return offset < 0 ? null : screenBox(offset);
    }

    Rectangle screenBox(int offset) {
        PagedSectionView section = editor.section();
        if (section == null) return null;
        Rectangle2D box = section.pictureBox(offset);
        return box == null ? null : section.toScreen(box, allocation());
    }

    private Rectangle allocation() {
        Insets i = editor.getInsets();
        return new Rectangle(i.left, i.top, editor.getWidth() - i.left - i.right,
                editor.getHeight() - i.top - i.bottom);
    }

    void paint(Graphics2D g) {
        Rectangle box = dragBox != null ? dragBox : selectedBox();
        if (box == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setColor(FRAME);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4f, 3f}, 0f));
            g2.drawRect(box.x, box.y, box.width, box.height);
            g2.setStroke(new BasicStroke(1f));
            for (int[] h : HANDLES) {
                Point p = handlePoint(box, h);
                g2.setColor(Color.WHITE);
                g2.fillRect(p.x - HANDLE, p.y - HANDLE, 2 * HANDLE, 2 * HANDLE);
                g2.setColor(FRAME);
                g2.drawRect(p.x - HANDLE, p.y - HANDLE, 2 * HANDLE, 2 * HANDLE);
            }
        } finally {
            g2.dispose();
        }
    }

    private static Point handlePoint(Rectangle box, int[] h) {
        return new Point(box.x + (h[0] + 1) * box.width / 2, box.y + (h[1] + 1) * box.height / 2);
    }

    private int[] handleAt(Point p) {
        Rectangle box = selectedBox();
        if (box == null) return null;
        for (int[] h : HANDLES) {
            Point hp = handlePoint(box, h);
            if (Math.abs(hp.x - p.x) <= HANDLE + 1 && Math.abs(hp.y - p.y) <= HANDLE + 1) return h;
        }
        return null;
    }

    /** The offset of the picture under a point, -1 if none. */
    int pictureAt(Point p) {
        PagedSectionView section = editor.section();
        if (section == null) return -1;
        FloatingPicture f = section.floatingPictureAt(p.x, p.y, allocation());
        if (f != null) return f.offset();
        int pos = editor.viewToModel2D(p);
        for (int candidate : new int[]{pos, pos - 1}) {
            if (candidate < 0 || PictureAttributes.at(editor.getStyledDocument(), candidate) == null) continue;
            Rectangle box = screenBox(candidate);
            if (box != null && box.contains(p)) return candidate;
        }
        return -1;
    }

    // ---------------------------------------------------------------- mouse

    /**
     * Handles a press before the caret sees it. Returns true if the press started a resize, which the caret
     * must not see (it would start a text selection).
     */
    boolean pressed(MouseEvent e) {
        int[] handle = editor.isEditable() ? handleAt(e.getPoint()) : null;
        if (handle == null) return false;
        dragHandle = handle;
        dragStart = e.getPoint();
        dragOffset = PictureEditing.selectedPicture(editor);
        startBox = selectedBox();
        dragBox = new Rectangle(startBox);
        return true;
    }

    /** After the caret placed itself: a click on a picture selects it. */
    void clicked(MouseEvent e) {
        int offset = pictureAt(e.getPoint());
        if (offset >= 0 && !e.isShiftDown()) PictureEditing.select(editor, offset);
    }

    boolean dragging() {
        return dragHandle != null;
    }

    void dragged(MouseEvent e) {
        int dx = e.getX() - dragStart.x;
        int dy = e.getY() - dragStart.y;
        double w = startBox.width + dragHandle[0] * dx;
        double h = startBox.height + dragHandle[1] * dy;
        boolean corner = dragHandle[0] != 0 && dragHandle[1] != 0;
        if (corner && !e.isShiftDown()) {
            double aspect = (double) startBox.height / Math.max(1, startBox.width);
            double scale = Math.max(w / startBox.width, h / Math.max(1, startBox.height));
            w = startBox.width * scale;
            h = w * aspect;
        }
        w = Math.max(8, w);
        h = Math.max(8, h);
        int x = dragHandle[0] < 0 ? startBox.x + startBox.width - (int) w : startBox.x;
        int y = dragHandle[1] < 0 ? startBox.y + startBox.height - (int) h : startBox.y;
        dragBox = new Rectangle(x, y, (int) Math.round(w), (int) Math.round(h));
        editor.repaint();
    }

    /** Ends a resize: the picture gets the size of the frame. */
    void released() {
        if (dragHandle == null) return;
        Picture p = PictureAttributes.at(editor.getStyledDocument(), dragOffset);
        if (p != null && !dragBox.equals(startBox)) {
            double zoom = editor.pageLayout().zoom();
            double border = 2 * p.border().width();
            double maxWidth = editor.pageLayout().contentWidth();
            double w = Math.min(maxWidth, dragBox.width / zoom - border);
            double h = dragBox.height / zoom - border;
            if (dragBox.width / zoom - border > maxWidth) h = h * maxWidth / (dragBox.width / zoom - border);
            editor.resizePicture(dragOffset, p.withSize(w, h));
        }
        dragHandle = null;
        dragBox = null;
        editor.repaint();
    }

    /** The resize cursor over a handle, the arrow over a picture, null elsewhere. */
    Cursor cursorAt(Point p) {
        int[] h = editor.isEditable() ? handleAt(p) : null;
        if (h != null) {
            int type = switch (h[0] * 3 + h[1]) {
                case -4 -> Cursor.NW_RESIZE_CURSOR;
                case -3 -> Cursor.W_RESIZE_CURSOR;
                case -2 -> Cursor.SW_RESIZE_CURSOR;
                case -1 -> Cursor.N_RESIZE_CURSOR;
                case 1 -> Cursor.S_RESIZE_CURSOR;
                case 2 -> Cursor.NE_RESIZE_CURSOR;
                case 3 -> Cursor.E_RESIZE_CURSOR;
                default -> Cursor.SE_RESIZE_CURSOR;
            };
            return Cursor.getPredefinedCursor(type);
        }
        return pictureAt(p) >= 0 ? Cursor.getDefaultCursor() : null;
    }
}
