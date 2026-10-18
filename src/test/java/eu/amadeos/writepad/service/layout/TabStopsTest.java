package eu.amadeos.writepad.service.layout;

import org.junit.jupiter.api.Test;

import javax.swing.text.TabSet;
import javax.swing.text.TabStop;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TabStopsTest {
    private static final TabSet SET = new TabSet(new TabStop[]{new TabStop(100), new TabStop(200,
            TabStop.ALIGN_RIGHT, TabStop.LEAD_NONE)});

    private static float next(float position, TabSet tabs, float left, boolean first, float firstIndent) {
        return TabStops.next(position, tabs, left, first, firstIndent).getPosition();
    }

    @Test
    void defaultStopsEveryHalfInchFromTheMargin() {
        assertEquals(36f, next(0, null, 0, true, 0));
        assertEquals(72f, next(36, null, 0, true, 0));
        assertEquals(72f, next(50, null, 0, true, 0));
        assertEquals(108f, next(71.995f, null, 0, true, 0));
    }

    @Test
    void setStopsComeFirstThenDefaultsAfterTheLast() {
        assertEquals(100f, next(10, SET, 0, true, 0));
        TabStop right = TabStops.next(100, SET, 0, true, 0);
        assertEquals(200f, right.getPosition());
        assertEquals(TabStop.ALIGN_RIGHT, right.getAlignment());
        assertEquals(216f, next(200, SET, 0, true, 0));
        assertEquals(216f, next(205, SET, 0, true, 0));
    }

    @Test
    void hangingIndentIsAStopOnTheFirstLine() {
        assertEquals(36f, next(14, null, 36, true, -18));
        assertEquals(36f, next(14, SET, 36, true, -18), "before the first set stop");
        assertEquals(100f, next(14, SET, 36, false, -18), "not on later lines");
        assertEquals(72f, next(40, null, 36, true, -18), "only before it");
        assertEquals(36f, next(14, null, 36, true, 0) , "no hanging indent: default stop");
    }

    @Test
    void emptySetMeansDefaults() {
        assertEquals(36f, next(5, new TabSet(new TabStop[0]), 0, true, 0));
    }
}
