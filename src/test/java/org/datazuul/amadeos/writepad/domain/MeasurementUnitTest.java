package org.datazuul.amadeos.writepad.domain;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeasurementUnitTest {
    @Test
    void regionDecides() {
        assertEquals(MeasurementUnit.INCH, MeasurementUnit.fromEnvironment(Map.of("LANG", "en_US.UTF-8"), Locale.GERMANY));
        assertEquals(MeasurementUnit.CENTIMETRE, MeasurementUnit.fromEnvironment(Map.of("LANG", "en_GB.UTF-8"), Locale.US));
        assertEquals(MeasurementUnit.CENTIMETRE, MeasurementUnit.fromEnvironment(
                Map.of("LANG", "en_US.UTF-8", "LC_MEASUREMENT", "de_DE.UTF-8"), Locale.US));
        assertEquals(MeasurementUnit.INCH, MeasurementUnit.fromEnvironment(Map.of("LANG", "C.UTF-8"), Locale.US));
        assertEquals(MeasurementUnit.CENTIMETRE, MeasurementUnit.fromEnvironment(Map.of(), Locale.ITALY));
    }

    @Test
    void convertsAndSnaps() {
        assertEquals(28.346, MeasurementUnit.CENTIMETRE.points(), 0.001);
        assertEquals(2.54, MeasurementUnit.CENTIMETRE.toUnit(72), 0.001);
        assertEquals(36, MeasurementUnit.INCH.toPoints(0.5), 0.001);
        assertEquals(9, MeasurementUnit.INCH.snap(), 0.001);
        assertEquals(7.087, MeasurementUnit.CENTIMETRE.snap(), 0.001);
    }
}
