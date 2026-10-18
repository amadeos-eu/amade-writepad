package eu.amadeos.writepad.ui.ruler;

import eu.amadeos.writepad.ui.editor.TextFormatting.Indents;

import javax.swing.text.TabStop;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What the ruler shows and how dragging changes it, without Swing. All positions are in points from the
 * left margin, as the indents and tab stops of the document.
 *
 * <p>The markers work as in WordPad and Word: the triangle at the top is where the first line starts, the
 * triangle at the bottom is the left indent of the other lines (the first line keeps its place), the box
 * below it moves both, the triangle on the right is the right indent.
 */
public final class RulerModel {
    /** Text keeps at least this width between the indents. */
    public static final float MIN_TEXT = 18f;

    public enum Handle {FIRST_LINE, LEFT, LEFT_AND_FIRST_LINE, RIGHT, TAB, NONE}

    /** What the mouse is over: a handle, and for {@link Handle#TAB} which stop. */
    public record Hit(Handle handle, int tab) {
        static final Hit NONE = new Hit(Handle.NONE, -1);
    }

    private final float textWidth;
    private Indents indents;
    private List<TabStop> tabs;

    /**
     * @param textWidth width between the page margins, in points
     */
    public RulerModel(float textWidth, Indents indents, List<TabStop> tabs) {
        this.textWidth = textWidth;
        this.indents = Objects.requireNonNull(indents, "indents");
        this.tabs = List.copyOf(tabs);
    }

    public float textWidth() {
        return textWidth;
    }

    public Indents indents() {
        return indents;
    }

    public List<TabStop> tabs() {
        return tabs;
    }

    /** Where the right indent marker sits, from the left margin. */
    public float rightMarker() {
        return textWidth - indents.right();
    }

    /**
     * The handle at a position.
     *
     * @param x         points from the left margin
     * @param upperHalf true over the upper part of the ruler (first-line marker), false over the lower part
     * @param below     true over the box under the left indent marker
     * @param tolerance how near counts as on a marker, in points
     */
    public Hit hit(float x, boolean upperHalf, boolean below, float tolerance) {
        if (Math.abs(x - indents.left()) <= tolerance && below) return new Hit(Handle.LEFT_AND_FIRST_LINE, -1);
        if (upperHalf && Math.abs(x - indents.firstLine()) <= tolerance) return new Hit(Handle.FIRST_LINE, -1);
        if (!upperHalf && Math.abs(x - indents.left()) <= tolerance) return new Hit(Handle.LEFT, -1);
        if (!upperHalf && Math.abs(x - rightMarker()) <= tolerance) return new Hit(Handle.RIGHT, -1);
        for (int i = 0; i < tabs.size(); i++) {
            if (Math.abs(x - tabs.get(i).getPosition()) <= tolerance) return new Hit(Handle.TAB, i);
        }
        if (upperHalf && Math.abs(x - indents.left()) <= tolerance) return new Hit(Handle.LEFT, -1);
        return Hit.NONE;
    }

    /** Rounds to the nearest multiple of the step (no rounding for a step of 0). */
    public static float snap(float x, double step) {
        if (step <= 0) return x;
        return (float) (Math.round(x / step) * step);
    }

    /** Moves a handle to a position (already snapped); the other markers stay where they are. */
    public void drag(Hit hit, float x) {
        float right = rightMarker();
        switch (hit.handle()) {
            case FIRST_LINE -> indents = new Indents(indents.left(), clamp(x, 0, right - MIN_TEXT), indents.right());
            case LEFT -> {
                float left = clamp(x, 0, right - MIN_TEXT);
                indents = new Indents(left, indents.firstLine(), indents.right());
            }
            case LEFT_AND_FIRST_LINE -> {
                float shift = x - indents.left();
                float lowest = Math.min(indents.left(), indents.firstLine());
                float highest = Math.max(indents.left(), indents.firstLine());
                shift = clamp(shift, -lowest, right - MIN_TEXT - highest);
                indents = new Indents(indents.left() + shift, indents.firstLine() + shift, indents.right());
            }
            case RIGHT -> {
                float marker = clamp(x, Math.max(indents.left(), indents.firstLine()) + MIN_TEXT, textWidth);
                indents = new Indents(indents.left(), indents.firstLine(), textWidth - marker);
            }
            case TAB -> {
                List<TabStop> moved = new ArrayList<>(tabs);
                TabStop old = moved.get(hit.tab());
                moved.set(hit.tab(), new TabStop(clamp(x, 0, textWidth), old.getAlignment(), old.getLeader()));
                tabs = List.copyOf(moved);
            }
            case NONE -> {
            }
        }
    }

    /** Adds a stop (a click on the ruler); returns its index. */
    public int addTab(float x, int alignment) {
        List<TabStop> more = new ArrayList<>(tabs);
        more.add(new TabStop(clamp(x, 0, textWidth), alignment, TabStop.LEAD_NONE));
        tabs = List.copyOf(more);
        return more.size() - 1;
    }

    /** Removes a stop (dragged off the ruler). */
    public void removeTab(int index) {
        List<TabStop> fewer = new ArrayList<>(tabs);
        fewer.remove(index);
        tabs = List.copyOf(fewer);
    }

    /** Tab stops sorted by position, as the document stores them. */
    public List<TabStop> sortedTabs() {
        List<TabStop> sorted = new ArrayList<>(tabs);
        sorted.sort((a, b) -> Float.compare(a.getPosition(), b.getPosition()));
        return sorted;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
