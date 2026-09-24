package org.materials.dsc.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnitsTest {

    @Test
    void kelvinToCelsiusOffset() {
        assertEquals(0.0, Units.KELVIN.toCanonical(273.15), 1e-9);
        assertEquals(373.15, Units.CELSIUS.toCanonical(100.0), 1e-9);
    }

    @Test
    void milliwattsAndGramsCanonical() {
        assertEquals(0.012, Units.MILLIWATT.toCanonical(12.0), 1e-12);
        assertEquals(0.00842, Units.GRAM.toCanonical(8.42e-3), 1e-15);
        assertEquals(0.005, Units.MILLIGRAM.toCanonical(5.0), 1e-15);
    }
}
