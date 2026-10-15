package org.datazuul.amadeos.writepad.domain;

/**
 * Page margins in points.
 */
public record Margins(double top, double left, double bottom, double right) {
    /** 25 mm on every side, the default of most word processors in Europe. */
    public static final Margins DEFAULT = ofMm(25, 25, 25, 25);

    public Margins {
        if (top < 0 || left < 0 || bottom < 0 || right < 0) {
            throw new IllegalArgumentException("Negative margin: " + top + ", " + left + ", " + bottom + ", " + right);
        }
    }

    public static Margins ofMm(double top, double left, double bottom, double right) {
        return new Margins(Units.mmToPoints(top), Units.mmToPoints(left), Units.mmToPoints(bottom),
                Units.mmToPoints(right));
    }
}
