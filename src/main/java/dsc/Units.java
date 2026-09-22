package dsc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Quantities carry a unit and a coordinate basis. Raw representations are kept
 * verbatim at import; normalization happens only in the derived layer. Values
 * that cannot be converted stay in place and produce a Diagnostic.
 */
public final class Units {

    /** basis: where the number lives, e.g. "as-recorded", "mass-normalized", "sample-temperature". */
    public record Quantity(double value, String unit, String basis) {
        public String canonical() { return value + " " + unit + " [" + basis + "]"; }
    }

    public record Diagnostic(String code, String message, String rawValue, String location) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("message", message);
            m.put("rawValue", rawValue);
            m.put("location", location);
            return m;
        }
    }

    /** dimension -> (unit -> factor to SI-ish base). base units: K, mW, mg, K/min, mL/min */
    private static final Map<String, Map<String, Double>> CONV = new LinkedHashMap<>();
    static {
        CONV.put("temperature", Map.of("K", 1.0, "degC", 1.0)); // offset handled separately
        CONV.put("heatflow", Map.of("mW", 1.0, "W", 1000.0, "uW", 0.001));
        CONV.put("mass", Map.of("mg", 1.0, "g", 1000.0, "ug", 0.001));
        CONV.put("rate", Map.of("K/min", 1.0, "degC/min", 1.0, "K/s", 60.0, "degC/s", 60.0));
        CONV.put("flow", Map.of("mL/min", 1.0));
        CONV.put("energy", Map.of("mJ", 1.0, "J", 1000.0));
    }

    public static String dimensionOf(String unit) {
        for (var e : CONV.entrySet()) if (e.getValue().containsKey(unit)) return e.getKey();
        return null;
    }

    /** Convert to target unit; empty when no known path (caller must keep raw + diagnostic). */
    public static Optional<Double> convert(double value, String fromUnit, String toUnit) {
        if (fromUnit.equals(toUnit)) return Optional.of(value);
        String dim = dimensionOf(fromUnit);
        if (dim == null || !CONV.get(dim).containsKey(toUnit)) return Optional.empty();
        // temperature offsets (degC <-> K) intentionally not auto-shifted: DSC axes stay in recorded basis
        if (dim.equals("temperature") && !fromUnit.equals(toUnit)) return Optional.empty();
        double base = value * CONV.get(dim).get(fromUnit);
        return Optional.of(base / CONV.get(dim).get(toUnit));
    }

    /** Parse raw strings like "12.45 mg" or "10 K/min" without losing the original text. */
    public record ParsedRaw(String raw, Double value, String unit, Diagnostic diagnostic) {}

    public static ParsedRaw parseRaw(String raw, String location) {
        String t = raw == null ? "" : raw.trim();
        var m = java.util.regex.Pattern.compile("^([-+0-9.eE]+)\\s*([a-zA-Z/%°].*)$").matcher(t);
        if (!m.matches()) {
            return new ParsedRaw(raw, null, null,
                new Diagnostic("UNPARSEABLE_QUANTITY", "cannot split value and unit", raw, location));
        }
        double v;
        try { v = Double.parseDouble(m.group(1)); }
        catch (NumberFormatException e) {
            return new ParsedRaw(raw, null, null,
                new Diagnostic("UNPARSEABLE_NUMBER", "numeric part invalid", raw, location));
        }
        String unit = m.group(2).trim().replace("°C", "degC");
        if (dimensionOf(unit) == null) {
            return new ParsedRaw(raw, v, unit,
                new Diagnostic("UNKNOWN_UNIT", "unit '" + unit + "' has no conversion path; value kept as recorded", raw, location));
        }
        return new ParsedRaw(raw, v, unit, null);
    }
}
