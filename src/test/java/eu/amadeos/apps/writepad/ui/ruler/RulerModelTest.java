package eu.amadeos.apps.writepad.ui.ruler;

import eu.amadeos.apps.writepad.ui.editor.TextFormatting.Indents;
import eu.amadeos.apps.writepad.ui.ruler.RulerModel.Handle;
import eu.amadeos.apps.writepad.ui.ruler.RulerModel.Hit;
import org.junit.jupiter.api.Test;

import javax.swing.text.TabStop;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RulerModelTest {
    private static final float WIDTH = 450;

    private RulerModel model(float left, float first, float right, TabStop... tabs) {
        return new RulerModel(WIDTH, new Indents(left, first, right), List.of(tabs));
    }

    @Test
    void findsTheHandles() {
        RulerModel m = model(36, 18, 72, new TabStop(200));
        assertEquals(Handle.FIRST_LINE, m.hit(19, true, false, 4).handle());
        assertEquals(Handle.LEFT, m.hit(37, false, false, 4).handle());
        assertEquals(Handle.LEFT_AND_FIRST_LINE, m.hit(36, false, true, 4).handle());
        assertEquals(Handle.RIGHT, m.hit(WIDTH - 72, false, false, 4).handle());
        assertEquals(new Hit(Handle.TAB, 0), m.hit(201, false, false, 4));
        assertEquals(Handle.NONE, m.hit(300, false, false, 4).handle());
        // Both triangles at the same place: the top one at the top, the bottom one below.
        RulerModel same = model(0, 0, 0);
        assertEquals(Handle.FIRST_LINE, same.hit(0, true, false, 4).handle());
        assertEquals(Handle.LEFT, same.hit(0, false, false, 4).handle());
    }

    @Test
    void leftMarkerKeepsTheFirstLineInPlace() {
        RulerModel m = model(36, 18, 0);
        m.drag(new Hit(Handle.LEFT, -1), 72);
        assertEquals(new Indents(72, 18, 0), m.indents());
        assertEquals(-54f, m.indents().firstLineOffset());
    }

    @Test
    void boxMovesBothAndStaysOnThePage() {
        RulerModel m = model(36, 18, 0);
        m.drag(new Hit(Handle.LEFT_AND_FIRST_LINE, -1), 72);
        assertEquals(new Indents(72, 54, 0), m.indents());
        m.drag(new Hit(Handle.LEFT_AND_FIRST_LINE, -1), -100);
        assertEquals(new Indents(18, 0, 0), m.indents(), "the first line stops at the margin");
    }

    @Test
    void firstLineAndRightMarkers() {
        RulerModel m = model(36, 36, 0);
        m.drag(new Hit(Handle.FIRST_LINE, -1), 0);
        assertEquals(new Indents(36, 0, 0), m.indents());
        m.drag(new Hit(Handle.RIGHT, -1), WIDTH - 50);
        assertEquals(50f, m.indents().right());
        m.drag(new Hit(Handle.RIGHT, -1), 10);
        assertEquals(WIDTH - 36 - RulerModel.MIN_TEXT, m.indents().right(), "keeps room for text");
        m.drag(new Hit(Handle.RIGHT, -1), WIDTH + 40);
        assertEquals(0f, m.indents().right());
    }

    @Test
    void tabsAreAddedMovedAndRemoved() {
        RulerModel m = model(0, 0, 0, new TabStop(200, TabStop.ALIGN_DECIMAL, TabStop.LEAD_DOTS));
        int added = m.addTab(100, TabStop.ALIGN_RIGHT);
        assertEquals(1, added);
        m.drag(new Hit(Handle.TAB, 0), 300);
        TabStop moved = m.tabs().get(0);
        assertEquals(300f, moved.getPosition());
        assertEquals(TabStop.ALIGN_DECIMAL, moved.getAlignment());
        assertEquals(TabStop.LEAD_DOTS, moved.getLeader());
        assertEquals(List.of(100f, 300f), m.sortedTabs().stream().map(TabStop::getPosition).toList());
        m.drag(new Hit(Handle.TAB, 1), WIDTH + 100);
        assertEquals(WIDTH, m.tabs().get(1).getPosition());
        m.removeTab(0);
        assertEquals(1, m.tabs().size());
    }

    @Test
    void snapsToTheUnit() {
        assertEquals(14.17f, RulerModel.snap(15, 7.0866), 0.01f);
        assertEquals(15f, RulerModel.snap(15, 0));
    }
}
