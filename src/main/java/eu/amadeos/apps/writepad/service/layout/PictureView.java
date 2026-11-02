package eu.amadeos.apps.writepad.service.layout;

import eu.amadeos.apps.writepad.domain.Picture;
import eu.amadeos.apps.writepad.service.picture.PictureAttributes;
import eu.amadeos.apps.writepad.service.table.TableAttributes;

import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Position;
import javax.swing.text.View;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * A picture's character. In the line it is as wide and high as the picture (with its border) and sits on the
 * baseline like a large letter. A floating picture is drawn by {@link PagedSectionView} beside the text; its
 * character only anchors it to the paragraph and takes no room in the line.
 */
final class PictureView extends View {
    private static final Color MISSING = new Color(0xE8E8E8);
    private static final Color MISSING_TEXT = new Color(0x808080);

    PictureView(Element element) {
        super(element);
    }

    private Picture picture() {
        return PictureAttributes.picture(getElement().getAttributes());
    }

    /** Whether the picture floats: pictures in table cells always sit in the line. */
    private boolean floats() {
        Picture p = picture();
        return p != null && floats(p, getElement());
    }

    static boolean floats(Picture p, Element character) {
        return p.placement().floats() && !TableAttributes.inTable(character.getParentElement().getAttributes());
    }

    @Override
    public float getPreferredSpan(int axis) {
        Picture p = picture();
        if (p == null || floats()) return 0;
        double border = 2 * p.border().width();
        return (float) ((axis == X_AXIS ? p.width() : p.height()) + border);
    }

    @Override
    public float getMinimumSpan(int axis) {
        return getPreferredSpan(axis);
    }

    @Override
    public float getMaximumSpan(int axis) {
        return getPreferredSpan(axis);
    }

    /** The bottom edge sits on the baseline. */
    @Override
    public float getAlignment(int axis) {
        return axis == Y_AXIS ? 1 : super.getAlignment(axis);
    }

    @Override
    public void paint(Graphics g, Shape a) {
        Picture p = picture();
        if (p == null || floats()) return;
        Rectangle r = a.getBounds();
        paintPicture((Graphics2D) g, p, new Rectangle2D.Double(r.x, r.y + r.height - getPreferredSpan(Y_AXIS),
                getPreferredSpan(X_AXIS), getPreferredSpan(Y_AXIS)));
    }

    /**
     * Paints a picture with its border into a box (border included), at the full resolution of the image, so
     * that printing gets every pixel.
     */
    static void paintPicture(Graphics2D g, Picture p, Rectangle2D box) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            double b = p.border().width();
            Rectangle2D inner = new Rectangle2D.Double(box.getX() + b, box.getY() + b, box.getWidth() - 2 * b,
                    box.getHeight() - 2 * b);
            BufferedImage image = p.image();
            if (image != null) {
                g2.translate(inner.getX(), inner.getY());
                g2.scale(inner.getWidth() / image.getWidth(), inner.getHeight() / image.getHeight());
                g2.drawImage(image, 0, 0, null);
                g2.setTransform(g.getTransform());
            } else {
                g2.setColor(MISSING);
                g2.fill(inner);
                g2.setColor(MISSING_TEXT);
                g2.draw(inner);
                if (!p.description().isEmpty()) {
                    g2.setFont(g2.getFont().deriveFont(9f));
                    g2.drawString(p.description(), (float) inner.getX() + 4, (float) inner.getY() + 12);
                }
            }
            if (b > 0) {
                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke((float) b, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
                g2.draw(new Rectangle2D.Double(box.getX() + b / 2, box.getY() + b / 2, box.getWidth() - b,
                        box.getHeight() - b));
            }
        } finally {
            g2.dispose();
        }
    }

    @Override
    public Shape modelToView(int pos, Shape a, Position.Bias b) throws BadLocationException {
        int p0 = getStartOffset();
        int p1 = getEndOffset();
        if (pos < p0 || pos > p1) throw new BadLocationException("Outside the picture", pos);
        Rectangle r = a.getBounds();
        if (floats()) return new Rectangle(r.x, r.y, 0, r.height);
        if (pos == p1) r.x += r.width;
        r.width = 0;
        return r;
    }

    @Override
    public int viewToModel(float x, float y, Shape a, Position.Bias[] bias) {
        Rectangle r = a.getBounds();
        if (x < r.x + r.width / 2f) {
            bias[0] = Position.Bias.Forward;
            return getStartOffset();
        }
        bias[0] = Position.Bias.Backward;
        return getEndOffset();
    }
}
