package eu.amadeos.writepad.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The unit of the ruler and the paragraph and tabs dialogs: centimetres, or inches where people measure in
 * inches.
 */
public enum MeasurementUnit {
    CENTIMETRE("cm", Units.mmToPoints(10), 4, 2),
    INCH("\"", Units.POINTS_PER_INCH, 8, 2);

    private static final Set<String> INCH_COUNTRIES = Set.of("US", "LR", "MM");

    private final String symbol;
    private final double points;
    private final int ticks;
    private final int decimals;

    MeasurementUnit(String symbol, double points, int ticks, int decimals) {
        this.symbol = symbol;
        this.points = points;
        this.ticks = ticks;
        this.decimals = decimals;
    }

    /** Inches in the United States, Liberia and Myanmar, centimetres everywhere else. */
    public static MeasurementUnit of(Locale locale) {
        return INCH_COUNTRIES.contains(locale.getCountry()) ? INCH : CENTIMETRE;
    }

    /**
     * The unit for the region the user lives in: the country of {@code LC_MEASUREMENT}, {@code LC_ALL} or
     * {@code LANG} (e.g. {@code en_US.UTF-8}), else of the locale. The application's language alone does not
     * tell: English is measured in centimetres in Britain.
     */
    public static MeasurementUnit fromEnvironment(Map<String, String> environment, Locale locale) {
        for (String variable : new String[]{"LC_MEASUREMENT", "LC_ALL", "LANG"}) {
            String value = environment.get(variable);
            if (value == null) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^[a-z]{2,3}_([A-Z]{2})").matcher(value);
            if (m.find()) return INCH_COUNTRIES.contains(m.group(1)) ? INCH : CENTIMETRE;
        }
        return of(locale);
    }

    public String symbol() {
        return symbol;
    }

    /** Points in one unit. */
    public double points() {
        return points;
    }

    /** Small ruler marks per unit: quarter centimetres, eighth inches. */
    public int ticks() {
        return ticks;
    }

    /** The step indents and tabs snap to on the ruler, in points. */
    public double snap() {
        return points / ticks;
    }

    /** Decimal places shown in dialogs. */
    public int decimals() {
        return decimals;
    }

    public double toUnit(double pointsValue) {
        return pointsValue / points;
    }

    public double toPoints(double unitValue) {
        return unitValue * points;
    }
}
