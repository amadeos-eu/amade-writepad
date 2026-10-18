package eu.amadeos.writepad.service.layout;

import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.LabelView;
import javax.swing.text.Position;
import javax.swing.text.View;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;

/**
 * A line break within a paragraph: ends the line it is on, takes no width and paints nothing. It keeps the
 * height of its font, so an empty line after it is as high as the text. Swing does not justify a line that
 * ends in a forced break, as word processors do.
 */
final class LineBreakView extends LabelView {
    LineBreakView(Element element) {
        super(element);
    }

    @Override
    public float getPreferredSpan(int axis) {
        return axis == View.X_AXIS ? 0 : super.getPreferredSpan(axis);
    }

    @Override
    public float getMinimumSpan(int axis) {
        return getPreferredSpan(axis);
    }

    @Override
    public int getBreakWeight(int axis, float pos, float len) {
        return axis == View.X_AXIS ? View.ForcedBreakWeight : super.getBreakWeight(axis, pos, len);
    }

    @Override
    public View breakView(int axis, int p0, float pos, float len) {
        return this;
    }

    @Override
    public View createFragment(int p0, int p1) {
        return this;
    }

    @Override
    public void paint(Graphics g, Shape a) {
        // invisible
    }

    @Override
    public Shape modelToView(int pos, Shape a, Position.Bias b) throws BadLocationException {
        Rectangle r = a.getBounds();
        return new Rectangle(r.x, r.y, 0, r.height);
    }

    /** A click behind the end of the line puts the caret before the break, at the end of that line. */
    @Override
    public int viewToModel(float x, float y, Shape a, Position.Bias[] biasReturn) {
        biasReturn[0] = Position.Bias.Forward;
        return getStartOffset();
    }
}
