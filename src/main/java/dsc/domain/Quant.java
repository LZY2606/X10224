package dsc.domain;

import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * A physical quantity that always remembers its original textual representation.
 * Import keeps the raw form exactly as found; only the derivation layer asks for
 * a normalized canonical value. A value that cannot be converted stays in place
 * (raw retained, canonical == null) and carries a diagnostic instead of being
 * dropped to please a chart.
 */
public record Quant(String raw, Double canonical, String canonicalUnit, String dimension, String diagnostic) {

    public static final String MASS = "MASS";
    public static final String TEMPERATURE = "TEMPERATURE";
    public static final String RATE = "RATE";
    public static final String HEAT_FLOW = "HEAT_FLOW";
    public static final String SPECIFIC_HEAT_FLOW = "SPECIFIC_HEAT_FLOW";
    public static final String ENTHALPY = "ENTHALPY";
    public static final String FLOW = "FLOW";
    public static final String DIMENSIONLESS = "DIMENSIONLESS";

    private static final Map<String, String> CANON = Map.of(
            MASS, "g", TEMPERATURE, "degC", RATE, "K/min", HEAT_FLOW, "mW",
            SPECIFIC_HEAT_FLOW, "W/g", ENTHALPY, "J/g", FLOW, "mL/min", DIMENSIONLESS, "");

    public static Set<String> dimensions() { return CANON.keySet(); }

    public static Quant parse(String raw, String dimension) {
        String r = raw == null ? "" : raw.trim();
        if (r.isEmpty() || r.equalsIgnoreCase("N/A") || r.equalsIgnoreCase("null")) {
            return bad(raw, dimension, "UNCONVERTIBLE: empty value retained at source");
        }
        int split = firstDigitBoundary(r);
        String numPart = split == r.length() ? r : r.substring(0, split).trim();
        String unit = split == r.length() ? "" : r.substring(split).trim();
        OptionalDouble num;
        try {
            num = OptionalDouble.of(Double.parseDouble(numPart));
        } catch (RuntimeException e) {
            return bad(raw, dimension, "UNCONVERTIBLE: cannot parse number '" + numPart + "'");
        }
        return convert(raw, num.getAsDouble(), unit, dimension);
    }

    /** Parse with the unit supplied separately, e.g. from a CSV column header. */
    public static Quant of(double value, String unit, String dimension) {
        return convert(fmt(value) + " " + unit, value, unit, dimension);
    }

    public boolean valid() { return canonical != null; }

    private static Quant convert(String raw, double value, String unit, String dimension) {
        String u = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
        switch (dimension) {
            case MASS -> {
                switch (u) {
                    case "g", "gram", "grams" -> { return ok(raw, value, dimension); }
                    case "mg" -> { return ok(raw, value * 0.001, dimension); }
                    case "kg" -> { return ok(raw, value * 1000.0, dimension); }
                    case "ug", "µg" -> { return ok(raw, value * 1e-6, dimension); }
                    default -> { return bad(raw, dimension, "UNCONVERTIBLE: unit '" + unit + "' not known for MASS"); }
                }
            }
            case TEMPERATURE -> {
                switch (u) {
                    case "c", "°c", "degc", "deg c", "oc", "celsius" -> { return ok(raw, value, dimension); }
                    case "k", "kelvin" -> { return ok(raw, value - 273.15, dimension); }
                    default -> { return bad(raw, dimension, "UNCONVERTIBLE: unit '" + unit + "' not known for TEMPERATURE"); }
                }
            }
            case RATE -> {
                switch (u) {
                    case "k/min", "°c/min", "c/min", "k min-1", "degc/min", "c min-1" -> { return ok(raw, value, dimension); }
                    case "k/s", "°c/s", "c/s" -> { return ok(raw, value * 60.0, dimension); }
                    default -> { return bad(raw, dimension, "UNCONVERTIBLE: unit '" + unit + "' not known for RATE"); }
                }
            }
            case HEAT_FLOW -> {
                switch (u) {
                    case "mw" -> { return ok(raw, value, dimension); }
                    case "w" -> { return ok(raw, value * 1000.0, dimension); }
                    case "uw", "µw" -> { return ok(raw, value * 0.001, dimension); }
                    default -> { return bad(raw, dimension, "UNCONVERTIBLE: unit '" + unit + "' not known for HEAT_FLOW"); }
                }
            }
            case SPECIFIC_HEAT_FLOW -> {
                switch (u) {
                    case "w/g" -> { return ok(raw, value, dimension); }
                    case "mw/g" -> { return ok(raw, value * 0.001, dimension); }
                    case "w/kg" -> { return ok(raw, value * 0.001, dimension); }
                    default -> { return bad(raw, dimension, "UNCONVERTIBLE: unit '" + unit + "' not known for SPECIFIC_HEAT_FLOW"); }
                }
            }
            case ENTHALPY -> {
                switch (u) {
                    case "j/g", "kj/kg" -> { return ok(raw, value, dimension); }
                    case "j/kg" -> { return ok(raw, value * 0.001, dimension); }
                    case "mj/g" -> { return ok(raw, value * 0.001, dimension); }
                    default -> { return bad(raw, dimension, "UNCONVERTIBLE: unit '" + unit + "' not known for ENTHALPY"); }
                }
            }
            case FLOW -> {
                switch (u) {
                    case "ml/min", "mlm" -> { return ok(raw, value, dimension); }
                    default -> { return bad(raw, dimension, "UNCONVERTIBLE: unit '" + unit + "' not known for FLOW"); }
                }
            }
            case DIMENSIONLESS -> { return ok(raw, value, dimension); }
            default -> { return bad(raw, dimension, "UNCONVERTIBLE: unknown dimension " + dimension); }
        }
    }

    private static Quant ok(String raw, double canonical, String dimension) {
        return new Quant(raw, canonical, CANON.get(dimension), dimension, null);
    }

    private static Quant bad(String raw, String dimension, String diag) {
        return new Quant(raw, null, CANON.get(dimension), dimension, diag);
    }

    private static int firstDigitBoundary(String s) {
        boolean seenDigit = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c) || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E') {
                seenDigit = true;
            } else if (seenDigit) {
                return i;
            }
        }
        return s.length();
    }

    public static String fmt(double v) {
        return String.format(Locale.ROOT, "%.6f", v);
    }
}
