package dsc.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * A physical quantity carrying its original textual representation and unit.
 * Import keeps the raw text; normalization happens only in the derivation layer.
 */
public final class Quantity {
    private static final java.util.Map<String, Double> FACTORS = java.util.Map.ofEntries(
            java.util.Map.entry("K", 1.0),
            java.util.Map.entry("degC", 1.0),
            java.util.Map.entry("C", 1.0),
            java.util.Map.entry("mg", 1.0),
            java.util.Map.entry("g", 1000.0),
            java.util.Map.entry("ug", 0.001),
            java.util.Map.entry("K/min", 1.0),
            java.util.Map.entry("degC/min", 1.0),
            java.util.Map.entry("W/g", 1.0),
            java.util.Map.entry("mW/mg", 1.0),
            java.util.Map.entry("mW", 0.001),
            java.util.Map.entry("J/g", 1.0),
            java.util.Map.entry("J", 1.0)
    );

    private final String raw;
    private final Double value;
    private final String unit;

    private Quantity(String raw, Double value, String unit) {
        this.raw = raw;
        this.value = value;
        this.unit = unit;
    }

    public static Quantity unparseable(String raw) {
        return new Quantity(raw, null, null);
    }

    public static Quantity of(double value, String unit) {
        return new Quantity(String.format(Locale.ROOT, "%.6g %s", value, unit), value, unit);
    }

    public static Quantity parse(String text) {
        if (text == null) {
            return new Quantity(null, null, null);
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return new Quantity(t, null, null);
        }
        int split = -1;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isLetter(c) || c == '°') { split = i; break; }
        }
        String numPart, unit;
        if (split <= 0) {
            numPart = t;
            unit = "";
        } else {
            numPart = t.substring(0, split).trim();
            unit = normalizeUnitToken(t.substring(split).trim());
        }
        try {
            double v = Double.parseDouble(numPart);
            if (!unit.isEmpty() && !FACTORS.containsKey(unit)) {
                return new Quantity(t, null, unit);
            }
            return new Quantity(t, v, unit.isEmpty() ? "" : unit);
        } catch (NumberFormatException e) {
            return new Quantity(t, null, unit);
        }
    }

    private static String normalizeUnitToken(String u) {
        return switch (u) {
            case "°C", "℃" -> "degC";
            case "°C/min", "℃/min" -> "degC/min";
            case "w/g" -> "W/g";
            default -> u;
        };
    }

    public boolean isParseable() { return value != null; }
    public String raw() { return raw; }
    public Double value() { return value; }
    public String unit() { return unit; }

    public boolean compatibleWith(Quantity other) {
        return dimension().equals(other.dimension());
    }

    public String dimension() {
        if (unit == null || unit.isEmpty()) return "unknown";
        return switch (unit) {
            case "K", "degC", "C" -> "temperature";
            case "mg", "g", "ug" -> "mass";
            case "K/min", "degC/min" -> "rate";
            case "W/g", "mW/mg", "mW" -> "heatflow";
            case "J/g" -> "specificEnergy";
            case "J" -> "energy";
            default -> "unknown";
        };
    }

    /** Value expressed in the canonical unit of the dimension (degC / mg / K/min / W/g / J/g). */
    public Double canonical() {
        if (value == null || unit == null || !FACTORS.containsKey(unit)) return null;
        return value * FACTORS.get(unit);
    }

    public static String canonicalUnit(String dimension) {
        return switch (dimension) {
            case "temperature" -> "degC";
            case "mass" -> "mg";
            case "rate" -> "K/min";
            case "heatflow" -> "W/g";
            case "specificEnergy" -> "J/g";
            case "energy" -> "J";
            default -> "";
        };
    }

    public Quantity toCanonical() {
        Double c = canonical();
        if (c == null) return this;
        return Quantity.of(c, canonicalUnit(dimension()));
    }

    @Override public String toString() { return raw; }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Quantity q)) return false;
        return Objects.equals(raw, q.raw);
    }
    @Override public int hashCode() { return Objects.hashCode(raw); }
}
