package dsc;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A domain quantity. The raw representation is always preserved; conversion
 * happens only in the derived layer. Values that cannot be converted stay in
 * place and carry a diagnostic instead of being dropped.
 */
public final class Q {
    public final String raw;
    public final Double value;
    public final String unit;
    public final String basis;
    public final String diagnostic;

    private Q(String raw, Double value, String unit, String basis, String diagnostic) {
        this.raw = raw;
        this.value = value;
        this.unit = unit;
        this.basis = basis;
        this.diagnostic = diagnostic;
    }

    public static Q of(double value, String unit) {
        return new Q(null, value, unit, null, null);
    }

    public static Q of(double value, String unit, String basis) {
        return new Q(null, value, unit, basis, null);
    }

    public static Q invalid(String raw, String diagnostic) {
        return new Q(raw, null, null, null, diagnostic);
    }

    /** Parse "12.46 mg" style raw text into a quantity. */
    public static Q parse(String raw) {
        if (raw == null) return Q.invalid(null, "missing value");
        String trimmed = raw.trim();
        String[] parts = trimmed.split("\\s+", 2);
        try {
            double v = Double.parseDouble(parts[0]);
            String unit = parts.length > 1 ? parts[1].trim() : "";
            return new Q(trimmed, v, unit, null, null);
        } catch (NumberFormatException e) {
            return Q.invalid(trimmed, "unparseable numeric value");
        }
    }

    /** Convert to target unit; returns a quantity with diagnostic when impossible. */
    public Q as(String targetUnit) {
        if (value == null) return this;
        if (unit == null || unit.isEmpty()) {
            return new Q(raw, null, targetUnit, basis, "no unit declared; cannot convert to " + targetUnit);
        }
        if (unit.equals(targetUnit)) return new Q(raw, value, targetUnit, basis, null);
        Double converted = convert(value, unit, targetUnit);
        if (converted == null) {
            return new Q(raw, null, targetUnit, basis,
                    "unsupported conversion " + unit + " -> " + targetUnit + "; raw value kept in place");
        }
        return new Q(raw, converted, targetUnit, basis, null);
    }

    private static Double convert(double v, String from, String to) {
        // temperature
        if (from.equals("°C") && to.equals("K")) return v + 273.15;
        if (from.equals("K") && to.equals("°C")) return v - 273.15;
        if (from.equals("°C") && to.equals("°C")) return v;
        // heat flow
        if (from.equals("mW") && to.equals("W")) return v / 1000.0;
        if (from.equals("W") && to.equals("mW")) return v * 1000.0;
        // mass
        if (from.equals("mg") && to.equals("g")) return v / 1000.0;
        if (from.equals("g") && to.equals("mg")) return v * 1000.0;
        // rate
        if (from.equals("K/min") && to.equals("K/s")) return v / 60.0;
        if (from.equals("K/s") && to.equals("K/min")) return v * 60.0;
        if (from.equals("°C/min") && to.equals("K/min")) return v;
        // energy
        if (from.equals("mJ") && to.equals("J")) return v / 1000.0;
        if (from.equals("J") && to.equals("mJ")) return v * 1000.0;
        return null;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (raw != null) m.put("raw", raw);
        if (value != null) m.put("value", value);
        if (unit != null) m.put("unit", unit);
        if (basis != null) m.put("basis", basis);
        if (diagnostic != null) m.put("diagnostic", diagnostic);
        return m;
    }

    @Override
    public String toString() {
        if (value == null) return (raw == null ? "?" : raw) + " [" + diagnostic + "]";
        return value + " " + unit + (basis != null ? " (" + basis + ")" : "");
    }
}
