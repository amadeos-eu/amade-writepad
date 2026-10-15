package org.datazuul.amadeos.writepad.ui.ruler;

import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.commons.swing.AmadeUI;
import org.datazuul.amadeos.commons.swing.EdtExecutor;
import org.datazuul.amadeos.commons.theme.DesktopTheme;
import org.datazuul.amadeos.writepad.domain.MeasurementUnit;
import org.datazuul.amadeos.writepad.service.layout.PageLayout;
import org.datazuul.amadeos.writepad.ui.editor.PageEditor;
import org.datazuul.amadeos.writepad.ui.editor.TextFormatting.Indents;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import javax.swing.text.TabStop;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;

/**
 * The ruler above the page, as in WordPad and GeoWorks Write: the page with its margins, a scale in the
 * user's unit from the left margin, the indent markers and tab stops of the paragraph at the caret. Markers
 * are dragged with the mouse (snapping to quarter centimetres or eighth inches, Alt for free placement), a
 * click on the scale sets a tab stop of the kind shown in the box on the left, dragging a stop off the ruler
 * removes it. Changes go to the {@link Listener} when the mouse is released.
 *
 * <p>Lives in the editor's scroll pane as column header, so it uses the editor's x coordinates.
 */
public final class Ruler extends JComponent {
    public static final int HEIGHT = 28;
    /** The white scale. */
    static final int BAND_TOP = 4;
    static final int BAND_BOTTOM = 18;
    /** The box under the left indent marker. */
    static final int BOX_TOP = 19;
    static final int BOX_BOTTOM = 24;
    static final int MIDDLE = 11;
    /** Half the width of a marker, in pixels. */
    static final int MARKER = 5;
    /** The tab type box at the left end. */
    static final Rectangle SELECTOR = new Rectangle(3, 5, 17, 17);
    private static final int REMOVE_DISTANCE = 12;
    private static final Color MARGIN = new Color(0xC8C8C8);
    private static final int[] TAB_TYPES = {TabStop.ALIGN_LEFT, TabStop.ALIGN_CENTER, TabStop.ALIGN_RIGHT,
            TabStop.ALIGN_DECIMAL};

    /** Receives what the user changed on the ruler. */
    public interface Listener {
        void indentsChanged(Indents indents);

        void tabsChanged(List<TabStop> tabs);
    }

    private final PageEditor editor;
    private final PageLayout layout;
    private final MeasurementUnit unit;
    private final Messages msg;
    private Listener listener = new Listener() {
        @Override
        public void indentsChanged(Indents indents) {
        }

        @Override
        public void tabsChanged(List<TabStop> tabs) {
        }
    };
    private RulerModel model;
    private int tabType = TabStop.ALIGN_LEFT;
    // A drag in progress
    private RulerModel.Hit dragging;
    private boolean removing;

    public Ruler(PageEditor editor, MeasurementUnit unit, Messages msg) {
        EdtExecutor.requireEdt();
        this.editor = Objects.requireNonNull(editor, "editor");
        this.layout = editor.pageLayout();
        this.unit = Objects.requireNonNull(unit, "unit");
        this.msg = msg;
        setName("ruler");
        setOpaque(true);
        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        model = new RulerModel(layout.contentWidth(), new Indents(0, 0, 0), List.of());
        editor.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                revalidate();
                repaint();
            }
        });
        layout.addChangeListener(() -> {
            model = new RulerModel(layout.contentWidth(), model.indents(), model.tabs());
            revalidate();
            repaint();
        });
        Mouse mouse = new Mouse();
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    public void setListener(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /** Shows the indents and stops of the paragraph at the caret; ignored while the user drags. */
    public void show(Indents indents, List<TabStop> tabs) {
        if (dragging != null) return;
        model = new RulerModel(layout.contentWidth(), indents, tabs);
        repaint();
    }

    public RulerModel model() {
        return model;
    }

    /** The kind of stop a click sets: {@link TabStop#ALIGN_LEFT}, CENTER, RIGHT or DECIMAL. */
    public int tabType() {
        return tabType;
    }

    public void nextTabType() {
        int i = 0;
        while (TAB_TYPES[i] != tabType) i++;
        tabType = TAB_TYPES[(i + 1) % TAB_TYPES.length];
        repaint(SELECTOR);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Math.max(editor.getWidth(), editor.getPreferredSize().width), HEIGHT);
    }

    // ---------------------------------------------------------------- coordinates

    /** Pixel x of a position in points from the left margin. */
    double screenX(float points) {
        return editor.textColumnX() + points * layout.zoom();
    }

    /** Position in points from the left margin of a pixel x. */
    float points(int x) {
        return (float) ((x - editor.textColumnX()) / layout.zoom());
    }

    // ---------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g) {
        DesktopTheme theme = AmadeUI.theme();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(theme.base());
            g2.fillRect(0, 0, getWidth(), getHeight());
            double zoom = layout.zoom();
            int pageLeft = (int) Math.round(screenX(-layout.left()));
            int pageRight = (int) Math.round(screenX(layout.pageWidth() - layout.left()));
            int textLeft = (int) Math.round(screenX(0));
            int textRight = (int) Math.round(screenX(layout.contentWidth()));
            int bandHeight = BAND_BOTTOM - BAND_TOP;
            // Margins grey, the text column white, as on paper: the same in every theme, like the page.
            g2.setColor(MARGIN);
            g2.fillRect(pageLeft, BAND_TOP, pageRight - pageLeft, bandHeight);
            g2.setColor(Color.WHITE);
            g2.fillRect(textLeft, BAND_TOP, textRight - textLeft, bandHeight);
            AmadeUI.paintBevel(g2, pageLeft - 1, BAND_TOP - 1, pageRight - pageLeft + 2, bandHeight + 2, false, 1);
            paintScale(g2, zoom, pageLeft, pageRight);
            for (TabStop tab : model.tabs()) {
                if (dragging != null && dragging.handle() == RulerModel.Handle.TAB && removing
                        && tab == model.tabs().get(dragging.tab())) {
                    continue;
                }
                paintTab(g2, (int) Math.round(screenX(tab.getPosition())), BAND_BOTTOM - 1, tab.getAlignment(),
                        Color.BLACK);
            }
            Indents indents = model.indents();
            paintMarker(g2, theme, (int) Math.round(screenX(indents.firstLine())), true);
            int left = (int) Math.round(screenX(indents.left()));
            paintMarker(g2, theme, left, false);
            g2.setColor(theme.base());
            g2.fillRect(left - MARKER, BOX_TOP, 2 * MARKER + 1, BOX_BOTTOM - BOX_TOP);
            AmadeUI.paintBevel(g2, left - MARKER, BOX_TOP, 2 * MARKER + 1, BOX_BOTTOM - BOX_TOP, true, 1);
            paintMarker(g2, theme, (int) Math.round(screenX(model.rightMarker())), false);
            paintSelector(g2, theme);
        } finally {
            g2.dispose();
        }
    }

    private void paintScale(Graphics2D g, double zoom, int pageLeft, int pageRight) {
        g.setFont(getFont());
        FontMetrics fm = g.getFontMetrics();
        double step = unit.points() / unit.ticks() * zoom;
        int ticks = unit.ticks();
        int first = (int) Math.ceil((pageLeft - screenX(0)) / step);
        int last = (int) Math.floor((pageRight - screenX(0)) / step);
        for (int k = first; k <= last; k++) {
            int x = (int) Math.round(screenX(0) + k * step);
            if (x <= pageLeft || x >= pageRight) continue;
            g.setColor(Color.BLACK);
            if (k % ticks == 0) {
                if (k == 0) continue;
                String label = String.valueOf(Math.abs(k / ticks));
                int width = fm.stringWidth(label);
                g.drawString(label, x - width / 2, BAND_TOP + (BAND_BOTTOM - BAND_TOP + fm.getAscent()) / 2 - 1);
            } else if (k % (ticks / 2) == 0) {
                g.drawLine(x, BAND_TOP + 4, x, BAND_BOTTOM - 5);
            } else {
                g.drawLine(x, BAND_TOP + 6, x, BAND_BOTTOM - 7);
            }
        }
    }

    /** An indent marker: pointing down at the top (first line), up at the bottom (left, right). */
    private static void paintMarker(Graphics2D g, DesktopTheme theme, int x, boolean top) {
        Polygon p = new Polygon();
        if (top) {
            p.addPoint(x - MARKER, 1);
            p.addPoint(x + MARKER, 1);
            p.addPoint(x, 1 + MARKER + 1);
        } else {
            p.addPoint(x - MARKER, BAND_BOTTOM);
            p.addPoint(x + MARKER, BAND_BOTTOM);
            p.addPoint(x, BAND_BOTTOM - MARKER - 1);
        }
        g.setColor(theme.base());
        g.fillPolygon(p);
        g.setColor(theme.darkShadow());
        g.drawPolygon(p);
        g.setColor(theme.highlight());
        if (top) g.drawLine(x - MARKER + 1, 2, x + MARKER - 1, 2);
        else g.drawLine(x - 1, BAND_BOTTOM - MARKER + 1, x - MARKER + 2, BAND_BOTTOM - 1);
    }

    /** The tab stop glyphs of WordPad and Word: └ left, ┴ centre, ┘ right, ┴ with a dot decimal. */
    static void paintTab(Graphics2D g, int x, int baseline, int alignment, Color color) {
        g.setColor(color);
        g.drawLine(x, baseline - 5, x, baseline);
        switch (alignment) {
            case TabStop.ALIGN_RIGHT -> g.drawLine(x - 5, baseline, x, baseline);
            case TabStop.ALIGN_CENTER -> g.drawLine(x - 4, baseline, x + 4, baseline);
            case TabStop.ALIGN_DECIMAL -> {
                g.drawLine(x - 4, baseline, x + 4, baseline);
                g.fillRect(x + 2, baseline - 4, 2, 2);
            }
            default -> g.drawLine(x, baseline, x + 5, baseline);
        }
        // Two pixels wide, so the glyph reads at a glance.
        g.translate(1, 0);
        g.drawLine(x, baseline - 5, x, baseline);
        g.translate(-1, 0);
    }

    private void paintSelector(Graphics2D g, DesktopTheme theme) {
        Rectangle r = SELECTOR;
        g.setColor(theme.base());
        g.fillRect(r.x, r.y, r.width, r.height);
        AmadeUI.paintBevel(g, r.x, r.y, r.width, r.height, true, 1);
        paintTab(g, r.x + r.width / 2 - (tabType == TabStop.ALIGN_LEFT ? 2 : 0), r.y + r.height - 5, tabType,
                theme.textActive());
    }

    // ---------------------------------------------------------------- tooltips and mouse

    @Override
    public String getToolTipText(MouseEvent e) {
        if (SELECTOR.contains(e.getPoint())) return msg.format("ruler.tab-type", tabName(tabType));
        RulerModel.Hit hit = hitAt(e.getX(), e.getY());
        return switch (hit.handle()) {
            case FIRST_LINE -> msg.get("ruler.first-line");
            case LEFT -> msg.get("ruler.hanging");
            case LEFT_AND_FIRST_LINE -> msg.get("ruler.left");
            case RIGHT -> msg.get("ruler.right");
            case TAB -> tabName(model.tabs().get(hit.tab()).getAlignment());
            case NONE -> e.getY() >= BAND_TOP && e.getY() <= BAND_BOTTOM ? msg.get("ruler.click-for-tab") : null;
        };
    }

    private String tabName(int alignment) {
        return msg.get(switch (alignment) {
            case TabStop.ALIGN_CENTER -> "tab.center";
            case TabStop.ALIGN_RIGHT -> "tab.right";
            case TabStop.ALIGN_DECIMAL -> "tab.decimal";
            default -> "tab.left";
        });
    }

    RulerModel.Hit hitAt(int x, int y) {
        float tolerance = (float) ((MARKER + 1) / layout.zoom());
        return model.hit(points(x), y < MIDDLE, y >= BOX_TOP, tolerance);
    }

    private final class Mouse extends MouseAdapter {
        private Indents before;

        @Override
        public void mousePressed(MouseEvent e) {
            if (e.getButton() != MouseEvent.BUTTON1) return;
            if (SELECTOR.contains(e.getPoint())) {
                nextTabType();
                return;
            }
            RulerModel.Hit hit = hitAt(e.getX(), e.getY());
            float x = points(e.getX());
            if (hit.handle() == RulerModel.Handle.NONE) {
                if (e.getY() < BAND_TOP || e.getY() > BAND_BOTTOM || x < 0 || x > model.textWidth()) return;
                int index = model.addTab(snapped(x, e), tabType);
                hit = new RulerModel.Hit(RulerModel.Handle.TAB, index);
            }
            before = model.indents();
            dragging = hit;
            removing = false;
            drag(e);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (dragging != null) drag(e);
        }

        private void drag(MouseEvent e) {
            model.drag(dragging, snapped(points(e.getX()), e));
            removing = dragging.handle() == RulerModel.Handle.TAB
                    && (e.getY() < -REMOVE_DISTANCE || e.getY() > getHeight() + REMOVE_DISTANCE);
            editor.setGuide(removing ? Double.NaN : screenX(position(dragging)));
            repaint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (dragging == null) return;
            RulerModel.Hit done = dragging;
            dragging = null;
            editor.setGuide(Double.NaN);
            if (done.handle() == RulerModel.Handle.TAB) {
                if (removing) model.removeTab(done.tab());
                removing = false;
                listener.tabsChanged(model.sortedTabs());
            } else if (!model.indents().equals(before)) {
                listener.indentsChanged(model.indents());
            }
            repaint();
        }

        private float snapped(float x, MouseEvent e) {
            return e.isAltDown() ? x : RulerModel.snap(x, unit.snap());
        }
    }

    private float position(RulerModel.Hit hit) {
        Indents indents = model.indents();
        return switch (hit.handle()) {
            case FIRST_LINE -> indents.firstLine();
            case LEFT, LEFT_AND_FIRST_LINE -> indents.left();
            case RIGHT -> model.rightMarker();
            case TAB -> model.tabs().get(hit.tab()).getPosition();
            case NONE -> 0;
        };
    }
}
