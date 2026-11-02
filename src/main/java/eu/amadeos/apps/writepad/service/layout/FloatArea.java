package eu.amadeos.apps.writepad.service.layout;

/**
 * The room a floating picture takes from the text, in layout space (points): the picture with its border and
 * the gap to the text. {@code left} is true for a picture at the left margin.
 */
record FloatArea(int x, int y, int width, int height, boolean left) {
    int bottom() {
        return y + height;
    }
}
