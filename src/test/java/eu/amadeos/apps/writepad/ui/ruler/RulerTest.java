package eu.amadeos.apps.writepad.ui.ruler;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.apps.writepad.OnEdt;
import eu.amadeos.apps.writepad.bootstrap.WritepadModule;
import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.domain.MeasurementUnit;
import eu.amadeos.apps.writepad.service.layout.PageLayout;
import eu.amadeos.apps.writepad.ui.editor.PageEditor;
import eu.amadeos.apps.writepad.ui.editor.TextFormatting.Indents;
import org.junit.jupiter.api.Test;

import javax.swing.text.TabStop;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mouse work on a real ruler over a real editor, at 150 % zoom. */
class RulerTest {
    private static final double ZOOM = 1.5;
    private final List<Object> changes = new ArrayList<>();
    private PageEditor editor;
    private Ruler ruler;

    private void withRuler(Runnable test) throws Exception {
        OnEdt.run(() -> {
            editor = new PageEditor(new PageLayout(DocumentSettings.DEFAULT, ZOOM));
            editor.setSize(editor.getPreferredSize());
            Messages msg = Messages.load(WritepadModule.class, WritepadModule.MESSAGES, Locale.ENGLISH);
            ruler = new Ruler(editor, MeasurementUnit.CENTIMETRE, msg);
            ruler.setSize(ruler.getPreferredSize());
            ruler.setListener(new Ruler.Listener() {
                @Override
                public void indentsChanged(Indents indents) {
                    changes.add(indents);
                }

                @Override
                public void tabsChanged(List<TabStop> tabs) {
                    changes.add(tabs);
                }
            });
            test.run();
        });
    }

    private int x(float points) {
        return (int) Math.round(ruler.screenX(points));
    }

    private void mouse(int id, int x, int y, int modifiers) {
        ruler.dispatchEvent(new MouseEvent(ruler, id, 0, modifiers | InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false,
                MouseEvent.BUTTON1));
    }

    private void drag(int fromX, int fromY, int toX, int toY) {
        mouse(MouseEvent.MOUSE_PRESSED, fromX, fromY, 0);
        mouse(MouseEvent.MOUSE_DRAGGED, (fromX + toX) / 2, (fromY + toY) / 2, 0);
        mouse(MouseEvent.MOUSE_DRAGGED, toX, toY, 0);
        mouse(MouseEvent.MOUSE_RELEASED, toX, toY, 0);
    }

    @Test
    void positionsFollowTheEditorsZoom() throws Exception {
        withRuler(() -> {
            assertEquals(editor.textColumnX(), ruler.screenX(0), 0.001);
            assertEquals(editor.textColumnX() + 100 * ZOOM, ruler.screenX(100), 0.001);
            assertEquals(100f, ruler.points(x(100)), 0.5f);
        });
    }

    @Test
    void draggingTheLeftMarkerSnapsToQuarterCentimetres() throws Exception {
        withRuler(() -> {
            ruler.show(new Indents(0, 0, 0), List.of());
            drag(x(0), 16, x(30), 16);
            Indents changed = (Indents) changes.get(0);
            assertEquals(MeasurementUnit.CENTIMETRE.snap() * 4, changed.left(), 0.01, "1 cm");
            assertEquals(0f, changed.firstLine());
            assertEquals(changed, ruler.model().indents());
        });
    }

    @Test
    void altDragsWithoutSnapping() throws Exception {
        withRuler(() -> {
            ruler.show(new Indents(0, 0, 0), List.of());
            mouse(MouseEvent.MOUSE_PRESSED, x(0), 3, 0);
            mouse(MouseEvent.MOUSE_DRAGGED, x(30), 3, InputEvent.ALT_DOWN_MASK);
            mouse(MouseEvent.MOUSE_RELEASED, x(30), 3, InputEvent.ALT_DOWN_MASK);
            assertEquals(30f, ((Indents) changes.get(0)).firstLine(), 1f);
        });
    }

    @Test
    void clickSetsATabOfTheChosenKindAndDraggingOffRemovesIt() throws Exception {
        withRuler(() -> {
            ruler.show(new Indents(0, 0, 0), List.of());
            ruler.nextTabType();
            assertEquals(TabStop.ALIGN_CENTER, ruler.tabType());
            drag(x(85), 10, x(85), 10);
            @SuppressWarnings("unchecked")
            List<TabStop> tabs = (List<TabStop>) changes.get(0);
            assertEquals(1, tabs.size());
            assertEquals(MeasurementUnit.CENTIMETRE.snap() * 12, tabs.get(0).getPosition(), 0.01, "3 cm");
            assertEquals(TabStop.ALIGN_CENTER, tabs.get(0).getAlignment());

            ruler.show(new Indents(0, 0, 0), tabs);
            drag(x(tabs.get(0).getPosition()), 15, x(tabs.get(0).getPosition()), 80);
            assertEquals(List.of(), changes.get(1));
        });
    }

    @Test
    void clicksOutsideTheTextColumnDoNothing() throws Exception {
        withRuler(() -> {
            ruler.show(new Indents(0, 0, 0), List.of());
            drag(x(-20), 10, x(-20), 10);
            drag(x(200), 26, x(200), 26);
            assertTrue(changes.isEmpty(), changes.toString());
        });
    }

    @Test
    void selectorBoxCyclesTheTabKinds() throws Exception {
        withRuler(() -> {
            for (int expected : new int[]{TabStop.ALIGN_CENTER, TabStop.ALIGN_RIGHT, TabStop.ALIGN_DECIMAL,
                    TabStop.ALIGN_LEFT}) {
                mouse(MouseEvent.MOUSE_PRESSED, Ruler.SELECTOR.x + 4, Ruler.SELECTOR.y + 4, 0);
                mouse(MouseEvent.MOUSE_RELEASED, Ruler.SELECTOR.x + 4, Ruler.SELECTOR.y + 4, 0);
                assertEquals(expected, ruler.tabType());
            }
            assertTrue(changes.isEmpty());
        });
    }

    @Test
    void tooltipsNameTheMarkers() throws Exception {
        withRuler(() -> {
            ruler.show(new Indents(36, 18, 0), List.of(new TabStop(200, TabStop.ALIGN_RIGHT, TabStop.LEAD_NONE)));
            assertEquals("First line indent", tip(x(18), 3));
            assertEquals("Hanging indent", tip(x(36), 15));
            assertEquals("Left indent", tip(x(36), 21));
            assertEquals("Right tab", tip(x(200), 15));
        });
    }

    private String tip(int x, int y) {
        return ruler.getToolTipText(new MouseEvent(ruler, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
    }
}
