package eu.amadeos.apps.writepad.ui.editor;

import eu.amadeos.apps.writepad.domain.Picture;
import eu.amadeos.apps.writepad.service.picture.PictureAttributes;
import eu.amadeos.apps.writepad.service.table.Tables;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.apps.writepad.service.layout.PageLayout;
import eu.amadeos.apps.writepad.service.layout.PagedEditorKit;
import eu.amadeos.apps.writepad.service.layout.PagedSectionView;

import javax.swing.BorderFactory;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.View;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * The text area of amade-writepad: styled text on white pages on a dark desk, in the colours of no theme
 * (paper is white everywhere), zoomable. 100 % means true size on a screen with the resolution the system
 * reports.
 */
public final class PageEditor extends JTextPane {
    /** Font of new text until documents bring their own styles. */
    public static final Font DEFAULT_FONT = new Font(Font.SERIF, Font.PLAIN, 12);

    private final PageLayout layout;
    private IntFunction<String> footer = page -> null;
    private double guide = Double.NaN;
    private final PictureHandles handles = new PictureHandles(this);
    private final ColumnHandles columns = new ColumnHandles(this);
    private PictureListener pictureListener = new PictureListener() {
    };
    /** Whether a picture was selected at the last caret update, to repaint its handles away. */
    private boolean pictureSelected;

    /** What the window does with pictures: open the Picture dialog, show the menu, group edits for undo. */
    public interface PictureListener {
        /** A double click on the picture at the offset. */
        default void open(int offset) {
        }

        /** A right click on the picture at the offset, at the mouse event's point. */
        default void menu(int offset, MouseEvent e) {
        }

        /** Runs a change of pictures (a resize, a paste) as one undo step. */
        default void group(Runnable change) {
            change.run();
        }

        /** A right click in a table cell, after the caret moved there; at the mouse event's point. */
        default void tableMenu(MouseEvent e) {
        }
    }

    public PageEditor(PageLayout layout) {
        this.layout = Objects.requireNonNull(layout, "layout");
        setEditorKit(new PagedEditorKit(layout));
        setHighlighter(new PagedHighlighter());
        setTransferHandler(new EditorTransferHandler());
        setMargin(new Insets(0, 0, 0, 0));
        AmadeUI.style(this, (editor, theme) -> {
            editor.setBackground(theme.shadow().darker());
            editor.setForeground(Color.BLACK);
            editor.setCaretColor(Color.BLACK);
            editor.setFont(DEFAULT_FONT);
            // The desk starts at the edge; the scroll pane draws the frame.
            editor.setBorder(BorderFactory.createEmptyBorder());
        });
        layout.addChangeListener(this::pageLayoutChanged);
        addCaretListener(e -> {
            boolean selected = PictureEditing.selectedPicture(this) >= 0;
            if (selected || pictureSelected) repaint();
            pictureSelected = selected;
        });
    }

    public void setPictureListener(PictureListener listener) {
        this.pictureListener = Objects.requireNonNull(listener, "listener");
    }

    /** Runs a change as one undo step, as the window's actions do. */
    public void groupEdit(Runnable change) {
        pictureListener.group(change);
    }

    void resizePicture(int offset, Picture resized) {
        groupEdit(() -> {
            PictureAttributes.replace(getStyledDocument(), offset, resized);
            PictureEditing.select(this, offset);
        });
    }

    // --8<-- [start:mouse]
    /**
     * Pictures first, then the caret: a press on a handle starts a resize that the caret never sees (it would
     * select text); a click on a picture selects it after the caret has placed itself; a double click opens
     * the Picture dialog, a right click its menu. A press on a table's column line starts moving it; a right
     * click in a table opens the table menu.
     */
    @Override
    protected void processMouseEvent(MouseEvent e) {
        switch (e.getID()) {
            case MouseEvent.MOUSE_PRESSED -> {
                if (e.getButton() == MouseEvent.BUTTON1 && (handles.pressed(e) || columns.pressed(e))) {
                    requestFocusInWindow();
                    return;
                }
                if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
                    int offset = handles.pictureAt(e.getPoint());
                    if (offset >= 0) {
                        requestFocusInWindow();
                        PictureEditing.select(this, offset);
                        pictureListener.menu(offset, e);
                        return;
                    }
                    int at = viewToModel2D(e.getPoint());
                    if (at >= 0 && Tables.inTable(getStyledDocument(), at)) {
                        requestFocusInWindow();
                        if (at < getSelectionStart() || at > getSelectionEnd()) setCaretPosition(at);
                        pictureListener.tableMenu(e);
                        return;
                    }
                }
                super.processMouseEvent(e);
                if (e.getButton() == MouseEvent.BUTTON1) {
                    int offset = handles.pictureAt(e.getPoint());
                    if (offset >= 0 && e.getClickCount() == 2) {
                        PictureEditing.select(this, offset);
                        pictureListener.open(offset);
                    } else {
                        handles.clicked(e);
                    }
                }
            }
            case MouseEvent.MOUSE_RELEASED -> {
                if (handles.dragging()) {
                    handles.released();
                    return;
                }
                if (columns.dragging()) {
                    columns.released();
                    return;
                }
                super.processMouseEvent(e);
            }
            default -> super.processMouseEvent(e);
        }
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e) {
        if (e.getID() == MouseEvent.MOUSE_DRAGGED && handles.dragging()) {
            handles.dragged(e);
            return;
        }
        if (e.getID() == MouseEvent.MOUSE_DRAGGED && columns.dragging()) {
            columns.dragged(e);
            return;
        }
        if (e.getID() == MouseEvent.MOUSE_MOVED) {
            Cursor cursor = handles.cursorAt(e.getPoint());
            if (cursor == null) cursor = columns.cursorAt(e.getPoint());
            setCursor(cursor != null ? cursor : Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        }
        super.processMouseMotionEvent(e);
    }
    // --8<-- [end:mouse]

    /** The selected picture's box on the screen, null if none is selected. */
    public Rectangle selectedPictureBox() {
        return handles.selectedBox();
    }

    /** Screen pixels per point for true size on this screen. */
    public static double screenScale() {
        try {
            return Toolkit.getDefaultToolkit().getScreenResolution() / 72.0;
        } catch (java.awt.HeadlessException e) {
            return 96 / 72.0;
        }
    }

    public PageLayout pageLayout() {
        return layout;
    }

    /**
     * Metrics in points with fractional advances, the same the zoomed and printed text is painted with.
     * Swing's text views measure lines, caret positions and clicks with the metrics of their component; with
     * the default whole-pixel advances every glyph is up to half a point off, and at the end of a line the
     * caret lands visibly beside the last character.
     */
    @Override
    public FontMetrics getFontMetrics(Font font) {
        return LayoutMetrics.of(font);
    }

    /**
     * Where the text column (the left page margin) is, in this component's pixels. Pages are centred when
     * the editor is wider than a page, as {@code PagedSectionView} paints them.
     */
    public double textColumnX() {
        double zoom = layout.zoom();
        double centring = Math.max(0, (getWidth() - layout.totalWidth() * zoom) / 2);
        return centring + (PageLayout.GAP + layout.left()) * zoom;
    }

    /** Shows a dotted vertical line at x (pixels) while the ruler drags a marker; NaN hides it. */
    public void setGuide(double x) {
        if (Double.compare(x, guide) == 0) return;
        guide = x;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        handles.paint((Graphics2D) g);
        if (Double.isNaN(guide)) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setColor(Color.GRAY);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{2f, 2f}, 0f));
            Rectangle clip = g2.getClipBounds();
            int top = clip == null ? 0 : clip.y;
            int bottom = clip == null ? getHeight() : clip.y + clip.height;
            int x = (int) Math.round(guide);
            g2.drawLine(x, top, x, bottom);
        } finally {
            g2.dispose();
        }
    }

    /** Text in the bottom margin of each page (0-based page index), e.g. page numbers. */
    public void setFooter(IntFunction<String> footer) {
        this.footer = Objects.requireNonNull(footer, "footer");
        PagedSectionView section = section();
        if (section != null) section.setFooter(footer);
        repaint();
    }

    /** The page view, or null before the UI has created it. */
    public PagedSectionView section() {
        if (getUI() == null) return null;
        View root = getUI().getRootView(this);
        if (root.getViewCount() > 0 && root.getView(0) instanceof PagedSectionView section) {
            section.setFooter(footer);
            return section;
        }
        return null;
    }

    /** Lays the views out now, as painting would; for printing and status display. */
    public PagedSectionView layOut() {
        PagedSectionView section = section();
        if (section == null) return null;
        View root = getUI().getRootView(this);
        root.setSize(Math.max(getWidth(), 1), Math.max(getHeight(), 1));
        return section;
    }

    public int pageCount() {
        PagedSectionView section = layOut();
        return section == null ? 1 : section.pageCount();
    }

    /** 0-based page of a document offset. */
    public int pageOf(int offset) {
        try {
            layOut();
            Rectangle2D r = modelToView2D(Math.min(offset, getDocument().getLength()));
            return r == null ? 0 : layout.pageAt(r.getY() / layout.zoom());
        } catch (BadLocationException e) {
            return 0;
        }
    }

    private void pageLayoutChanged() {
        PagedSectionView section = section();
        if (section != null) section.pageLayoutChanged();
        revalidate();
        repaint();
    }

    // The desk fills the viewport; pages are centred on it.

    /** Size of the pages at the current zoom; JEditorPane.getPreferredSize would ask the methods below. */
    private Dimension pagesSize() {
        return getUI().getPreferredSize(this);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        Container parent = SwingUtilities.getUnwrappedParent(this);
        return parent instanceof JViewport port && port.getWidth() > pagesSize().width;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        Container parent = SwingUtilities.getUnwrappedParent(this);
        return parent instanceof JViewport port && port.getHeight() > pagesSize().height;
    }
}
