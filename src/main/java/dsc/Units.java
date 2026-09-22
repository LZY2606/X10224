package dsc;

import java.util.HashMap;
import java.util.Map;

/**
 * Units and coordinate basis for DSC domain quantities.
 * Raw representations are preserved at import; conversion happens only in the
 * derived layer. A value whose unit cannot be converted is returned unchanged
 * and the caller attaches a diagnostic instead of dropping it.
 */
public final class Units {

    /** A quantity carrying value + unit + coordinate basis (e.g. temperature axis direction). */
    public record Quantity(double value, String unit, String basis) {
        public Quantity(double value, String unit) { this(value, unit, ""); }
        @Override public String toString() {
            return value + " " + unit + (basis.isEmpty() ? "" : " [" + basis + "]");
        }
    }

    /** Result of an attempted conversion; either converted value or a diagnostic. */
    public record Conversion(boolean ok, double value, String diagnostic) {
        static Conversion ok(double v) { return new Conversion(true, v, null); }
        static Conversion fail(String d) { return new Conversion(false, Double.NaN, d); }
    }

    private static final Map<String, Double> TO_CANONICAL = new HashMap<>();
    static {
        // canonical heat-flow unit: mW
        TO_CANONICAL.put("hf:mW", 1.0);
        TO_CANONICAL.put("hf:W", 1000.0);
        // canonical temperature unit: degC (K converted with offset, handled separately)
        TO_CANONICAL.put("t:degC", 1.0);
        // canonical time unit: s
        TO_CANONICAL.put("time:s", 1.0);
        TO_CANONICAL.put("time:min", 60.0);
        // canonical mass unit: mg
        TO_CANONICAL.put("mass:mg", 1.0);
        TO_CANONICAL.put("mass:g", 1000.0);
        // canonical scan rate unit: K/min
        TO_CANONICAL.put("rate:K/min", 1.0);
        TO_CANONICAL.put("rate:K/s", 60.0);
        // canonical enthalpy unit: J/g
        TO_CANONICAL.put("H:J/g", 1.0);
        TO_CANONICAL.put("H:kJ/mol", Double.NaN); // needs molar mass: not convertible here
    }

    /**
     * Convert a quantity of the given dimension ("hf","t","time","mass","rate","H")
     * to the canonical unit of that dimension. Unknown units or conversions that
     * need extra context (e.g. kJ/mol without molar mass) fail with a diagnostic.
     */
    public static Conversion toCanonical(String dimension, Quantity q) {
        if (dimension.equals("t") && q.unit().equals("K")) {
            return Conversion.ok(q.value() - 273.15);
        }
        Double factor = TO_CANONICAL.get(dimension + ":" + q.unit());
        if (factor == null) {
            return Conversion.fail("unknown-unit:" + q.unit() + " for dimension " + dimension);
        }
        if (factor.isNaN()) {
            return Conversion.fail("unconvertible-without-context:" + q.unit());
        }
        return Conversion.ok(q.value() * factor);
    }

    public static boolean isKnown(String dimension, String unit) {
        if (dimension.equals("t") && unit.equals("K")) return true;
        Double f = TO_CANONICAL.get(dimension + ":" + unit);
        return f != null && !f.isNaN();
    }

    public static String canonicalUnit(String dimension) {
        return switch (dimension) {
            case "hf" -> "mW";
            case "t" -> "degC";
            case "time" -> "s";
            case "mass" -> "mg";
            case "rate" -> "K/min";
            case "H" -> "J/g";
            default -> throw new IllegalArgumentException("dimension " + dimension);
        };
    }

    private Units() {}
}
