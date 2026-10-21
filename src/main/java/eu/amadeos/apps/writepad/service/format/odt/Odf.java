package eu.amadeos.apps.writepad.service.format.odt;

import java.util.Locale;
import java.util.Map;

/** Namespaces and lengths of the OpenDocument format. */
final class Odf {
    static final String MIME_TYPE = "application/vnd.oasis.opendocument.text";
    static final String VERSION = "1.3";

    static final String OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0";
    static final String STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0";
    static final String TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";
    static final String TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";
    static final String DRAW = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0";
    static final String FO = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0";
    static final String SVG = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0";
    static final String META = "urn:oasis:names:tc:opendocument:xmlns:meta:1.0";
    static final String CONFIG = "urn:oasis:names:tc:opendocument:xmlns:config:1.0";
    static final String MANIFEST = "urn:oasis:names:tc:opendocument:xmlns:manifest:1.0";
    static final String XML = "http://www.w3.org/XML/1998/namespace";

    /** The prefixes amade-writepad writes, and by which it keys the formatting properties it reads. */
    static final Map<String, String> PREFIXES = Map.of(
            "office", OFFICE, "style", STYLE, "text", TEXT, "table", TABLE, "draw", DRAW, "fo", FO, "svg", SVG,
            "meta", META, "config", CONFIG);

    private Odf() {
    }

    /** The usual prefix of a namespace, or null for namespaces whose properties don't matter here. */
    static String prefix(String namespace) {
        for (Map.Entry<String, String> entry : PREFIXES.entrySet()) {
            if (entry.getValue().equals(namespace)) return entry.getKey();
        }
        return null;
    }

    /**
     * A length such as {@code 2.5cm}, {@code 12pt} or {@code 0.5in} in points; NaN if it is no absolute length
     * (empty, a percentage, a unit relative to the font).
     */
    static double points(String length) {
        if (length == null) return Double.NaN;
        String s = length.strip().toLowerCase(Locale.ROOT);
        int unit = s.length();
        while (unit > 0 && Character.isLetter(s.charAt(unit - 1))) unit--;
        double value;
        try {
            value = Double.parseDouble(s.substring(0, unit));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
        return switch (s.substring(unit)) {
            case "pt" -> value;
            case "cm" -> value / 2.54 * 72;
            case "mm" -> value / 25.4 * 72;
            case "in", "inch" -> value * 72;
            case "pc" -> value * 12;
            case "px" -> value * 0.75;
            default -> Double.NaN;
        };
    }

    /** A percentage such as {@code 115%} as a number (115); NaN if it is none. */
    static double percent(String value) {
        if (value == null || !value.strip().endsWith("%")) return Double.NaN;
        try {
            return Double.parseDouble(value.strip().substring(0, value.strip().length() - 1));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Points as centimetres, as LibreOffice writes lengths in Europe. */
    static String cm(double points) {
        String s = String.format(Locale.ROOT, "%.4f", points / 72 * 2.54);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return (s.equals("-0") ? "0" : s) + "cm";
    }

    static String pt(double points) {
        String s = String.format(Locale.ROOT, "%.2f", points).replaceAll("0+$", "").replaceAll("\\.$", "");
        return s + "pt";
    }
}
