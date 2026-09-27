package dsc.domain;

import java.util.Map;

/** Unit conversion for the derived (normalization) layer only. */
public final class Units {
    private Units() {}

    /** Canonical units: temperature K, heat flow mW, mass mg, rate K/min, energy J/g. */
    private static final Map<String, String> CANONICAL = Map.of(
            "degC", "K", "K", "K",
            "mW", "mW", "W", "mW",
            "mg", "mg", "g", "mg",
            "K/min", "K/min", "K/s", "K/min",
            "J/g", "J/g", "mJ/mg", "J/g");

    public static boolean convertible(String unit) {
        return CANONICAL.containsKey(unit);
    }

    /** Convert to canonical unit; returns null with diagnostic if impossible. */
    public static Quantity normalize(Quantity q) {
        if (q.value == null) {
            return Quantity.unconvertible(q.raw, q.unit, q.basis,
                    q.diagnostic != null ? q.diagnostic : "no numeric value");
        }
        if (!convertible(q.unit)) {
            return Quantity.unconvertible(q.raw, q.unit, q.basis, "unknown unit: " + q.unit);
        }
        double v = q.value;
        switch (q.unit) {
            case "degC" -> v = v + 273.15;
            case "W" -> v = v * 1000.0;
            case "g" -> v = v * 1000.0;
            case "K/s" -> v = v * 60.0;
            case "mJ/mg" -> { /* numerically equal to J/g */ }
            default -> { }
        }
        return Quantity.of(q.raw, v, CANONICAL.get(q.unit), q.basis);
    }

    /** Convert a canonical heat-flow value into the opposite coordinate basis. */
    public static double flipBasis(double value) {
        return -value;
    }
}
