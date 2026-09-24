package dsc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A measured value that carries its physical unit / coordinate basis.
 * Raw representations are retained on import; the derived layer converts
 * to canonical SI-aligned units and records both the original and the basis.
 */
public final class Units {

    public static final String KELVIN = "K";
    public static final String CELSIUS = "degC";
    public static final String MW = "mW";
    public static final String W = "W";
    public static final String MG = "mg";
    public static final String G = "g";
    public static final String K_PER_MIN = "K/min";
    public static final String J_PER_G = "J/g";

    private Units() {}

    public record Q(double value, String unit) {
        public Q {
            Objects.requireNonNull(unit, "unit");
        }

        public static Q of(double value, String unit) {
            return new Q(value, unit);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", value);
            m.put("unit", unit);
            return m;
        }

        public static Q fromMap(Map<?, ?> m) {
            return new Q(((Number) m.get("value")).doubleValue(), (String) m.get("unit"));
        }

        @Override
        public String toString() {
            return value + " " + unit;
        }
    }

    /** Temperature conversions: K = degC + 273.15. */
    public static double toKelvin(double value, String unit) {
        return switch (unit) {
            case KELVIN -> value;
            case CELSIUS -> value + 273.15;
            default -> throw new IllegalArgumentException("unsupported temperature unit: " + unit);
        };
    }

    public static double fromKelvin(double kelvin, String targetUnit) {
        return switch (targetUnit) {
            case KELVIN -> kelvin;
            case CELSIUS -> kelvin - 273.15;
            default -> throw new IllegalArgumentException("unsupported temperature unit: " + targetUnit);
        };
    }

    /** Mass to milligrams. */
    public static double toMilligrams(double value, String unit) {
        return switch (unit) {
            case MG -> value;
            case G -> value * 1000.0;
            default -> throw new IllegalArgumentException("unsupported mass unit: " + unit);
        };
    }

    /** Heat flow to watts. */
    public static double toWatts(double value, String unit) {
        return switch (unit) {
            case W -> value;
            case MW -> value / 1000.0;
            default -> throw new IllegalArgumentException("unsupported heat-flow unit: " + unit);
        };
    }

    /** Scan rate K/min (degC/min == K/min for a rate). */
    public static double toKPerMin(double value, String unit) {
        if (!K_PER_MIN.equals(unit) && !"degC/min".equals(unit)) {
            throw new IllegalArgumentException("unsupported scan-rate unit: " + unit);
        }
        return value;
    }

    /**
     * Convert an enthalpy density expressed as mJ/mg (the natural output of
     * mW integrated against K at K/min, divided by mg) into J/g.
     * 1 mW = 1 mJ/s; integral mW*dT / (K/min) => mJ; mJ/mg == J/g.
     */
    public static double mjPerMgToJPerG(double mjPerMg) {
        return mjPerMg;
    }

    public static boolean isTemperatureUnit(String unit) {
        return KELVIN.equals(unit) || CELSIUS.equals(unit);
    }
}
