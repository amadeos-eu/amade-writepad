package org.datazuul.amadeos.writepad.service.layout;

import javax.swing.text.TabSet;
import javax.swing.text.TabStop;

/**
 * Where a tab character goes, as Word, WordPad and LibreOffice place it. Positions are in points from the
 * left margin, as RTF stores them, not from the paragraph's indent as in Swing.
 *
 * <ul>
 *     <li>the next tab stop set for the paragraph after the current position;</li>
 *     <li>on the first line of a paragraph with a hanging indent, the left indent counts as a stop, so
 *     "1.&#9;Text" lines up with the following lines;</li>
 *     <li>after the last stop set, default stops every {@link #DEFAULT_INTERVAL} points from the margin.</li>
 * </ul>
 */
public final class TabStops {
    /** Half an inch (1.27 cm), RTF's {@code \deftab720} and WordPad's default. */
    public static final float DEFAULT_INTERVAL = 36f;
    private static final float EPSILON = 0.01f;

    private TabStops() {
    }

    /**
     * The stop after a position.
     *
     * @param position     where the text before the tab ends, in points from the left margin
     * @param tabs         the paragraph's stops, may be null
     * @param leftIndent   the paragraph's left indent
     * @param firstLine    true on the first line of the paragraph
     * @param firstIndent  the first line's indent relative to the left indent (negative for hanging)
     */
    public static TabStop next(float position, TabSet tabs, float leftIndent, boolean firstLine, float firstIndent) {
        TabStop set = tabs == null ? null : tabs.getTabAfter(position + EPSILON);
        TabStop hanging = firstLine && firstIndent < 0 && leftIndent > position + EPSILON
                ? new TabStop(leftIndent) : null;
        if (set != null && hanging != null) return set.getPosition() <= hanging.getPosition() ? set : hanging;
        if (set != null) return set;
        if (hanging != null) return hanging;
        float last = 0;
        if (tabs != null && tabs.getTabCount() > 0) last = tabs.getTab(tabs.getTabCount() - 1).getPosition();
        float from = Math.max(position, last);
        float stop = (float) ((Math.floor((from + EPSILON) / DEFAULT_INTERVAL) + 1) * DEFAULT_INTERVAL);
        return new TabStop(stop);
    }
}
