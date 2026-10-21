package eu.amadeos.apps.writepad.service.layout;

import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.domain.Orientation;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageLayoutTest {
    private final PageLayout layout = new PageLayout(DocumentSettings.DEFAULT, 1);

    @Test
    void pagesAreStackedWithGaps() {
        int height = layout.pageHeight();
        assertEquals(842, height);
        assertEquals(PageLayout.GAP, layout.pageTop(0));
        assertEquals(2 * PageLayout.GAP + height, layout.pageTop(1));
        assertEquals(layout.pageTop(1) + 71, layout.contentTop(1)); // 25 mm
        assertEquals(PageLayout.GAP + 3 * (height + PageLayout.GAP), layout.totalHeight(3));
    }

    @Test
    void lineThatFitsStays() {
        int y = layout.contentTop(0) + 100;
        assertEquals(y, layout.place(y, 14));
    }

    // --8<-- [start:crossing]
    @Test
    void lineCrossingTheBottomMarginMovesToNextPage() {
        int y = layout.contentBottom(0) - 10;
        assertEquals(layout.contentTop(1), layout.place(y, 14));
        assertEquals(y, layout.place(y, 10));
    }
    // --8<-- [end:crossing]

    @Test
    void lineStartingInMarginOrGapMovesToText() {
        assertEquals(layout.contentTop(0), layout.place(0, 14));
        assertEquals(layout.contentTop(1), layout.place(layout.contentBottom(0) + 5, 14));
        assertEquals(layout.contentTop(1), layout.place(layout.pageTop(1) + 2, 14));
    }

    @Test
    void lineTallerThanAPageStaysToEndLayout() {
        int top = layout.contentTop(0);
        assertEquals(top, layout.place(top, 5000));
    }

    @Test
    void pageCountFollowsTheEndOfTheText() {
        assertEquals(1, layout.pagesUpTo(0));
        assertEquals(1, layout.pagesUpTo(layout.contentBottom(0)));
        assertEquals(2, layout.pagesUpTo(layout.contentTop(1) + 1));
    }

    @Test
    void changesNotifyListeners() {
        AtomicInteger calls = new AtomicInteger();
        layout.addChangeListener(calls::incrementAndGet);
        layout.setZoom(2);
        layout.setZoom(2);
        layout.setSettings(DocumentSettings.DEFAULT.withOrientation(Orientation.LANDSCAPE));
        assertEquals(2, calls.get());
        assertEquals(842, layout.pageWidth());
        assertThrows(IllegalArgumentException.class, () -> layout.setZoom(10));
    }
}
