package dsc.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnitsAndRawTest {

    @Test
    void convertsAcrossUnits() {
        assertEquals(0.0084, new Quantity(8.4, Units.MG).to(Units.G).value(), 1e-12);
        assertEquals(1000.0, new Quantity(1.0, Units.G).to(Units.MG).value(), 1e-12);
        assertEquals(283.15, new Quantity(10.0, Units.C).to(Units.K).value(), 1e-9);
        assertEquals(1.0, new Quantity(1000.0, Units.MW).to(Units.W).value(), 1e-12);
        assertEquals(5000.0, new Quantity(5.0, Units.J).to(Units.MJ).value(), 1e-9);
    }

    @Test
    void rejectsCrossDimensionConversion() {
        assertThrows(IllegalArgumentException.class, () -> new Quantity(1, Units.MG).to(Units.C));
    }

    @Test
    void unconvertibleValueStaysInPlaceWithDiagnostic() {
        RawValue raw = new RawValue("n/a", Units.MW);
        var parsed = raw.parse();
        assertFalse(parsed.ok());
        assertNotNull(parsed.diagnostic());
        assertEquals("n/a", raw.raw(), "original token is retained verbatim");
    }
}
