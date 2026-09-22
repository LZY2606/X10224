package dsc.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Unit parsing and normalization. Normalization happens only in the derived
 *  layer; raw representations are preserved at import. Values that cannot be
 *  converted stay in place and produce a diagnostic instead of being dropped. */
public final class Units {

    private static final Pattern QTY = Pattern.compile(
            "^\\s*([-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?)\\s*([a-zA-Z/%°^ ]*?)\\s*$");

    private static final Map<String, String> CANONICAL = new LinkedHashMap<>();
    static {
        CANONICAL.put("mg", "mg");
        CANONICAL.put("g", "mg");        // converted via factor
        CANONICAL.put("mW", "mW");
        CANONICAL.put("mJ/s", "mW");
        CANONICAL.put("W", "mW");
        CANONICAL.put("K/min", "K/min");
        CANONICAL.put("C/min", "K/min");
        CANONICAL.put("K/s", "K/min");
        CANONICAL.put("°C", "C");
        CANONICAL.put("C", "C");
        CANONICAL.put("K", "K");
        CANONICAL.put("mL/min", "mL/min");
        CANONICAL.put("ml/min", "mL/min");
    }

    public record Parsed(double value, String canonicalUnit) {}

    /** Parse "12.4 mg" into value + canonical unit. Returns null when unconvertible. */
    public static Parsed parse(String raw) {
        if (raw == null) return null;
        Matcher m = QTY.matcher(raw);
        if (!m.matches()) return null;
        String unit = m.group(2).trim();
        if (!CANONICAL.containsKey(unit)) return null;
        double value = Double.parseDouble(m.group(1));
        String canonical = CANONICAL.get(unit);
        switch (unit) {
            case "g" -> value = value * 1000.0;
            case "W" -> value = value * 1000.0;
            case "mJ/s" -> { /* 1 mJ/s == 1 mW */ }
            case "K/s" -> value = value * 60.0;
            default -> { }
        }
        return new Parsed(value, canonical);
    }

    private Units() {}
}
