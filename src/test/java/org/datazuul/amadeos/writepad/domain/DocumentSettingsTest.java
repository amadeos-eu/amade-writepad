package org.datazuul.amadeos.writepad.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentSettingsTest {
    private static final double EPSILON = 0.01;

    @Test
    void paperSizesAreInPoints() {
        assertEquals(595.28, PaperSize.A4.width(), EPSILON);
        assertEquals(841.89, PaperSize.A4.height(), EPSILON);
        assertEquals(419.53, PaperSize.A5.width(), EPSILON);
        assertEquals(595.28, PaperSize.A5.height(), EPSILON);
        assertEquals(612, PaperSize.LETTER.width(), EPSILON);
        assertEquals(792, PaperSize.LETTER.height(), EPSILON);
    }

    @Test
    void landscapeSwapsWidthAndHeight() {
        DocumentSettings portrait = DocumentSettings.DEFAULT;
        DocumentSettings landscape = portrait.withOrientation(Orientation.LANDSCAPE);
        assertEquals(portrait.pageWidth(), landscape.pageHeight(), EPSILON);
        assertEquals(portrait.pageHeight(), landscape.pageWidth(), EPSILON);
    }

    @Test
    void contentAreaLeavesOutTheMargins() {
        DocumentSettings settings = new DocumentSettings(PaperSize.A5, Orientation.PORTRAIT,
                Margins.ofMm(20, 15, 20, 15));
        assertEquals(Units.mmToPoints(148 - 30), settings.contentWidth(), EPSILON);
        assertEquals(Units.mmToPoints(210 - 40), settings.contentHeight(), EPSILON);
    }

    @Test
    void marginsMustLeaveRoomForText() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentSettings(PaperSize.A5,
                Orientation.LANDSCAPE, Margins.ofMm(70, 10, 70, 10)));
        assertThrows(IllegalArgumentException.class, () -> Margins.ofMm(-1, 0, 0, 0));
    }

    @Test
    void unitsConvert() {
        assertEquals(72, Units.mmToPoints(25.4), EPSILON);
        assertEquals(25.4, Units.pointsToMm(72), EPSILON);
        assertEquals(11906, Units.pointsToTwips(PaperSize.A4.width()));
        assertEquals(36, Units.twipsToPoints(720), EPSILON);
    }
}
