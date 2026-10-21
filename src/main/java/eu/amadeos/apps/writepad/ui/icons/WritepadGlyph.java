package eu.amadeos.apps.writepad.ui.icons;

import eu.amadeos.commons.swing.StandardGlyph;
import eu.amadeos.commons.swing.ToolIcon;

import java.awt.Font;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/**
 * The toolbar glyphs only a word processor needs; open, save, print, cut, undo and the other common ones
 * come from {@link StandardGlyph}, the text colour from {@link TextColorGlyph}.
 */
public enum WritepadGlyph implements ToolIcon.Glyph {
    PREVIEW, BOLD, ITALIC, UNDERLINE, STRIKE_THROUGH,
    ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT, ALIGN_JUSTIFY, BULLETS, NUMBERING;

    @Override
    public void paint(ToolIcon.Pen pen) {
        switch (this) {
            case PREVIEW -> {
                pen.page(1.5, 1.5, 9, 12);
                pen.line(3.5, 5.5, 7.5, 5.5);
                pen.line(3.5, 7.5, 6.5, 7.5);
                pen.magnifier(10, 10, 3.5);
            }
            case BOLD -> pen.letter("B", new Font(Font.SERIF, Font.BOLD, 14), 13.5);
            case ITALIC -> pen.letter("I", new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 14), 13.5);
            case UNDERLINE -> {
                pen.letter("U", new Font(Font.SERIF, Font.PLAIN, 13), 12);
                pen.line(3, 14.5, 13, 14.5);
            }
            case STRIKE_THROUGH -> {
                pen.letter("S", new Font(Font.SERIF, Font.PLAIN, 14), 13.5);
                pen.line(2, 8.5, 14, 8.5);
            }
            case ALIGN_LEFT -> lines(pen, 0);
            case ALIGN_CENTER -> lines(pen, 1);
            case ALIGN_RIGHT -> lines(pen, 2);
            case ALIGN_JUSTIFY -> lines(pen, 3);
            case BULLETS -> list(pen, false);
            case NUMBERING -> list(pen, true);
        }
    }

    /** Four lines: 0 left, 1 centred, 2 right, 3 justified. */
    private static void lines(ToolIcon.Pen pen, int alignment) {
        double[] widths = alignment == 3 ? new double[]{12, 12, 12, 12} : new double[]{12, 8, 12, 7};
        for (int i = 0; i < widths.length; i++) {
            double w = widths[i];
            double x = switch (alignment) {
                case 1 -> 8 - w / 2;
                case 2 -> 14 - w;
                default -> 2;
            };
            pen.fill(new Rectangle2D.Double(x, 2.5 + i * 3.5, w, 1.4), pen.ink());
        }
    }

    private static void list(ToolIcon.Pen pen, boolean numbered) {
        Font digits = new Font(Font.SANS_SERIF, Font.BOLD, 5);
        for (int i = 0; i < 3; i++) {
            double y = 3.5 + i * 4.5;
            if (numbered) {
                pen.g().setColor(pen.ink());
                pen.g().setFont(digits);
                pen.g().drawString(String.valueOf(i + 1), 1.5f, (float) (y + 2));
            } else {
                pen.fill(new Ellipse2D.Double(2, y - 1.2, 2.6, 2.6), pen.ink());
            }
            pen.fill(new Rectangle2D.Double(6.5, y - 0.6, 7.5, 1.4), pen.ink());
        }
    }
}
