package dsc.analysis;

/** 单位换算：原始表示永远保留，无法换算时返回诊断而不是丢弃。 */
public final class Units {

    public record Parsed(Double value, String diagnostic) {
        public boolean ok() { return value != null; }
    }

    private static Double num(String raw) {
        try { return Double.parseDouble(raw.trim()); }
        catch (Exception e) { return null; }
    }

    public static Parsed massToMg(String raw, String unit) {
        Double v = num(raw);
        if (v == null) return new Parsed(null, "MASS_UNPARSEABLE:" + raw);
        return switch (norm(unit)) {
            case "mg" -> new Parsed(v, null);
            case "g" -> new Parsed(v * 1000.0, null);
            case "ug", "µg" -> new Parsed(v / 1000.0, null);
            default -> new Parsed(null, "MASS_UNIT_UNCONVERTIBLE:" + unit);
        };
    }

    public static Parsed tempToC(String raw, String unit) {
        Double v = num(raw);
        if (v == null) return new Parsed(null, "TEMP_UNPARSEABLE:" + raw);
        return switch (norm(unit)) {
            case "c", "degc", "°c" -> new Parsed(v, null);
            case "k" -> new Parsed(v - 273.15, null);
            default -> new Parsed(null, "TEMP_UNIT_UNCONVERTIBLE:" + unit);
        };
    }

    public static Parsed hfToMw(String raw, String unit) {
        Double v = num(raw);
        if (v == null) return new Parsed(null, "HF_UNPARSEABLE:" + raw);
        return switch (norm(unit)) {
            case "mw" -> new Parsed(v, null);
            case "w" -> new Parsed(v * 1000.0, null);
            case "mj/s" -> new Parsed(v, null);
            default -> new Parsed(null, "HF_UNIT_UNCONVERTIBLE:" + unit);
        };
    }

    public static Parsed rateToKPerS(String raw, String unit) {
        Double v = num(raw);
        if (v == null) return new Parsed(null, "RATE_UNPARSEABLE:" + raw);
        return switch (norm(unit)) {
            case "k/min", "c/min", "°c/min" -> new Parsed(v / 60.0, null);
            case "k/s", "c/s" -> new Parsed(v, null);
            default -> new Parsed(null, "RATE_UNIT_UNCONVERTIBLE:" + unit);
        };
    }

    private static String norm(String unit) {
        return unit == null ? "" : unit.trim().toLowerCase();
    }

    private Units() {}
}
