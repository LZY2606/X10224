package dsc;

import java.util.Locale;

/** Unit normalization. Raw strings are always preserved by callers; this layer
 *  only converts. Returns null when conversion is impossible. */
public final class Units {
    private Units() {}

    public static Double temperatureToC(String raw, String unit) {
        Double v = parse(raw);
        if (v == null || unit == null) return null;
        return switch (unit.trim()) {
            case "C", "degC", "°C" -> v;
            case "K" -> v - 273.15;
            case "F" -> (v - 32.0) * 5.0 / 9.0;
            default -> null;
        };
    }

    public static Double timeToS(String raw, String unit) {
        Double v = parse(raw);
        if (v == null || unit == null) return null;
        return switch (unit.trim()) {
            case "s" -> v;
            case "min" -> v * 60.0;
            default -> null;
        };
    }

    public static Double heatflowToMw(String raw, String unit) {
        Double v = parse(raw);
        if (v == null || unit == null) return null;
        return switch (unit.trim()) {
            case "mW" -> v;
            case "W" -> v * 1000.0;
            default -> null;
        };
    }

    public static Double massToMg(String raw, String unit) {
        Double v = parse(raw);
        if (v == null || unit == null) return null;
        return switch (unit.trim().toLowerCase(Locale.ROOT)) {
            case "mg" -> v;
            case "g" -> v * 1000.0;
            case "ug", "µg" -> v / 1000.0;
            default -> null;
        };
    }

    public static Double rateToKPerMin(String raw, String unit) {
        Double v = parse(raw);
        if (v == null || unit == null) return null;
        return switch (unit.trim()) {
            case "K/min", "C/min", "°C/min" -> v;
            case "K/s", "C/s" -> v * 60.0;
            default -> null;
        };
    }

    public static Double parse(String raw) {
        if (raw == null) return null;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
