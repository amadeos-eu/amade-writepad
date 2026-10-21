package eu.amadeos.apps.writepad.service.list;

/**
 * How a list item is marked: a bullet, or a number in one of the usual styles. RTF stores the style as
 * {@code \levelnfc}, ODT as {@code style:num-format}.
 */
public enum ListFormat {
    BULLET(23, ""),
    DECIMAL(0, "1"),
    LOWER_LETTER(4, "a"),
    UPPER_LETTER(3, "A"),
    LOWER_ROMAN(2, "i"),
    UPPER_ROMAN(1, "I");

    /** Bullets of the levels, repeating: •, ◦, ▪. */
    private static final String[] BULLETS = {"•", "◦", "▪"};
    private static final ListFormat[] NUMBERING = {DECIMAL, LOWER_LETTER, LOWER_ROMAN};

    private final int rtfCode;
    private final String odfCode;

    ListFormat(int rtfCode, String odfCode) {
        this.rtfCode = rtfCode;
        this.odfCode = odfCode;
    }

    public boolean isBullet() {
        return this == BULLET;
    }

    /** {@code \levelnfc} of RTF. */
    public int rtfCode() {
        return rtfCode;
    }

    /** The format for an RTF {@code \levelnfc}; unknown numbering styles count as decimal. */
    public static ListFormat ofRtf(int code) {
        for (ListFormat format : values()) {
            if (format.rtfCode == code) return format;
        }
        return code == 255 || code == 23 ? BULLET : DECIMAL;
    }

    /** {@code style:num-format} of ODF; empty for bullets. */
    public String odfCode() {
        return odfCode;
    }

    /** The numbering style for an ODF {@code style:num-format}; other scripts' numbers count as decimal. */
    public static ListFormat ofOdf(String code) {
        for (ListFormat format : values()) {
            if (!format.isBullet() && format.odfCode.equals(code)) return format;
        }
        return DECIMAL;
    }

    /** Numbering of new numbered items by level, as in Word: 1., a., i., then again. */
    public static ListFormat numberingFor(int level) {
        return NUMBERING[Math.floorMod(level, NUMBERING.length)];
    }

    /** The mark of item {@code number} (from 1) at a level: "•", "3.", "c.", "iv.", ... */
    public String label(int number, int level) {
        return switch (this) {
            case BULLET -> BULLETS[Math.floorMod(level, BULLETS.length)];
            case DECIMAL -> number + ".";
            case LOWER_LETTER -> letters(number) + ".";
            case UPPER_LETTER -> letters(number).toUpperCase(java.util.Locale.ROOT) + ".";
            case LOWER_ROMAN -> roman(number).toLowerCase(java.util.Locale.ROOT) + ".";
            case UPPER_ROMAN -> roman(number) + ".";
        };
    }

    /** a … z, then aa … zz, aaa …, as Word counts. */
    static String letters(int number) {
        if (number < 1) return String.valueOf(number);
        char letter = (char) ('a' + (number - 1) % 26);
        return String.valueOf(letter).repeat((number - 1) / 26 + 1);
    }

    static String roman(int number) {
        if (number < 1 || number > 3999) return String.valueOf(number);
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder s = new StringBuilder();
        int rest = number;
        for (int i = 0; i < values.length; i++) {
            while (rest >= values[i]) {
                s.append(symbols[i]);
                rest -= values[i];
            }
        }
        return s.toString();
    }
}
