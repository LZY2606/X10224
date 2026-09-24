package dsc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A domain quantity. The raw textual representation from the instrument is
 * always preserved; normalization into canonical units happens only in the
 * derived layer. Values that cannot be converted stay in place and carry a
 * diagnostic instead of being dropped.
 */
public final class Quantity {
    public enum Dim { MASS, HEAT_FLOW, TEMPERATURE, RATE, TIME, ENERGY_PER_MASS, DIMENSIONLESS, UNKNOWN }

    public final double value;
    public final String unit;      // normalized unit, or the original unit if unconvertible
    public final String raw;       // original representation as imported
    public final Dim dim;
    public final boolean convertible;
    public final String diagnostic; // non-null when conversion failed

    private Quantity(double value, String unit, String raw, Dim dim, boolean convertible, String diagnostic) {
        this.value = value;
        this.unit = unit;
        this.raw = raw;
        this.dim = dim;
        this.convertible = convertible;
        this.diagnostic = diagnostic;
    }

    public static Quantity of(double value, String unit, Dim dim) {
        return new Quantity(value, unit, value + " " + unit, dim, true, null);
    }

    public static Quantity unconvertible(String raw, Dim dim, String diagnostic) {
        return new Quantity(Double.NaN, rawUnit(raw), raw, dim, false, diagnostic);
    }

    private static String rawUnit(String raw) {
        String[] parts = raw.trim().split("\\s+", 2".split(",")[0]);
        return parts.length > 1 ? parts[1] : "";
    }

    /** Parse "12.5 mg" style raw text into a canonical quantity. */
    public static Quantity parse(String raw, Dim expected) {
        if (raw == null || raw.isBlank()) {
            return unconvertible(String.valueOf(raw), expected, "EMPTY_VALUE");
        }
        String trimmed = raw.trim();
        String numPart = trimmed;
        String unitPart = "";
        int split = -1;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (!(Character.isDigit(c) || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E')) { split = i; break; }
        }
        if (split >= 0) { numPart = trimmed.substring(0, split); unitPart = trimmed.substring(split).trim(); }
        double v;
        try { v = Double.parseDouble(numPart.trim()); }
        catch (NumberFormatException e) { return unconvertible(raw, expected, "UNPARSEABLE_NUMBER"); }
        return convert(v, unitPart, raw, expected);
    }

    private static Quantity convert(double v, String unit, String raw, Dim expected) {
        String u = unit.toLowerCase(Locale.ROOT).replace("°", "").replace(" ", "");
        switch (expected) {
            case MASS:
                switch (u) {
                    case "mg": return new Quantity(v, "mg", raw, Dim.MASS, true, null);
                    case "g": return new Quantity(v * 1000.0, "mg", raw, Dim.MASS, true, null);
                    case "ug": case "µg": return new Quantity(v / 1000.0, "mg", raw, Dim.MASS, true, null);
                    default: return unconvertible(raw, expected, "UNIT_UNCONVERTIBLE:" + unit);
                }
            case HEAT_FLOW:
                switch (u) {
                    case "mw": return new Quantity(v, "mW", raw, Dim.HEAT_FLOW, true, null);
                    case "w": return new Quantity(v * 1000.0, "mW", raw, Dim.HEAT_FLOW, true, null);
                    case "mw/g": case "mwmg-1": return unconvertible(raw, expected, "ALREADY_NORMALIZED_NEEDS_MASS");
                    default: return unconvertible(raw, expected, "UNIT_UNCONVERTIBLE:" + unit);
                }
            case TEMPERATURE:
                switch (u) {
                    case "c": case "degc": return new Quantity(v, "C", raw, Dim.TEMPERATURE, true, null);
                    case "k": return new Quantity(v - 273.15, "C", raw, Dim.TEMPERATURE, true, null);
                    default: return unconvertible(raw, expected, "UNIT_UNCONVERTIBLE:" + unit);
                }
            case RATE:
                switch (u) {
                    case "c/min": case "k/min": return new Quantity(v, "C/min", raw, Dim.RATE, true, null);
                    case "c/s": case "k/s": return new Quantity(v * 60.0, "C/min", raw, Dim.RATE, true, null);
                    default: return unconvertible(raw, expected, "UNIT_UNCONVERTIBLE:" + unit);
                }
            default:
                return new Quantity(v, unit, raw, expected, true, null);
        }
    }

    /** Diagnostics collected alongside any derived computation. */
    public static final class Diagnostics {
        public final List<String> entries = new ArrayList<>();
        public void add(String code) { entries.add(code); }
        public void addAll(Diagnostics other) { entries.addAll(other.entries); }
        public boolean has(String code) { return entries.stream().anyMatch(e -> e.startsWith(code)); }
        public String toJson() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(entries.get(i).replace("\"", "'")).append('"');
            }
            return sb.append(']').toString();
        }
    }
}
