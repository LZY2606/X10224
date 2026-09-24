package dsc;

import dsc.domain.Units;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnitsTest {
    @Test
    void keepsUnconvertibleValuesInPlaceWithDiagnostic() {
        Units.Parse p = Units.powerW("OVERLOAD", "mW");
        assertNull(p.value);
        assertNotNull(p.diagnostic);
        assertTrue(p.diagnostic.contains("UNCONVERTIBLE_VALUE"));
    }

    @Test
    void convertsKnownUnitsToCanonical() {
        assertEquals(1.0, Units.temperatureK("0", "°C").value, 1e-9);
        assertEquals(373.15, Units.temperatureK("100", "C").value, 1e-9);
        assertEquals(0.001, Units.powerW("1", "mW").value, 1e-12);
        assertEquals(0.00842, Units.massG("8.42", "mg").value, 1e-12);
        assertEquals(60.0, Units.rateKPerMin("1", "K/s").value, 1e-9);
    }

    @Test
    void unknownUnitIsDiagnosticNotThrown() {
        assertNull(Units.massG("1", "grains").value);
        assertNotNull(Units.massG("1", "grains").diagnostic);
    }
}
