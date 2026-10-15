package org.datazuul.amadeos.writepad.domain;

/** Conversions between the units documents use. Layout works in points (1/72 inch). */
public final class Units {
    public static final double POINTS_PER_INCH = 72;
    public static final double MM_PER_INCH = 25.4;

    private Units() {
    }

    public static double mmToPoints(double mm) {
        return mm / MM_PER_INCH * POINTS_PER_INCH;
    }

    public static double pointsToMm(double points) {
        return points / POINTS_PER_INCH * MM_PER_INCH;
    }

    /** DOCX and RTF store lengths in twentieths of a point. */
    public static double twipsToPoints(int twips) {
        return twips / 20.0;
    }

    public static int pointsToTwips(double points) {
        return (int) Math.round(points * 20);
    }
}
