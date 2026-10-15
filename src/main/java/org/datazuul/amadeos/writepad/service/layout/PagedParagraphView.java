package org.datazuul.amadeos.writepad.service.layout;

import javax.swing.SizeRequirements;
import javax.swing.text.Element;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyleConstants;
import javax.swing.text.TabStop;
import javax.swing.text.View;
import java.awt.Rectangle;

/**
 * A paragraph whose lines never cross a page boundary: a line that would run into the bottom margin moves
 * to the top of the next page. {@link PagedSectionView} tells the paragraph where it starts before laying
 * it out, so that the paragraph knows where the page boundaries fall.
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
    private int startY = -1;
    /** Space added in front of moved lines in the last layout. */
    private int pushed;
    /** Height of the last layout, insets and moved lines included. */
    private int laidOutHeight;

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

    int laidOutHeight() {
        return laidOutHeight;
    }

    @Override
    protected void layoutMajorAxis(int targetSpan, int axis, int[] offsets, int[] spans) {
        int top = startY + getTopInset();
        int offset = 0;
        int moved = 0;
        for (int i = 0; i < getViewCount(); i++) {
            int span = (int) Math.ceil(getView(i).getPreferredSpan(Y_AXIS));
            int placed = layout.place(top + offset, span);
            moved += placed - (top + offset);
            offsets[i] = placed - top;
            spans[i] = span;
            offset = offsets[i] + span;
        }
        pushed = moved;
        laidOutHeight = getTopInset() + offset + getBottomInset();
    }

    @Override
    protected SizeRequirements calculateMajorAxisRequirements(int axis, SizeRequirements r) {
        SizeRequirements req = super.calculateMajorAxisRequirements(axis, r);
        req.minimum += pushed;
        req.preferred += pushed;
        req.maximum += pushed;
        return req;
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
        return columnLeft() + getLeftInset() + rowInset;
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
