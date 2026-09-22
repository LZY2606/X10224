package dsc.domain;

import java.util.Map;

/**
 * Measurement-unit support with a strict rule: callers keep the original
 * representation; this layer only adds a normalized value plus a diagnostic
 * when conversion is impossible. Unconvertible values never disappear.
 */
public final class Units {
    private Units() {
    }

    public record Conversion(double value, String canonicalUnit, String diagnostic) {
        public boolean ok() {
            return diagnostic == null;
        }
    }

    // canonical bases: temperature -> degC, heat flow -> mW, scan rate -> K/min,
    // mass -> mg, energy -> J, specific enthalpy -> J/g
    public static Conversion convert(String rawValue, String rawUnit, String physicalKind) {
        if (rawValue == null || rawValue.isBlank()) {
            return new Conversion(Double.NaN, canonical(physicalKind),
                    "EMPTY_VALUE: raw=" + rawValue + " unit=" + rawUnit);
        }
        String text = rawValue.trim();
        double v;
        try {
            v = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return new Conversion(Double.NaN, canonical(physicalKind),
                    "UNCONVERTIBLE_VALUE: raw=" + rawValue + " unit=" + rawUnit);
        }
        if (rawUnit == null) {
            return new Conversion(v, canonical(physicalKind),
                    "MISSING_UNIT: kept as canonical base, raw=" + rawValue);
        }
        String u = rawUnit.trim().replace(" ", "");
        Double out = switch (physicalKind) {
            case "temperature" -> toCelsius(v, u);
            case "heatflow" -> toMilliWatt(v, u);
            case "rate" -> toKPerMin(v, u);
            case "mass" -> toMilliGram(v, u);
            case "energy" -> toJoule(v, u);
            case "enthalpy" -> toJoulePerGram(v, u);
            default -> null;
        };
        if (out == null) {
            return new Conversion(Double.NaN, canonical(physicalKind),
                    "UNKNOWN_UNIT: raw=" + rawValue + " unit=" + rawUnit);
        }
        return new Conversion(out, canonical(physicalKind), null);
    }

    private static String canonical(String physicalKind) {
        return switch (physicalKind) {
            case "temperature" -> "degC";
            case "heatflow" -> "mW";
            case "rate" -> "K/min";
            case "mass" -> "mg";
            case "energy" -> "J";
            case "enthalpy" -> "J/g";
            default -> "dimensionless";
        };
    }

    private static Double toCelsius(double v, String u) {
        return switch (u) {
            case "C", "°C", "degC" -> v;
            case "K" -> v - 273.15;
            case "F", "°F" -> (v - 32.0) * 5.0 / 9.0;
            default -> null;
        };
    }

    private static Double toMilliWatt(double v, String u) {
        return switch (u) {
            case "mW" -> v;
            case "W" -> v * 1000.0;
            case "uW", "µW" -> v / 1000.0;
            case "J/s" -> v * 1000.0;
            default -> null;
        };
    }

    private static Double toKPerMin(double v, String u) {
        return switch (u) {
            case "K/min", "C/min", "°C/min", "degC/min" -> v;
            case "K/s", "C/s", "°C/s" -> v * 60.0;
            default -> null;
        };
    }

    private static Double toMilliGram(double v, String u) {
        return switch (u) {
            case "mg" -> v;
            case "g" -> v * 1000.0;
            case "kg" -> v * 1_000_000.0;
            case "ug", "µg" -> v / 1000.0;
            default -> null;
        };
    }

    private static Double toJoule(double v, String u) {
        return switch (u) {
            case "J" -> v;
            case "mJ" -> v / 1000.0;
            case "kJ" -> v * 1000.0;
            default -> null;
        };
    }

    private static Double toJoulePerGram(double v, String u) {
        return switch (u) {
            case "J/g" -> v;
            case "J/mg" -> v * 1000.0;
            case "mJ/mg" -> v;
            default -> null;
        };
    }

    /**
     * Exothermic direction is declared metadata, never inferred from file
     * names. "+" means an exotherm points to positive heat flow; "-" the
     * reverse. "unknown" forces an explicit correction event.
     */
    public static int exoSign(Map<String, Object> instrumentMeta) {
        Object o = instrumentMeta.get("exo_sign");
        if (o == null) {
            return 0;
        }
        return switch (o.toString().trim()) {
            case "+", "1", "exo_up", "exo_positive" -> 1;
            case "-", "-1", "exo_down", "exo_negative" -> -1;
            default -> 0;
        };
    }
}
