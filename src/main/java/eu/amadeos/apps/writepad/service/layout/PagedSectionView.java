package eu.amadeos.apps.writepad.service.layout;

import eu.amadeos.apps.writepad.domain.Picture;
import eu.amadeos.apps.writepad.domain.TableFormat;
import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import eu.amadeos.apps.writepad.service.picture.PictureAttributes;
import eu.amadeos.apps.writepad.service.table.TableAttributes;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.BoxView;
import javax.swing.text.Caret;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.View;
import javax.swing.event.DocumentEvent;
import javax.swing.text.ViewFactory;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * The document as a stack of pages. Paragraphs are laid out one after the other in layout space (points,
 * see {@link PageLayout}); each paragraph moves lines that would cross a page boundary to the next page.
 *
 * <p>The view also applies the zoom: everything outside (component size, caret, mouse) is in screen pixels,
 * everything inside (paragraphs, lines, glyphs) in points. {@link #paint}, {@link #modelToView},
 * {@link #viewToModel} and the preferred size translate between the two. Pages are centred horizontally
 * when the window is wider than the page.
 */
public final class PagedSectionView extends BoxView {
    private static final Color PAPER = Color.WHITE;
    private static final Color PAGE_EDGE = new Color(0x606060);
    private static final Color PAGE_SHADOW = new Color(0, 0, 0, 90);
    private static final Color FOOTER_TEXT = new Color(0x707070);

    private final PageLayout layout;
    private int pageCount = 1;
    private IntFunction<String> footer = page -> null;
    /** The floating pictures of the last layout. */
    private List<FloatingPicture> floating = List.of();
    /** Per child, from the last minor layout: the left and right edge of its table cell; NaN outside tables. */
    private double[] cellLeft = new double[0];
    private double[] cellRight = new double[0];
    /** Per child, from the last major layout: the top of its table row, its own top outside tables. */
    private int[] rowTop = new int[0];
    /** The tables of the last layout, for painting and for the column handles. */
    private List<TableBox> tables = List.of();
    /** Whether paint draws for the screen: light guides where a table has no lines. */
    private boolean screen;

    PagedSectionView(Element element, PageLayout layout) {
        super(element, Y_AXIS);
        this.layout = layout;
        applyInsets();
    }

    public PageLayout pageLayout() {
        return layout;
    }

    /** Pages of the last layout. */
    public int pageCount() {
        return pageCount;
    }

    /** Text for the bottom margin of a page (0-based index), e.g. "1 / 3"; null for none. */
    public void setFooter(IntFunction<String> footer) {
        this.footer = footer;
    }

    /** Lays the document out again after the paper, orientation or margins changed. */
    public void pageLayoutChanged() {
        applyInsets();
        for (int i = 0; i < getViewCount(); i++) {
            View child = getView(i);
            if (child instanceof PagedParagraphView paragraph) {
                paragraph.setStartY(-1);
                paragraph.setFloats(List.of(), false);
            }
        }
        layoutChanged(X_AXIS);
        layoutChanged(Y_AXIS);
        preferenceChanged(null, true, true);
    }

    private void applyInsets() {
        setInsets((short) 0, (short) (PageLayout.GAP + layout.left()), (short) 0,
                (short) (PageLayout.GAP + layout.right()));
    }

    // ---------------------------------------------------------------- layout (points)

    @Override
    public void setSize(float width, float height) {
        // The page, not the window, decides the width of the text.
        super.setSize(layout.totalWidth(), layout.totalHeight(pageCount));
    }

    // --8<-- [start:stack]
    @Override
    protected void layoutMajorAxis(int targetSpan, int axis, int[] offsets, int[] spans) {
        int width = layout.contentWidth();
        int y = layout.contentTop(0);
        List<FloatingPicture> placed = new ArrayList<>();
        int bottom = y;
        int n = getViewCount();
        if (rowTop.length != n) rowTop = new int[n];
        List<TableBox> boxes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            View child = getView(i);
            int height;
            TableFormat table = TableAttributes.table(child.getAttributes());
            if (table != null && child instanceof PagedParagraphView && i < cellLeft.length
                    && !Double.isNaN(cellLeft[i])) {
                int end = i;
                while (end + 1 < n && TableAttributes.table(getView(end + 1).getAttributes()) == table) end++;
                TableBox box = layoutTable(table, i, end, y, offsets, spans);
                boxes.add(box);
                y = box.bottom();
                i = end;
                continue;
            }
            if (child instanceof PagedParagraphView paragraph) {
                paragraph.setColumn(-1, -1);
                boolean moved = paragraph.startY() != y;
                paragraph.setStartY(y);
                placeFloats(paragraph, y + paragraph.topInset(), placed);
                paragraph.setFloats(floatsBelow(placed, y), moved);
                paragraph.setSize(width, 0);
                height = paragraph.laidOutHeight();
            } else {
                height = (int) Math.ceil(child.getPreferredSpan(Y_AXIS));
                y = layout.place(y, height);
            }
            offsets[i] = y;
            spans[i] = height;
            rowTop[i] = y;
            y += height;
        }
        tables = List.copyOf(boxes);
        floating = List.copyOf(placed);
        for (FloatingPicture f : placed) bottom = Math.max(bottom, (int) Math.ceil(f.box().getMaxY()));
        int pages = layout.pagesUpTo(Math.max(y, bottom));
        if (pages != pageCount) {
            pageCount = pages;
            // The component has to grow or shrink; the parent asks for the preferred size again.
            View parent = getParent();
            if (parent != null) parent.preferenceChanged(this, true, true);
        }
    }
    // --8<-- [end:stack]

    // ---------------------------------------------------------------- tables

    /** A table row as painted: where it is, its cells' edges and backgrounds. */
    public record RowBox(int top, int bottom, double[] edges, Color[] backgrounds, boolean repeat) {
    }

    /**
     * A table as laid out: its rows in painting order (a repeated header row among them, at the top of each
     * page the table continues on), the column edges and the children of the header row.
     */
    public record TableBox(TableFormat format, int offset, int first, int last, List<RowBox> rows,
                           double[] columns, int headerFirst, int headerLast, int headerTop, int bottom) {
        /** The x positions (layout space) of the column lines that can be dragged, left edge first. */
        public double[] columnEdges() {
            return columns.clone();
        }

        public int top() {
            return rows.getFirst().top();
        }
    }

    /** The tables of the last layout, in layout space. */
    public List<TableBox> tableBoxes() {
        return tables;
    }

    /** The column edges of a table, in layout space: scaled down if it is wider than the text column. */
    private double[] columnEdges(TableFormat table) {
        double total = table.totalWidth();
        double content = layout.contentWidth();
        double scale = total > content ? content / total : 1;
        double width = total * scale;
        double x = PageLayout.GAP + layout.left() + switch (table.alignment()) {
            case LEFT -> 0;
            case CENTER -> (content - width) / 2;
            case RIGHT -> content - width;
        };
        double[] edges = new double[table.columns() + 1];
        edges[0] = x;
        for (int c = 0; c < table.columns(); c++) edges[c + 1] = edges[c] + table.width(c) * scale;
        return edges;
    }

    /** Puts the paragraphs of tables into their cells: the minor axis is where the cells are. */
    @Override
    protected void layoutMinorAxis(int targetSpan, int axis, int[] offsets, int[] spans) {
        super.layoutMinorAxis(targetSpan, axis, offsets, spans);
        int n = getViewCount();
        cellLeft = new double[n];
        cellRight = new double[n];
        Arrays.fill(cellLeft, Double.NaN);
        Arrays.fill(cellRight, Double.NaN);
        Map<TableFormat, double[]> edgesOf = new IdentityHashMap<>();
        int inset = PageLayout.GAP + layout.left();
        for (int i = 0; i < n; i++) {
            View child = getView(i);
            TableFormat table = TableAttributes.table(child.getAttributes());
            if (table == null || !(child instanceof PagedParagraphView paragraph)) continue;
            double[] edges = edgesOf.computeIfAbsent(table, this::columnEdges);
            int c = Math.min(TableAttributes.column(child.getAttributes()), table.columns() - 1);
            cellLeft[i] = edges[c];
            cellRight[i] = edges[c + 1];
            double pad = table.padding();
            int x = (int) Math.round(edges[c] + pad);
            int w = Math.max(6, (int) Math.round(edges[c + 1] - pad) - x);
            paragraph.setColumn(x, w);
            offsets[i] = x - inset;
            spans[i] = w;
        }
    }

    // --8<-- [start:table]
    /**
     * Lays out the table made of children first..last from y on, row by row. A row that does not fit on the
     * rest of a page moves to the next one as a whole, below the repeated header row if the table has one;
     * only a row taller than a page is split between its lines.
     */
    private TableBox layoutTable(TableFormat table, int first, int last, int y, int[] offsets, int[] spans) {
        List<int[]> rows = new ArrayList<>();
        int start = first;
        for (int i = first + 1; i <= last + 1; i++) {
            if (i > last || TableAttributes.row(getView(i).getAttributes())
                    != TableAttributes.row(getView(start).getAttributes())) {
                rows.add(new int[]{start, i - 1});
                start = i;
            }
        }
        double[] columns = columnEdges(table);
        boolean repeat = table.repeatHeader() && rows.size() > 1;
        List<RowBox> boxes = new ArrayList<>();
        int top = layout.place(y, 0);
        int pageTop = layout.contentTop(layout.pageAt(top));
        int headerTop = top;
        int headerHeight = 0;
        for (int r = 0; r < rows.size(); r++) {
            int[] row = rows.get(r);
            int height = layoutRow(table, row[0], row[1], top, offsets, spans);
            int page = layout.pageAt(top);
            if (top + height > layout.contentBottom(page) && top > pageTop) {
                top = layout.contentTop(page + 1);
                pageTop = top;
                if (repeat && r > 0) {
                    boxes.add(rowBox(table, rows.getFirst()[0], rows.getFirst()[1], top, top + headerHeight, true));
                    top += headerHeight;
                }
                height = layoutRow(table, row[0], row[1], top, offsets, spans);
            }
            if (r == 0) {
                headerTop = top;
                headerHeight = height;
            }
            boxes.add(rowBox(table, row[0], row[1], top, top + height, false));
            top += height;
        }
        return new TableBox(table, getView(first).getStartOffset(), first, last, List.copyOf(boxes), columns,
                rows.getFirst()[0],
                rows.getFirst()[1], headerTop, top);
    }

    /** Lays out one row's cells at y; returns the row's height, padding included. */
    private int layoutRow(TableFormat table, int first, int last, int y, int[] offsets, int[] spans) {
        int pad = (int) Math.round(table.padding());
        int[] cellHeight = new int[last - first + 1];
        int[] cellStart = new int[last - first + 1];
        int contentHeight = 0;
        int i = first;
        while (i <= last) {
            int column = TableAttributes.column(getView(i).getAttributes());
            int cy = y + pad;
            int j = i;
            while (j <= last && TableAttributes.column(getView(j).getAttributes()) == column) {
                cy = layoutCellParagraph(j, cy, offsets, spans);
                j++;
            }
            for (int k = i; k < j; k++) {
                cellStart[k - first] = i;
                cellHeight[k - first] = cy - (y + pad);
            }
            contentHeight = Math.max(contentHeight, cy - (y + pad));
            i = j;
        }
        // Centred and bottom cells move down within the row.
        i = first;
        while (i <= last) {
            int cellEnd = i;
            while (cellEnd + 1 <= last && cellStart[cellEnd + 1 - first] == i) cellEnd++;
            TableAttributes.VerticalAlignment v = TableAttributes.verticalAlignment(getView(i).getAttributes());
            int free = contentHeight - cellHeight[i - first];
            int shift = v == TableAttributes.VerticalAlignment.CENTER ? free / 2
                    : v == TableAttributes.VerticalAlignment.BOTTOM ? free : 0;
            if (shift > 0) {
                int cy = y + pad + shift;
                for (int k = i; k <= cellEnd; k++) cy = layoutCellParagraph(k, cy, offsets, spans);
            }
            for (int k = i; k <= cellEnd; k++) rowTop[k] = y;
            i = cellEnd + 1;
        }
        return contentHeight + 2 * pad;
    }

    private int layoutCellParagraph(int index, int y, int[] offsets, int[] spans) {
        PagedParagraphView paragraph = (PagedParagraphView) getView(index);
        paragraph.setStartY(y);
        paragraph.setFloats(List.of(), false);
        paragraph.setSize(paragraph.cellWidth(), 0);
        offsets[index] = y;
        spans[index] = paragraph.laidOutHeight();
        return y + spans[index];
    }
    // --8<-- [end:table]

    private RowBox rowBox(TableFormat table, int first, int last, int top, int bottom, boolean repeat) {
        List<Double> edges = new ArrayList<>();
        List<Color> backgrounds = new ArrayList<>();
        int column = -1;
        for (int i = first; i <= last; i++) {
            AttributeSet a = getView(i).getAttributes();
            int c = TableAttributes.column(a);
            if (c == column) continue;
            column = c;
            if (edges.isEmpty()) edges.add(cellLeft[i]);
            else edges.set(edges.size() - 1, cellLeft[i]);
            edges.add(cellRight[i]);
            backgrounds.add(TableAttributes.background(a));
        }
        double[] e = new double[edges.size()];
        for (int k = 0; k < e.length; k++) e[k] = edges.get(k);
        return new RowBox(top, bottom, e, backgrounds.toArray(new Color[0]), repeat);
    }


    // --8<-- [start:place-floats]
    /**
     * Places the floating pictures of a paragraph at its top, at the left or right margin. A picture that does
     * not fit on the rest of the page goes to the top of the next one; pictures on the same side stack.
     */
    private void placeFloats(PagedParagraphView paragraph, int top, List<FloatingPicture> placed) {
        for (PictureAttributes.Placed p : PictureAttributes.in(paragraph.getElement())) {
            Picture picture = p.picture();
            if (!picture.placement().floats()) continue;
            double border = 2 * picture.border().width();
            double w = Math.min(picture.width() + border, layout.contentWidth());
            double h = picture.height() + border;
            boolean left = picture.placement() == Picture.Placement.FLOAT_LEFT;
            double x = PageLayout.GAP + layout.left() + (left ? 0 : layout.contentWidth() - w);
            int y = layout.place(top, (int) Math.ceil(h));
            for (FloatingPicture other : placed) {
                boolean sameSide = other.picture().placement() == picture.placement();
                if (sameSide && other.box().getMaxY() + FloatingPicture.GAP_BELOW > y
                        && other.box().getY() < y + h) {
                    y = layout.place((int) Math.ceil(other.box().getMaxY()) + FloatingPicture.GAP_BELOW,
                            (int) Math.ceil(h));
                }
            }
            placed.add(new FloatingPicture(p.offset(), picture, new Rectangle2D.Double(x, y, w, h)));
        }
    }

    /** The room taken by the pictures that reach below y. */
    private static List<FloatArea> floatsBelow(List<FloatingPicture> placed, int y) {
        List<FloatArea> areas = new ArrayList<>();
        for (FloatingPicture f : placed) {
            FloatArea a = f.area();
            if (a.bottom() > y) areas.add(a);
        }
        return areas;
    }
    // --8<-- [end:place-floats]

    /** The floating pictures as the last layout placed them, boxes in layout space (points). */
    public List<FloatingPicture> floatingPictures() {
        return floating;
    }

    /** The floating picture at a point of the component (screen pixels), null if none. */
    public FloatingPicture floatingPictureAt(double x, double y, Shape allocation) {
        Point2D.Double p = toPoints(x, y, allocation);
        for (int i = floating.size() - 1; i >= 0; i--) {
            if (floating.get(i).box().contains(p)) return floating.get(i);
        }
        return null;
    }

    /**
     * The box of the picture at an offset, border included, in layout space (points); null if there is no
     * picture there or it is not laid out.
     */
    public Rectangle2D pictureBox(int offset) {
        for (FloatingPicture f : floating) {
            if (f.offset() == offset) return f.box();
        }
        Shape a = pointsAllocation();
        View v = this;
        while (!(v instanceof PictureView)) {
            if (v == null || v.getViewCount() == 0) return null;
            int i = v.getViewIndex(offset, Position.Bias.Forward);
            if (i < 0) return null;
            a = v.getChildAllocation(i, a);
            v = v.getView(i);
            if (a == null || v.getStartOffset() > offset || v.getEndOffset() <= offset) return null;
        }
        Rectangle r = a.getBounds();
        float w = v.getPreferredSpan(X_AXIS);
        float h = v.getPreferredSpan(Y_AXIS);
        return new Rectangle2D.Double(r.x, r.y + r.height - h, w, h);
    }

    /** A point in layout space as the component's pixels. */
    public Point2D toScreen(double x, double y, Shape allocation) {
        return toScreen(allocation).transform(new Point2D.Double(x, y), null);
    }

    /** A box in layout space as the component's pixels. */
    public Rectangle toScreen(Rectangle2D points, Shape allocation) {
        return toScreen(allocation).createTransformedShape(points).getBounds();
    }

    private void paintFloats(Graphics2D g) {
        Rectangle clip = g.getClipBounds();
        for (FloatingPicture f : floating) {
            if (clip != null && !f.box().intersects(clip)) continue;
            PictureView.paintPicture(g, f.picture(), f.box());
        }
    }

    @Override
    public float getPreferredSpan(int axis) {
        return (float) (unscaledSpan(axis) * layout.zoom());
    }

    @Override
    public float getMinimumSpan(int axis) {
        return getPreferredSpan(axis);
    }

    @Override
    public float getMaximumSpan(int axis) {
        return getPreferredSpan(axis);
    }

    private int unscaledSpan(int axis) {
        return axis == X_AXIS ? layout.totalWidth() : layout.totalHeight(pageCount);
    }

    // --8<-- [start:hit]
    /**
     * In a gap or margin, a point belongs to the paragraph above it (BoxView would pick the last one). In a
     * table, it belongs to the cell of its row and column: points left of the table count for the first
     * column, points right of it for the last.
     */
    @Override
    protected View getViewAtPoint(int x, int y, Rectangle alloc) {
        int found = 0;
        int n = getViewCount();
        boolean tops = rowTop.length == n;
        for (int i = 0; i < n; i++) {
            int top = tops ? rowTop[i] : getOffset(Y_AXIS, i);
            if (alloc.y + top > y) break;
            if (!inColumn(i, x)) continue;
            if (alloc.y + getOffset(Y_AXIS, i) <= y || firstInCell(i)) found = i;
        }
        childAllocation(found, alloc);
        return getView(found);
    }

    /** Whether x (layout space, like the cell edges) falls into the column of a child; always true outside tables. */
    private boolean inColumn(int i, double x) {
        if (i >= cellLeft.length || Double.isNaN(cellLeft[i])) return true;
        AttributeSet a = getView(i).getAttributes();
        boolean firstColumn = i == 0 || !TableAttributes.sameRow(a, getView(i - 1).getAttributes());
        boolean lastColumn = true;
        for (int k = i + 1; k < getViewCount(); k++) {
            AttributeSet b = getView(k).getAttributes();
            if (!TableAttributes.sameRow(a, b)) break;
            if (TableAttributes.column(b) != TableAttributes.column(a)) {
                lastColumn = false;
                break;
            }
        }
        return (firstColumn || x >= cellLeft[i]) && (lastColumn || x < cellRight[i]);
    }

    private boolean firstInCell(int i) {
        return i == 0 || !TableAttributes.sameCell(getView(i).getAttributes(), getView(i - 1).getAttributes());
    }
    // --8<-- [end:hit]

    // ---------------------------------------------------------------- zoom: screen <-> points

    private AffineTransform toScreen(Shape allocation) {
        Rectangle a = allocation.getBounds();
        double zoom = layout.zoom();
        double dx = Math.max(0, (a.width - layout.totalWidth() * zoom) / 2);
        AffineTransform t = AffineTransform.getTranslateInstance(a.x + dx, a.y);
        t.scale(zoom, zoom);
        return t;
    }

    private Rectangle pointsAllocation() {
        return new Rectangle(0, 0, layout.totalWidth(), layout.totalHeight(pageCount));
    }

    // --8<-- [start:paint]
    @Override
    public void paint(Graphics g, Shape allocation) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.transform(toScreen(allocation));
            // Glyphs scaled from point sizes: without fractional metrics they snap to whole pixels and the
            // spacing inside a line goes uneven.
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Rectangle clip = g2.getClipBounds();
            for (int page = 0; page < pageCount; page++) {
                Rectangle sheet = new Rectangle(PageLayout.GAP, layout.pageTop(page), layout.pageWidth(),
                        layout.pageHeight());
                if (clip != null && !clip.intersects(sheet.x - 4, sheet.y - 4, sheet.width + 8, sheet.height + 8)) {
                    continue;
                }
                g2.setColor(PAGE_SHADOW);
                g2.fillRect(sheet.x + 3, sheet.y + 3, sheet.width, sheet.height);
                g2.setColor(PAPER);
                g2.fill(sheet);
                g2.setColor(PAGE_EDGE);
                g2.setStroke(new BasicStroke(0.5f));
                g2.draw(sheet);
                paintFooter(g2, page);
            }
            screen = true;
            paintTables(g2, true);
            super.paint(g2, pointsAllocation());
            paintTables(g2, false);
            paintFloats(g2);
        } finally {
            g2.dispose();
        }
    }
    // --8<-- [end:paint]

    // --8<-- [start:paint-page]
    /**
     * Paints one page without zoom and without the page decoration, with the top-left corner of the paper at
     * (0, 0): for printing.
     */
    public void paintPage(Graphics2D g, int page) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2.clipRect(0, 0, layout.pageWidth(), layout.pageHeight());
            g2.translate(-PageLayout.GAP, -layout.pageTop(page));
            paintFooter(g2, page);
            screen = false;
            paintTables(g2, true);
            super.paint(g2, pointsAllocation());
            paintTables(g2, false);
            paintFloats(g2);
        } finally {
            g2.dispose();
        }
    }
    // --8<-- [end:paint-page]

    // --8<-- [start:paint-tables]
    /** Light grey guides where a table has no lines; on the screen only. */
    private static final Color GUIDE = new Color(0xC8C8C8);

    /**
     * Paints the tables: before the text the cell backgrounds, after it the repeated header rows and the
     * lines.
     */
    private void paintTables(Graphics2D g, boolean backgrounds) {
        Rectangle clip = g.getClipBounds();
        for (TableBox table : tables) {
            if (clip != null && (table.bottom() < clip.y || table.top() > clip.y + clip.height)) continue;
            if (backgrounds) {
                for (RowBox row : table.rows()) {
                    for (int c = 0; c < row.backgrounds().length; c++) {
                        if (row.backgrounds()[c] == null) continue;
                        g.setColor(row.backgrounds()[c]);
                        g.fill(new Rectangle2D.Double(row.edges()[c], row.top(), row.edges()[c + 1] - row.edges()[c],
                                row.bottom() - row.top()));
                    }
                }
            } else {
                for (RowBox row : table.rows()) {
                    if (row.repeat()) paintHeaderCopy(g, table, row.top() - table.headerTop());
                }
                paintLines(g, table);
            }
        }
    }

    /** Paints the header row's text again, moved down by dy. */
    private void paintHeaderCopy(Graphics2D g, TableBox table, int dy) {
        Rectangle all = pointsAllocation();
        for (int i = table.headerFirst(); i <= table.headerLast(); i++) {
            Shape a = getChildAllocation(i, all);
            if (a == null) continue;
            Rectangle r = a.getBounds();
            r.translate(0, dy);
            Rectangle clip = g.getClipBounds();
            if (clip == null || clip.intersects(r)) getView(i).paint(g, r);
        }
    }

    private void paintLines(Graphics2D g, TableBox table) {
        TableFormat format = table.format();
        TableFormat.Lines lines = format.lines();
        boolean guides = lines == TableFormat.Lines.NONE || format.lineWidth() <= 0;
        if (guides && !screen) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setColor(guides ? GUIDE : format.lineColor());
            float width = guides ? 0.5f : format.lineWidth();
            g2.setStroke(guides ? new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                    new float[]{2, 2}, 0) : new BasicStroke(width, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            boolean all = guides || lines == TableFormat.Lines.ALL;
            boolean outer = all || lines == TableFormat.Lines.OUTER;
            boolean rows = all || lines == TableFormat.Lines.ROWS;
            List<RowBox> list = table.rows();
            for (int r = 0; r < list.size(); r++) {
                RowBox row = list.get(r);
                double[] e = row.edges();
                double left = e[0];
                double right = e[e.length - 1];
                boolean firstOnPage = r == 0 || layout.pageAt(list.get(r - 1).top()) != layout.pageAt(row.top());
                boolean lastOnPage = r == list.size() - 1
                        || layout.pageAt(list.get(r + 1).top()) != layout.pageAt(row.top());
                if (rows || firstOnPage && outer) line(g2, left, row.top(), right, row.top());
                if (rows || lastOnPage && outer) line(g2, left, row.bottom(), right, row.bottom());
                if (outer) {
                    line(g2, left, row.top(), left, row.bottom());
                    line(g2, right, row.top(), right, row.bottom());
                }
                if (all) {
                    for (int c = 1; c < e.length - 1; c++) line(g2, e[c], row.top(), e[c], row.bottom());
                }
            }
        } finally {
            g2.dispose();
        }
    }

    private static void line(Graphics2D g, double x0, double y0, double x1, double y1) {
        g.draw(new java.awt.geom.Line2D.Double(x0, y0, x1, y1));
    }
    // --8<-- [end:paint-tables]

    private void paintFooter(Graphics2D g, int page) {
        String text = footer.apply(page);
        if (text == null || text.isEmpty()) return;
        g.setColor(FOOTER_TEXT);
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 9));
        FontMetrics fm = g.getFontMetrics();
        int bottomMargin = layout.pageTop(page) + layout.pageHeight() - layout.contentBottom(page);
        int baseline = layout.contentBottom(page) + Math.max(fm.getAscent(), bottomMargin / 2);
        g.drawString(text, PageLayout.GAP + (layout.pageWidth() - fm.stringWidth(text)) / 2, baseline);
    }

    @Override
    public Shape modelToView(int pos, Shape allocation, Position.Bias b) throws BadLocationException {
        Shape shape = super.modelToView(pos, pointsAllocation(), bias(pos, b));
        return toScreen(shape, allocation);
    }

    /**
     * The area of a range, which Swing repaints when a selection or highlight changes. View's implementation
     * would measure both ends with {@link #modelToView(int, Shape, Position.Bias)} above, which already
     * answers in screen pixels, and so convert twice: wrong wherever the zoom is not 1 (the default zoom
     * follows the screen's resolution), leaving parts of an old selection on the screen. So this measures in
     * points: from the first end to the second, whole rows in between, then converts once.
     */
    @Override
    public Shape modelToView(int p0, Position.Bias b0, int p1, Position.Bias b1, Shape allocation)
            throws BadLocationException {
        Rectangle points = pointsAllocation();
        Rectangle2D start = super.modelToView(p0, points, bias(p0, b0)).getBounds2D();
        Rectangle2D end = super.modelToView(p1, points, b1).getBounds2D();
        Rectangle2D range = start.createUnion(end);
        if (start.getY() != end.getY()) {
            range.setRect(points.x, range.getY(), points.width, range.getHeight());
        }
        return toScreen(range, allocation);
    }

    /**
     * A position right after a line break is the start of the next line, also with backward bias: Swing's
     * caret keeps that bias after typing, which would draw it at the end of the line before the break.
     */
    private Position.Bias bias(int pos, Position.Bias b) {
        if (b != Position.Bias.Backward || pos <= 0) return b;
        try {
            return getDocument().getText(pos - 1, 1).charAt(0) == WritepadDocument.LINE_BREAK
                    ? Position.Bias.Forward : b;
        } catch (BadLocationException e) {
            return b;
        }
    }

    private Shape toScreen(Shape shape, Shape allocation) {
        Rectangle r = toScreen(allocation).createTransformedShape(shape).getBounds();
        // A caret is zero pixels wide; keep it that way instead of rounding it up.
        if (shape.getBounds().width == 0) r.width = 0;
        return r;
    }

    @Override
    public int viewToModel(float x, float y, Shape allocation, Position.Bias[] bias) {
        Point2D.Double p = toPoints(x, y, allocation);
        int pos = super.viewToModel((float) p.x, (float) p.y, pointsAllocation(), bias);
        return nearestOnRow(pos, p.x);
    }

    /**
     * The character boundary on the row of {@code pos} that is closest to x (points). Swing's views find the
     * offset for a point with whole-pixel glyph widths, while the caret and the painted text use fractional
     * ones (see {@code PageEditor.getFontMetrics}); this corrects the difference of a character or two
     * towards the end of a line, using the same measurement as the caret.
     */
    private int nearestOnRow(int pos, double x) {
        Rectangle2D start = caretAt(pos);
        if (start == null) return pos;
        double distance = Math.abs(start.getX() - x);
        // Within the paragraph: in a table, the cell next door may have a line at the same height.
        Element paragraph = getElement().getElement(getElement().getElementIndex(pos));
        int low = paragraph.getStartOffset();
        int high = Math.min(getDocument().getLength(), paragraph.getEndOffset() - 1);
        for (int step : new int[]{-1, 1}) {
            for (int next = pos + step; next >= low && next <= high; next += step) {
                Rectangle2D r = caretAt(next);
                if (r == null || r.getY() != start.getY()) break;
                double d = Math.abs(r.getX() - x);
                if (d >= distance) break;
                distance = d;
                pos = next;
            }
        }
        return pos;
    }

    /** The caret rectangle of an offset in points, null if it has none. */
    private Rectangle2D caretAt(int pos) {
        try {
            Shape shape = super.modelToView(pos, pointsAllocation(), Position.Bias.Forward);
            return shape == null ? null : shape.getBounds2D();
        } catch (BadLocationException e) {
            return null;
        }
    }

    private Point2D.Double toPoints(double x, double y, Shape allocation) {
        AffineTransform t = toScreen(allocation);
        double zoom = layout.zoom();
        return new Point2D.Double((x - t.getTranslateX()) / zoom, (y - t.getTranslateY()) / zoom);
    }

    /**
     * Up and down keep the caret's column. The text component remembers that column in screen pixels; the
     * paragraphs compare it with their lines in points, so it is converted for the duration of the call.
     */
    @Override
    public int getNextVisualPositionFrom(int pos, Position.Bias b, Shape allocation, int direction,
                                         Position.Bias[] biasRet) throws BadLocationException {
        Caret caret = getContainer() instanceof JTextComponent text ? text.getCaret() : null;
        Point magic = caret == null ? null : caret.getMagicCaretPosition();
        Point2D.Double column = magic == null ? null : toPoints(magic.x, magic.y, allocation);
        if (magic != null) caret.setMagicCaretPosition(new Point((int) Math.round(column.x), (int) Math.round(column.y)));
        try {
            int next = super.getNextVisualPositionFrom(pos, bias(pos, b), pointsAllocation(), direction, biasRet);
            boolean vertical = direction == NORTH || direction == SOUTH;
            return vertical && column != null && next >= 0 ? nearestOnRow(next, column.x) : next;
        } finally {
            if (magic != null) caret.setMagicCaretPosition(magic);
        }
    }

    // --8<-- [start:north-south]
    /**
     * Up and down in a table stay in the column: from a cell's first or last line to the cell above or below,
     * and into a table from above or below at the column under the caret.
     */
    @Override
    protected int getNextNorthSouthVisualPositionFrom(int pos, Position.Bias b, Shape a, int direction,
                                                      Position.Bias[] biasRet) throws BadLocationException {
        int n = getViewCount();
        if (pos < 0 || rowTop.length != n || cellLeft.length != n || tables.isEmpty()) {
            return super.getNextNorthSouthVisualPositionFrom(pos, b, a, direction, biasRet);
        }
        int i = getViewIndex(b == Position.Bias.Backward && pos > 0 ? pos - 1 : pos, Position.Bias.Forward);
        if (i < 0) return super.getNextNorthSouthVisualPositionFrom(pos, b, a, direction, biasRet);
        int found = getView(i).getNextVisualPositionFrom(pos, b, getChildAllocation(i, a), direction, biasRet);
        if (found != -1) return found;
        boolean down = direction == SOUTH;
        int target = neighbour(i, down, columnX(pos));
        if (target < 0) return -1;
        return getView(target).getNextVisualPositionFrom(-1, b, getChildAllocation(target, a), direction, biasRet);
    }

    /** The x (points) the caret keeps going up and down: the remembered column, else the caret's own. */
    private double columnX(int pos) {
        Caret caret = getContainer() instanceof JTextComponent text ? text.getCaret() : null;
        Point magic = caret == null ? null : caret.getMagicCaretPosition();
        if (magic != null) return magic.x;
        Rectangle2D r = caretAt(pos);
        return r == null ? 0 : r.getX();
    }

    /** The child above or below child i for the caret, in the column at x; -1 at the document's ends. */
    private int neighbour(int i, boolean down, double x) {
        int n = getViewCount();
        boolean cell = !Double.isNaN(cellLeft[i]);
        if (!cell) {
            int k = down ? i + 1 : i - 1;
            if (k < 0 || k >= n) return -1;
            return Double.isNaN(cellLeft[k]) ? k : inRow(k, x, down);
        }
        AttributeSet a = getView(i).getAttributes();
        int k = down ? i + 1 : i - 1;
        if (k >= 0 && k < n && TableAttributes.sameCell(a, getView(k).getAttributes())) return k;
        // Leave the row.
        while (k >= 0 && k < n && TableAttributes.sameRow(a, getView(k).getAttributes())) k += down ? 1 : -1;
        if (k < 0 || k >= n) return -1;
        AttributeSet b = getView(k).getAttributes();
        return TableAttributes.table(b) != null && TableAttributes.table(b) == TableAttributes.table(a)
                ? inRow(k, x, down) : k;
    }

    /** In the row of child k, the cell at x: its first paragraph going down, its last going up. */
    private int inRow(int k, double x, boolean down) {
        AttributeSet a = getView(k).getAttributes();
        int s = k;
        while (s > 0 && TableAttributes.sameRow(a, getView(s - 1).getAttributes())) s--;
        int e = k;
        while (e + 1 < getViewCount() && TableAttributes.sameRow(a, getView(e + 1).getAttributes())) e++;
        int cellStart = s;
        for (int j = s; j <= e; j++) {
            if (j > s && !TableAttributes.sameCell(getView(j).getAttributes(), getView(j - 1).getAttributes())) {
                cellStart = j;
            }
            boolean cellEnds = j == e || !TableAttributes.sameCell(getView(j).getAttributes(),
                    getView(j + 1).getAttributes());
            if (cellEnds && (inColumn(cellStart, x) || j == e)) return down ? cellStart : j;
        }
        return down ? s : e;
    }
    // --8<-- [end:north-south]

    // getChildAllocation stays in points: CompositeView uses it internally to walk into the children.

    // Edits repaint the whole component: child views would compute their damage in points.

    @Override
    public void insertUpdate(DocumentEvent e, Shape allocation, ViewFactory f) {
        super.insertUpdate(e, null, f);
        invalidateLayout();
        repaintAll();
    }

    @Override
    public void removeUpdate(DocumentEvent e, Shape allocation, ViewFactory f) {
        super.removeUpdate(e, null, f);
        invalidateLayout();
        repaintAll();
    }

    @Override
    public void changedUpdate(DocumentEvent e, Shape allocation, ViewFactory f) {
        super.changedUpdate(e, null, f);
        invalidateLayout();
        repaintAll();
    }

    /** Both axes again: a paragraph's table attributes decide its cell, which is the minor axis. */
    private void invalidateLayout() {
        layoutChanged(X_AXIS);
        layoutChanged(Y_AXIS);
    }

    private void repaintAll() {
        Container host = getContainer();
        if (host != null) host.repaint();
    }
}
