package dsc.core;

import java.util.Map;

/**
 * Unit / coordinate-frame conversion used by the derivation layer only.
 *
 * Canonical frames:
 *   temperature        K
 *   time               s
 *   heating rate       K/s
 *   heat flow          W (absolute) and W/g (mass normalized)
 *   mass               g
 *   specific enthalpy  J/g
 *   enthalpy           J
 */
public final class Units {
    private Units() {}

    private static final Map<String, Double> MASS = Map.of(
            "g", 1.0, "mg", 0.001, "kg", 1000.0);
    private static final Map<String, Double> TIME = Map.of(
            "s", 1.0, "sec", 1.0, "min", 60.0, "ms", 0.001, "h", 3600.0);
    private static final Map<String, Double> ENERGY = Map.of(
            "J", 1.0, "mJ", 0.001, "kJ", 1000.0);
    private static final Map<String, Double> POWER = Map.of(
            "W", 1.0, "mW", 0.001);

    /** Returns null when the value cannot be converted to the target frame. */
    public static Double convert(double value, String from, String to) {
        if (from == null || to == null) return null;
        if (from.equals(to)) return value;
        Double t = temp(value, from, to);
        if (t != null) return t;
        Double linear = linear(value, from, to);
        return linear;
    }

    public static boolean isTemperature(String unit) {
        return unit != null && switch (unit) {
            case "K", "C", "°C", "degC", "F", "°F", "degF" -> true;
            default -> false;
        };
    }

    private static Double temp(double value, String from, String to) {
        Double c = toCelsius(value, from);
        if (c == null) return null;
        return switch (to) {
            case "C", "°C", "degC" -> c;
            case "K" -> c + 273.15;
            case "F", "°F", "degF" -> c * 9.0 / 5.0 + 32.0;
            default -> null;
        };
    }

    private static Double toCelsius(double v, String u) {
        return switch (u) {
            case "C", "°C", "degC" -> v;
            case "K" -> v - 273.15;
            case "F", "°F", "degF" -> (v - 32.0) * 5.0 / 9.0;
            default -> null;
        };
    }

    private static Double linear(double value, String from, String to) {
        Double f = factor(from);
        Double g = factor(to);
        if (f == null || g == null) return null;
        String fk = kind(from);
        String gk = kind(to);
        if (!fk.equals(gk)) return null;
        double canonical = value * f;
        return canonical / g;
    }

    private static String kind(String u) {
        if (MASS.containsKey(u)) return "mass";
        if (TIME.containsKey(u)) return "time";
        if (ENERGY.containsKey(u)) return "energy";
        if (POWER.containsKey(u)) return "power";
        if (u.equals("K/s") || u.equals("K/min") || u.equals("C/min")
                || u.equals("°C/min") || u.equals("C/s") || u.equals("°C/s")) {
            return "rate";
        }
        return "?";
    }

    private static Double factor(String u) {
        if (MASS.containsKey(u)) return MASS.get(u);
        if (TIME.containsKey(u)) return TIME.get(u);
        if (ENERGY.containsKey(u)) return ENERGY.get(u);
        if (POWER.containsKey(u)) return POWER.get(u);
        return switch (u) {
            case "K/s", "C/s", "°C/s" -> 1.0;
            case "K/min", "C/min", "°C/min" -> 1.0 / 60.0;
            default -> null;
        };
    }
}
