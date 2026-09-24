package dsc.domain;

import java.util.Map;
import java.util.Objects;

/**
 * A measured value that keeps its original representation and declared unit.
 * Normalization happens only in the derivation layer; values that cannot be
 * converted stay in place and carry diagnostics instead of being dropped.
 */
public record Quantity(double value, String unit, String raw) {

    public Quantity {
        Objects.requireNonNull(unit, "unit");
        if (raw == null) raw = format(value, unit);
    }

    public static Quantity of(double value, String unit) {
        return new Quantity(value, unit, null);
    }

    public static Quantity raw(String raw) {
        return new Quantity(Double.NaN, "", raw);
    }

    public boolean isConvertible() {
        return unit != null && !unit.isBlank() && !Double.isNaN(value);
    }

    /** Convert to a canonical unit within the same quantity kind; null if impossible. */
    public Quantity to(String targetUnit) {
        if (!isConvertible()) return null;
        if (unit.equals(targetUnit)) return this;
        Double factor = UnitTables.factor(unit, targetUnit);
        if (factor == null) return null;
        return new Quantity(value * factor, targetUnit, raw);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("value", Double.isNaN(value) ? null : value);
        m.put("unit", unit);
        m.put("raw", raw);
        m.put("convertible", isConvertible());
        return m;
    }

    public static Quantity fromMap(Map<String, Object> m) {
        Object v = m.get("value");
        double d = v instanceof Number n ? n.doubleValue() : Double.NaN;
        String unit = String.valueOf(m.getOrDefault("unit", ""));
        String raw = m.get("raw") == null ? null : String.valueOf(m.get("raw"));
        return new Quantity(d, unit, raw);
    }

    private static String format(double value, String unit) {
        if (Double.isNaN(value)) return "";
        return ((double) Math.round(value * 1e6d) / 1e6d) + (unit.isEmpty() ? "" : " " + unit);
    }
}
