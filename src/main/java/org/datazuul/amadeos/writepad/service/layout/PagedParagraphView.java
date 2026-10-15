package org.datazuul.amadeos.writepad.service.layout;

import javax.swing.SizeRequirements;
import javax.swing.text.Element;
import javax.swing.text.ParagraphView;
import javax.swing.text.View;
import java.awt.Rectangle;

/**
 * A paragraph whose lines never cross a page boundary: a line that would run into the bottom margin moves
 * to the top of the next page. {@link PagedSectionView} tells the paragraph where it starts before laying
 * it out, so that the paragraph knows where the page boundaries fall.
 */
final class PagedParagraphView extends ParagraphView {
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
