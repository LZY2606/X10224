package org.materials.dsc.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unit handling.  All domain quantities keep their raw textual representation;
 * conversion to the internal normalized system is a separate, auditable step
 * that never discards the original.  Unconvertible values stay in place and
 * produce a {@link ConversionDiagnostic} instead of being dropped.
 */
public final class Units {

    private Units() {
    }

    public enum Kind { TEMPERATURE, MASS, RATE, HEAT_FLOW, ENERGY, SPECIFIC_ENTHALPY, TIME, DIMENSIONLESS }

    public static final class ConversionDiagnostic {
        public final String code;
        public final String message;

        public ConversionDiagnostic(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("message", message);
            return m;
        }
    }

    public static final class Scalar {
        public final double value;
        public final String unit;
        public final Kind kind;

        public Scalar(double value, String unit, Kind kind) {
            this.value = value;
            this.unit = unit;
            this.kind = kind;
        }
    }

    /** Convert a raw value+unit to the internal SI-ish base unit used by derivations. */
    public static double toBase(double value, String unit, Kind kind) {
        String u = unit == null ? "" : unit.trim().toLowerCase();
        switch (kind) {
            case TEMPERATURE:
                switch (u) {
                    case "c": case "°c": case "degc": case "deg c": case "celsius":
                        return value; // internal temperature = degrees Celsius
                    case "k": case "kelvin":
                        return value - 273.15;
                    case "f": case "°f": case "fahrenheit":
                        return (value - 32.0) * 5.0 / 9.0;
                    default:
                        throw new IllegalArgumentException("unknown temperature unit: " + unit);
                }
            case MASS:
                switch (u) {
                    case "mg": return value * 1e-6;
                    case "g": return value * 1e-3;
                    case "kg": return value;
                    default:
                        throw new IllegalArgumentException("unknown mass unit: " + unit);
                }
            case RATE:
                switch (u) {
                    case "k/min": case "c/min": case "°c/min": case "degc/min": return value;
                    case "k/s": case "c/s": return value * 60.0;
                    default:
                        throw new IllegalArgumentException("unknown rate unit: " + unit);
                }
            case HEAT_FLOW:
                // internal: W, endotherm-up positive
                switch (u) {
                    case "w": return value;
                    case "mw": return value * 1e-3;
                    case "uw": case "µw": return value * 1e-6;
                    default:
                        throw new IllegalArgumentException("unknown heat-flow unit: " + unit);
                }
            case ENERGY:
                switch (u) {
                    case "j": return value;
                    case "mj": return value * 1e-3;
                    default:
                        throw new IllegalArgumentException("unknown energy unit: " + unit);
                }
            case SPECIFIC_ENTHALPY:
                // J/g
                switch (u) {
                    case "j/g": return value;
                    case "j/kg": return value * 1e-3;
                    case "mj/mg": return value;
                    default:
                        throw new IllegalArgumentException("unknown specific enthalpy unit: " + unit);
                }
            case TIME:
                switch (u) {
                    case "s": return value;
                    case "min": return value * 60.0;
                    default:
                        throw new IllegalArgumentException("unknown time unit: " + unit);
                }
            case DIMENSIONLESS:
                return value;
            default:
                throw new IllegalArgumentException("unknown kind: " + kind);
        }
    }

    public static String baseUnit(Kind kind) {
        switch (kind) {
            case TEMPERATURE: return "°C";
            case MASS: return "kg";
            case RATE: return "°C/min";
            case HEAT_FLOW: return "W";
            case ENERGY: return "J";
            case SPECIFIC_ENTHALPY: return "J/g";
            case TIME: return "s";
            default: return "";
        }
    }

    /**
     * Attempt conversion; on failure the raw value remains the source of truth
     * and a diagnostic (never an exception) explains why.
     */
    public static Object[] tryToBase(String rawValue, String rawUnit, Kind kind) {
        try {
            double v = Double.parseDouble(rawValue == null ? "" : rawValue.trim());
            return new Object[] { toBase(v, rawUnit, kind), null };
        } catch (RuntimeException ex) {
            return new Object[] { null, new ConversionDiagnostic("UNCONVERTIBLE_VALUE",
                    "value='" + rawValue + "' unit='" + rawUnit + "' (" + kind + "): " + ex.getMessage()) };
        }
    }
}
