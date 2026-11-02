package eu.amadeos.apps.writepad.service.layout;

import eu.amadeos.apps.writepad.service.list.ListAttributes;
import eu.amadeos.apps.writepad.service.list.ListNumbering;

import javax.swing.SizeRequirements;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.ParagraphView;
import javax.swing.text.Position;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabStop;
import javax.swing.text.View;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;

/**
 * A paragraph whose lines never cross a page boundary: a line that would run into the bottom margin moves
 * to the top of the next page. {@link PagedSectionView} tells the paragraph where it starts before laying
 * it out, so that the paragraph knows where the page boundaries fall.
 *
 * <p>A list item's text starts at the left indent on every line; its label ("•", "2.", ...) is painted in
 * front of it, at the first line's indent, and is not part of the text.
 *
 * <p>Tabs follow {@link TabStops}, measured from the left margin. Swing's own tab handling measures from a
 * position it only learns while painting, so lines could break differently before and after the first
 * paint, and it ignores tab stops in paragraphs that are not left-aligned.
 */
final class PagedParagraphView extends ParagraphView {
    private static final char[] TAB_STOPS = {'\t', '\n'};
    /** A decimal tab aligns on the decimal separator of the language amade-writepad runs in. */
    private static final char[] DECIMAL_STOPS = {'\t', '\n',
            java.text.DecimalFormatSymbols.getInstance().getDecimalSeparator()};

    private final PageLayout layout;
    private boolean listItem;
    /** Where a list item's label starts, relative to the left indent. */
    private int labelIndent;
    private int startY = -1;
    /** Space added in front of moved lines in the last layout. */
    private int pushed;
    /** Height of the last layout, insets and moved lines included. */
    private int laidOutHeight;
    /** Floating pictures that may reach into this paragraph, in layout space. */
    private List<FloatArea> floats = List.of();
    /** Per row, while floats are about: the room taken at the left and right, and where it moved down to. */
    private final List<RowShape> shapes = new ArrayList<>();

    /** Lines narrower than this move below the floating picture instead of squeezing beside it. */
    static final int MIN_LINE = 54;

    private record RowShape(int left, int right, int movedTo) {
        static final RowShape FULL = new RowShape(0, 0, -1);
    }

    PagedParagraphView(Element element, PageLayout layout) {
        super(element);
        this.layout = layout;
    }

    /** Sets the absolute y (layout space) of the paragraph's top edge. */
    void setStartY(int y) {
        if (y != startY) {
            startY = y;
            layoutChanged(Y_AXIS);
        }
    }

    /**
     * Sets the floating pictures that may reach into the paragraph. The lines are broken again when they change,
     * or when the paragraph moved while floats are about.
     */
    void setFloats(List<FloatArea> areas, boolean moved) {
        if (!areas.equals(floats) || moved && !areas.isEmpty()) {
            floats = List.copyOf(areas);
            shapes.clear();
            // Both axes: the flow strategy only rebreaks all rows when the major allocation is invalid.
            layoutChanged(X_AXIS);
            layoutChanged(Y_AXIS);
        }
    }

    int startY() {
        return startY;
    }

    int topInset() {
        return getTopInset();
    }

    int laidOutHeight() {
        return laidOutHeight;
    }

    // --8<-- [start:floats]
    /**
     * Where row {@code row} goes and how much room floating pictures leave it. The rows above are already
     * filled, so their heights are known; this row is assumed as high as the one before it (the first as high
     * as its font). A row that would be narrower than {@link #MIN_LINE} moves below the picture.
     */
    private RowShape shape(int row) {
        if (floats.isEmpty() || startY < 0) return RowShape.FULL;
        while (shapes.size() > row) shapes.removeLast();
        int y = startY + getTopInset();
        for (int k = 0; k < row; k++) {
            int h = rowHeight(k);
            y = layout.place(y, h);
            RowShape s = k < shapes.size() ? shapes.get(k) : RowShape.FULL;
            if (s.movedTo() > y) y = s.movedTo();
            y += h;
            if (k >= shapes.size()) shapes.add(s);
        }
        int h = row > 0 ? rowHeight(row - 1) : lineHeight();
        y = layout.place(y, h);
        int textLeft = columnLeft() + getLeftInset();
        int textRight = columnLeft() + layout.contentWidth() - getRightInset();
        int movedTo = -1;
        RowShape result = RowShape.FULL;
        for (int guard = 0; guard < 20; guard++) {
            int left = 0;
            int right = 0;
            int clearBelow = Integer.MAX_VALUE;
            for (FloatArea f : floats) {
                if (f.y() >= y + h || f.bottom() <= y) continue;
                if (f.left()) left = Math.max(left, f.x() + f.width() - textLeft);
                else right = Math.max(right, textRight - f.x());
                clearBelow = Math.min(clearBelow, f.bottom());
            }
            result = new RowShape(Math.max(0, left), Math.max(0, right), movedTo);
            if (clearBelow == Integer.MAX_VALUE || textRight - textLeft - left - right >= MIN_LINE) break;
            y = layout.place(clearBelow, h);
            movedTo = y;
            result = new RowShape(0, 0, movedTo);
        }
        shapes.add(result);
        return result;
    }
    // --8<-- [end:floats]

    private RowShape knownShape(int row) {
        return row < shapes.size() ? shapes.get(row) : RowShape.FULL;
    }

    private int rowHeight(int row) {
        return row < getViewCount() ? (int) Math.ceil(getView(row).getPreferredSpan(Y_AXIS)) : lineHeight();
    }

    /** The height of a line in the paragraph's first font, for a row whose height is not known yet. */
    private int lineHeight() {
        if (getDocument() instanceof StyledDocument document) {
            Font font = document.getFont(document.getCharacterElement(getStartOffset()).getAttributes());
            Container host = getContainer();
            FontMetrics fm = host != null ? host.getFontMetrics(font) : null;
            if (fm != null) return fm.getHeight();
            return (int) Math.ceil(font.getSize2D() * 1.2f);
        }
        return 14;
    }

    @Override
    public int getFlowSpan(int index) {
        int span = super.getFlowSpan(index);
        if (span == Integer.MAX_VALUE || floats.isEmpty()) return span;
        RowShape s = knownShape(index);
        return Math.max(1, span - s.left() - s.right());
    }

    @Override
    protected void layoutMinorAxis(int targetSpan, int axis, int[] offsets, int[] spans) {
        super.layoutMinorAxis(targetSpan, axis, offsets, spans);
        if (floats.isEmpty()) return;
        for (int i = 0; i < getViewCount(); i++) {
            RowShape s = knownShape(i);
            if (s.left() == 0 && s.right() == 0) continue;
            View v = getView(i);
            int room = Math.max(1, targetSpan - s.left() - s.right());
            int max = (int) v.getMaximumSpan(axis);
            if (max < room) {
                offsets[i] = s.left() + (int) ((room - max) * v.getAlignment(axis));
                spans[i] = max;
            } else {
                offsets[i] = s.left();
                spans[i] = Math.max((int) v.getMinimumSpan(axis), room);
            }
        }
    }

    // --8<-- [start:layout]
    @Override
    protected void layoutMajorAxis(int targetSpan, int axis, int[] offsets, int[] spans) {
        int top = startY + getTopInset();
        int offset = 0;
        int moved = 0;
        for (int i = 0; i < getViewCount(); i++) {
            int span = (int) Math.ceil(getView(i).getPreferredSpan(Y_AXIS));
            int placed = layout.place(top + offset, span);
            int movedTo = knownShape(i).movedTo();
            if (movedTo > placed) placed = movedTo;
            moved += placed - (top + offset);
            offsets[i] = placed - top;
            spans[i] = span;
            offset = offsets[i] + span;
        }
        pushed = moved;
        laidOutHeight = getTopInset() + offset + getBottomInset();
    }
    // --8<-- [end:layout]

    @Override
    protected SizeRequirements calculateMajorAxisRequirements(int axis, SizeRequirements r) {
        SizeRequirements req = super.calculateMajorAxisRequirements(axis, r);
        req.minimum += pushed;
        req.preferred += pushed;
        req.maximum += pushed;
        return req;
    }

    @Override
    protected void setPropertiesFromAttributes() {
        super.setPropertiesFromAttributes();
        listItem = ListAttributes.isListItem(getAttributes());
        if (listItem) {
            // The label takes the first line's indent; the text itself starts where the other lines do.
            labelIndent = firstLineIndent;
            firstLineIndent = 0;
        }
    }

    @Override
    public void paint(Graphics g, Shape a) {
        super.paint(g, a);
        if (listItem) paintLabel(g, a);
    }

    /** Space kept between a label that does not fit its indent and the text. */
    private static final float LABEL_GAP = 3f;

    private void paintLabel(Graphics g, Shape a) {
        if (!(getDocument() instanceof StyledDocument document)) return;
        String label = ListNumbering.label(document, getElement());
        if (label == null) return;
        Rectangle alloc = a.getBounds();
        Shape first;
        try {
            first = modelToView(getStartOffset(), a, Position.Bias.Forward);
        } catch (BadLocationException e) {
            return;
        }
        AttributeSet chars = document.getCharacterElement(getStartOffset()).getAttributes();
        Font font = document.getFont(chars);
        Container host = getContainer();
        FontMetrics fm = host != null ? host.getFontMetrics(font) : g.getFontMetrics(font);
        float text = alloc.x + getLeftInset();
        float x = text + labelIndent;
        float width = (float) fm.getStringBounds(label, g).getWidth();
        if (x + width > text - LABEL_GAP) x = text - LABEL_GAP - width;
        Rectangle line = first.getBounds();
        float baseline = line.y + line.height - fm.getDescent();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setFont(font);
            g2.setColor(document.getForeground(chars));
            g2.drawString(label, x, baseline);
        } finally {
            g2.dispose();
        }
    }

    /** Where the text column starts: paragraphs sit at the section's left inset. */
    private int columnLeft() {
        return PageLayout.GAP + layout.left();
    }

    /**
     * The x where a row starts, in the same coordinates as painting, so that tab widths computed while
     * breaking lines are the ones painted. Swing returns its painting-time tab base here, which is stale
     * until the first paint.
     */
    @Override
    public int getFlowStart(int row) {
        int rowInset = super.getFlowStart(row) - (int) getTabBase();
        // The flow asks for the start before the span: this is where the row's room is worked out.
        int floatInset = floats.isEmpty() ? 0 : shape(row).left();
        return columnLeft() + getLeftInset() + rowInset + floatInset;
    }

    @Override
    public float nextTabStop(float x, int tabOffset) {
        int paragraphAlignment = StyleConstants.getAlignment(getAttributes());
        if (paragraphAlignment == StyleConstants.ALIGN_CENTER || paragraphAlignment == StyleConstants.ALIGN_RIGHT) {
            // These lines move as a whole after they are broken, so a stop measured from the margin would
            // give a different width when painted: a tab is a fixed gap here.
            return x + TabStops.DEFAULT_INTERVAL / 2;
        }
        float position = x - columnLeft();
        boolean firstLine = getViewCount() <= 1 || tabOffset < getView(1).getStartOffset();
        TabStop stop = TabStops.next(position, getTabSet(), getLeftInset(), firstLine, firstLineIndent);
        float target = stop.getPosition();
        int alignment = stop.getAlignment();
        if (alignment == TabStop.ALIGN_RIGHT || alignment == TabStop.ALIGN_CENTER
                || alignment == TabStop.ALIGN_DECIMAL) {
            // The text after the tab, up to the next tab (or decimal point), ends at (or centres on) the stop.
            char[] until = alignment == TabStop.ALIGN_DECIMAL ? DECIMAL_STOPS : TAB_STOPS;
            int end = findOffsetToCharactersInString(until, tabOffset + 1);
            if (end == -1) end = getEndOffset();
            float width = getPartialSize(tabOffset + 1, end);
            float shift = alignment == TabStop.ALIGN_CENTER ? width / 2 : width;
            target = Math.max(position, target - shift);
        }
        return columnLeft() + target;
    }

    /** In the space a moved line leaves behind, a point belongs to the line above it. */
    @Override
    protected View getViewAtPoint(int x, int y, Rectangle alloc) {
        int found = 0;
        for (int i = 0; i < getViewCount(); i++) {
            if (alloc.y + getOffset(Y_AXIS, i) <= y) found = i;
        }
        childAllocation(found, alloc);
        return getView(found);
    }
}
