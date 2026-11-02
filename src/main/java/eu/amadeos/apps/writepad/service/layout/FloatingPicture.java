package eu.amadeos.apps.writepad.service.layout;

import eu.amadeos.apps.writepad.domain.Picture;

import java.awt.geom.Rectangle2D;

/**
 * A floating picture as the last layout placed it: the offset of its character, the picture and its box on
 * the page (border included) in layout space (points).
 */
public record FloatingPicture(int offset, Picture picture, Rectangle2D box) {
    /** Space between a floating picture and the text beside it. */
    public static final int SIDE_GAP = 9;
    /** Space between a floating picture and the text below it, or the next picture on the same side. */
    public static final int GAP_BELOW = 6;

    FloatArea area() {
        boolean left = picture.placement() == Picture.Placement.FLOAT_LEFT;
        int x = (int) Math.floor(box.getX()) - (left ? 0 : SIDE_GAP);
        int w = (int) Math.ceil(box.getWidth()) + SIDE_GAP;
        return new FloatArea(x, (int) Math.floor(box.getY()), w, (int) Math.ceil(box.getHeight()) + GAP_BELOW, left);
    }
}
