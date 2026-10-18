package eu.amadeos.writepad.ui.icons;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Toolbar icons drawn in Java2D on a 16 × 16 grid, so they need no icon theme, scale to any size and
 * follow the desktop theme: lines in the button's text colour (the inactive text colour when the button is
 * disabled), paper in the theme's field colour.
 */
public final class ToolIcon implements Icon {
    /**
     * Size in toolbars at 100 % scaling, as large as the icons of LibreOffice's toolbars. The glyphs are
     * drawn on a 16-unit grid and scaled to this size.
     */
    public static final int SIZE = 22;

    public enum Glyph {
        NEW, OPEN, SAVE, PRINT, PREVIEW, CUT, COPY, PASTE, UNDO, REDO, FIND,
        BOLD, ITALIC, UNDERLINE, STRIKE_THROUGH,
        ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT, ALIGN_JUSTIFY, BULLETS, NUMBERING, TEXT_COLOR
    }

    private final Glyph glyph;
    private final int size;
    private final Supplier<Color> swatch;

    public ToolIcon(Glyph glyph) {
        this(glyph, SIZE, () -> Color.BLACK);
    }

    /**
     * @param swatch colour of the bar under the {@link Glyph#TEXT_COLOR} "A"; asked on every paint
     */
    public ToolIcon(Glyph glyph, int size, Supplier<Color> swatch) {
        this.glyph = Objects.requireNonNull(glyph, "glyph");
        this.size = size;
        this.swatch = Objects.requireNonNull(swatch, "swatch");
    }

    public Glyph glyph() {
        return glyph;
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        DesktopTheme theme = AmadeUI.theme();
        Color ink = c != null && !c.isEnabled() ? theme.textInactive()
                : c != null && c.getForeground() != null ? c.getForeground() : theme.textActive();
        Color paper = theme.fieldBackground();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            g2.scale(size / 16.0, size / 16.0);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            new Painter(g2, ink, paper, c != null && !c.isEnabled() ? theme.textInactive() : swatch.get())
                    .paint(glyph);
        } finally {
            g2.dispose();
        }
    }

    /** Draws one glyph on the 16-unit grid; lines on half units so they are crisp at 100 %. */
    private record Painter(Graphics2D g, Color ink, Color paper, Color swatch) {
        void paint(Glyph glyph) {
            switch (glyph) {
                case NEW -> page(3.5, 1.5, 10, 13);
                case OPEN -> open();
                case SAVE -> save();
                case PRINT -> print();
                case PREVIEW -> preview();
                case CUT -> cut();
                case COPY -> {
                    page(1.5, 1.5, 8, 10);
                    page(6.5, 4.5, 8, 10);
                }
                case PASTE -> paste();
                case UNDO -> undo();
                case REDO -> {
                    g.translate(16, 0);
                    g.scale(-1, 1);
                    undo();
                }
                case FIND -> magnifier(6.5, 6.5, 4.5, 14.5);
                case BOLD -> letter("B", new Font(Font.SERIF, Font.BOLD, 14), 13.5);
                case ITALIC -> letter("I", new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 14), 13.5);
                case UNDERLINE -> {
                    letter("U", new Font(Font.SERIF, Font.PLAIN, 13), 12);
                    line(3, 14.5, 13, 14.5);
                }
                case STRIKE_THROUGH -> {
                    letter("S", new Font(Font.SERIF, Font.PLAIN, 14), 13.5);
                    line(2, 8.5, 14, 8.5);
                }
                case ALIGN_LEFT -> lines(0);
                case ALIGN_CENTER -> lines(1);
                case ALIGN_RIGHT -> lines(2);
                case ALIGN_JUSTIFY -> lines(3);
                case BULLETS -> list(false);
                case NUMBERING -> list(true);
                case TEXT_COLOR -> textColor();
            }
        }

        private void fillAndDraw(Shape shape, Color fill) {
            g.setColor(fill);
            g.fill(shape);
            g.setColor(ink);
            g.draw(shape);
        }

        private void line(double x1, double y1, double x2, double y2) {
            g.setColor(ink);
            g.draw(new Line2D.Double(x1, y1, x2, y2));
        }

        /** A sheet with a folded corner. */
        private void page(double x, double y, double w, double h) {
            double fold = Math.min(3, w / 3);
            Path2D sheet = new Path2D.Double();
            sheet.moveTo(x, y);
            sheet.lineTo(x + w - fold, y);
            sheet.lineTo(x + w, y + fold);
            sheet.lineTo(x + w, y + h);
            sheet.lineTo(x, y + h);
            sheet.closePath();
            fillAndDraw(sheet, paper);
            Path2D corner = new Path2D.Double();
            corner.moveTo(x + w - fold, y);
            corner.lineTo(x + w - fold, y + fold);
            corner.lineTo(x + w, y + fold);
            g.setColor(ink);
            g.draw(corner);
        }

        private void open() {
            Path2D back = new Path2D.Double();
            back.moveTo(1.5, 13.5);
            back.lineTo(1.5, 3.5);
            back.lineTo(5.5, 3.5);
            back.lineTo(7, 5.5);
            back.lineTo(12.5, 5.5);
            back.lineTo(12.5, 8.5);
            fillAndDraw(back, paper);
            Path2D front = new Path2D.Double();
            front.moveTo(1.5, 13.5);
            front.lineTo(4, 8.5);
            front.lineTo(15, 8.5);
            front.lineTo(12.5, 13.5);
            front.closePath();
            fillAndDraw(front, ink);
        }

        private void save() {
            Path2D disk = new Path2D.Double();
            disk.moveTo(1.5, 1.5);
            disk.lineTo(12.5, 1.5);
            disk.lineTo(14.5, 3.5);
            disk.lineTo(14.5, 14.5);
            disk.lineTo(1.5, 14.5);
            disk.closePath();
            fillAndDraw(disk, ink);
            g.setColor(paper);
            g.fill(new Rectangle2D.Double(4, 2, 7, 4));
            g.fill(new Rectangle2D.Double(3.5, 8.5, 9, 6));
            g.setColor(ink);
            g.fill(new Rectangle2D.Double(8, 2.5, 2, 3));
            line(5, 10.5, 11, 10.5);
            line(5, 12.5, 11, 12.5);
        }

        private void print() {
            fillAndDraw(new Rectangle2D.Double(4.5, 1.5, 7, 5), paper);
            fillAndDraw(new Rectangle2D.Double(1.5, 6.5, 13, 6), ink);
            fillAndDraw(new Rectangle2D.Double(4.5, 10.5, 7, 4), paper);
            g.setColor(paper);
            g.fill(new Rectangle2D.Double(11.5, 8, 1.5, 1.5));
        }

        private void preview() {
            page(1.5, 1.5, 9, 12);
            line(3.5, 5.5, 7.5, 5.5);
            line(3.5, 7.5, 6.5, 7.5);
            magnifier(10, 10, 3.5, 15);
        }

        private void magnifier(double cx, double cy, double r, double end) {
            Ellipse2D lens = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
            fillAndDraw(lens, paper);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double edge = r * Math.sqrt(0.5);
            line(cx + edge, cy + edge, end - 0.5, end - 0.5);
            g.setStroke(new BasicStroke(1f));
        }

        private void cut() {
            g.setColor(ink);
            g.draw(new Ellipse2D.Double(1.5, 10.5, 4.5, 4.5));
            g.draw(new Ellipse2D.Double(10, 10.5, 4.5, 4.5));
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            line(5.3, 10.8, 11.5, 1.5);
            line(10.7, 10.8, 4.5, 1.5);
            g.setStroke(new BasicStroke(1f));
        }

        private void paste() {
            fillAndDraw(new Rectangle2D.Double(1.5, 2.5, 10, 12), ink);
            fillAndDraw(new Rectangle2D.Double(4.5, 1.5, 4, 2.5), paper);
            page(6.5, 5.5, 8, 9.5);
        }

        private void undo() {
            g.setColor(ink);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D arc = new Path2D.Double();
            arc.moveTo(5, 6.5);
            arc.lineTo(9.5, 6.5);
            arc.curveTo(13.5, 6.5, 14.5, 9.5, 14, 11);
            arc.curveTo(13.5, 13, 11.5, 13.5, 9, 13.5);
            g.draw(arc);
            g.setStroke(new BasicStroke(1f));
            Path2D head = new Path2D.Double();
            head.moveTo(1, 6.5);
            head.lineTo(6, 2.5);
            head.lineTo(6, 10.5);
            head.closePath();
            g.fill(head);
        }

        private void letter(String text, Font font, double baseline) {
            g.setColor(ink);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, (float) ((16 - fm.stringWidth(text)) / 2.0), (float) baseline);
        }

        /** Four lines: 0 left, 1 centred, 2 right, 3 justified. */
        private void lines(int alignment) {
            double[] widths = alignment == 3 ? new double[]{12, 12, 12, 12} : new double[]{12, 8, 12, 7};
            for (int i = 0; i < widths.length; i++) {
                double w = widths[i];
                double x = switch (alignment) {
                    case 1 -> 8 - w / 2;
                    case 2 -> 14 - w;
                    default -> 2;
                };
                g.setColor(ink);
                g.fill(new Rectangle2D.Double(x, 2.5 + i * 3.5, w, 1.4));
            }
        }

        private void list(boolean numbered) {
            g.setColor(ink);
            Font digits = new Font(Font.SANS_SERIF, Font.BOLD, 5);
            for (int i = 0; i < 3; i++) {
                double y = 3.5 + i * 4.5;
                if (numbered) {
                    g.setFont(digits);
                    g.drawString(String.valueOf(i + 1), 1.5f, (float) (y + 2));
                } else {
                    g.fill(new Ellipse2D.Double(2, y - 1.2, 2.6, 2.6));
                }
                g.fill(new Rectangle2D.Double(6.5, y - 0.6, 7.5, 1.4));
            }
        }

        private void textColor() {
            letter("A", new Font(Font.SERIF, Font.BOLD, 12), 10.5);
            fillAndDraw(new Rectangle2D.Double(2.5, 11.5, 11, 3), swatch);
        }
    }
}
