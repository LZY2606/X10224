package dsc.model;

import java.util.OptionalDouble;

/**
 * A physical value carries its raw textual representation, an explicit unit and the
 * coordinate reference it is expressed against. Normalisation happens only in the
 * derivation layer; values that cannot be converted stay at the source with a diagnostic.
 */
public final class Physical {
    public final String raw;
    public final String unit;
    public final String reference;

    public Physical(String raw, String unit, String reference) {
        this.raw = raw;
        this.unit = unit;
        this.reference = reference;
    }

    public static String fmt(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e9) return Long.toString((long) v);
        return String.format(java.util.Locale.ROOT, "%.6g", v);
    }

    /** Canonical working units: K, kg, K/s, W. Empty when conversion is impossible. */
    public OptionalDouble toCanonical() {
        if (raw == null || raw.isBlank()) return OptionalDouble.empty();
        try {
            double v = Double.parseDouble(raw.trim());
            String u = unit == null ? "" : unit.trim();
            return switch (u) {
                case "K" -> OptionalDouble.of(v);
                case "degC", "C" -> OptionalDouble.of(v + 273.15);
                case "mg" -> OptionalDouble.of(v * 1e-6);
                case "g" -> OptionalDouble.of(v * 1e-3);
                case "kg" -> OptionalDouble.of(v);
                case "K/min", "degC/min", "C/min" -> OptionalDouble.of(v / 60.0);
                case "mW" -> OptionalDouble.of(v * 1e-3);
                case "W" -> OptionalDouble.of(v);
                case "mW/g" -> OptionalDouble.of(v * 1e-3);
                case "W/g" -> OptionalDouble.of(v);
                case "J/g" -> OptionalDouble.of(v * 1e3);
                case "J/kg" -> OptionalDouble.of(v);
                default -> OptionalDouble.empty();
            };
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    /** Value in a requested display unit, retaining the offset conversions we support. */
    public OptionalDouble toUnit(String target) {
        if (target == null) return OptionalDouble.empty();
        OptionalDouble canon = toCanonical();
        if (canon.isEmpty()) return OptionalDouble.empty();
        double c = canon.getAsDouble();
        return switch (target) {
            case "K" -> OptionalDouble.of(c);
            case "degC" -> OptionalDouble.of(c - 273.15);
            case "kg" -> OptionalDouble.of(c);
            case "g" -> OptionalDouble.of(c * 1e3);
            case "mg" -> OptionalDouble.of(c * 1e6);
            case "K/s" -> OptionalDouble.of(c);
            case "K/min" -> OptionalDouble.of(c * 60);
            case "W" -> OptionalDouble.of(c);
            case "mW" -> OptionalDouble.of(c * 1000);
            case "W/g" -> OptionalDouble.of(c);
            case "mW/g" -> OptionalDouble.of(c * 1000);
            case "J/kg" -> OptionalDouble.of(c);
            case "J/g" -> OptionalDouble.of(c / 1e3);
            default -> OptionalDouble.empty();
        };
    }
}
