package dsc;

import java.util.Locale;

/** Unit parsing with normalization. Unconvertible input returns null; callers retain the raw token + a diagnostic. */
public final class Units {
    private Units() {}

    public static Double tempK(String raw, String unit) {
        Double v = num(raw);
        if (v == null || unit == null) return null;
        switch (unit.trim().toLowerCase(Locale.ROOT)) {
            case "k": return v;
            case "degc", "c", "°c": return v + 273.15;
            case "degf", "f": return (v - 32.0) * 5.0 / 9.0 + 273.15;
            default: return null;
        }
    }

    public static Double massG(String raw, String unit) {
        Double v = num(raw);
        if (v == null || unit == null) return null;
        switch (unit.trim().toLowerCase(Locale.ROOT)) {
            case "g": return v;
            case "mg": return v / 1000.0;
            case "kg": return v * 1000.0;
            default: return null;
        }
    }

    public static Double flowW(String raw, String unit) {
        Double v = num(raw);
        if (v == null || unit == null) return null;
        switch (unit.trim().toLowerCase(Locale.ROOT)) {
            case "w": return v;
            case "mw": return v / 1000.0;
            case "uw", "µw": return v / 1_000_000.0;
            default: return null;
        }
    }

    public static Double timeS(String raw, String unit) {
        Double v = num(raw);
        if (v == null || unit == null) return null;
        switch (unit.trim().toLowerCase(Locale.ROOT)) {
            case "s", "sec": return v;
            case "min": return v * 60.0;
            case "ms": return v / 1000.0;
            default: return null;
        }
    }

    public static Double rateKPerMin(String raw, String unit) {
        Double v = num(raw);
        if (v == null || unit == null) return null;
        switch (unit.trim().toLowerCase(Locale.ROOT).replace(" ", "")) {
            case "k/min": return v;
            case "c/min", "degc/min": return v;
            case "k/s": return v * 60.0;
            default: return null;
        }
    }

    public static Double num(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("NA") || s.equalsIgnoreCase("N/A")) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
