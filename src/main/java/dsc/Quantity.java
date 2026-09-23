package dsc;

import java.util.List;

/**
 * A domain quantity always carries a unit and a coordinate basis.
 * Raw imports keep their original textual representation; only the derived
 * layer normalizes. Values that cannot be converted stay where they are and
 * collect a diagnostic instead of being dropped.
 */
public record Quantity(double value, String unit, String basis) {

    /** Convert to the target unit. Returns null and appends a diagnostic when impossible. */
    public static Quantity convert(Quantity q, String targetUnit, List<String> diagnostics, String context) {
        if (q.unit().equals(targetUnit)) {
            return q;
        }
        Double factor = factor(q.unit(), targetUnit);
        if (factor == null) {
            diagnostics.add("unconvertible:" + context + ":" + q.value() + " " + q.unit()
                    + " -> " + targetUnit + " (kept in original position)");
            return null;
        }
        return new Quantity(q.value() * factor, targetUnit, q.basis());
    }

    private static Double factor(String from, String to) {
        return switch (from + ">" + to) {
            case "mW>W" -> 1e-3;
            case "W>mW" -> 1e3;
            case "mg>g" -> 1e-3;
            case "g>mg" -> 1e3;
            case "K/min>K/s", "C/min>C/s" -> 1.0 / 60.0;
            case "K/s>K/min", "C/s>C/min" -> 60.0;
            case "mJ>J" -> 1e-3;
            case "J>mJ" -> 1e3;
            default -> null;
        };
    }

    public static Quantity parse(String raw, String defaultBasis, List<String> diagnostics, String context) {
        String trimmed = raw.trim();
        String[] parts = trimmed.split("\\s+", 2);
        try {
            double v = Double.parseDouble(parts[0]);
            String unit = parts.length > 1 ? parts[1].trim() : "";
            if (unit.isEmpty()) {
                diagnostics.add("unitless:" + context + ":" + trimmed + " (kept raw, basis=" + defaultBasis + ")");
            }
            return new Quantity(v, unit, defaultBasis);
        } catch (NumberFormatException e) {
            diagnostics.add("unparseable:" + context + ":" + trimmed + " (kept raw)");
            return null;
        }
    }

    @Override
    public String toString() {
        return value + " " + unit + (basis == null || basis.isEmpty() ? "" : " [" + basis + "]");
    }
}
