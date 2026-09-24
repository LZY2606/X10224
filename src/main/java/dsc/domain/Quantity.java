package dsc.domain;

import java.util.Objects;

/**
 * A domain quantity. The raw representation (as imported) is always preserved;
 * normalization to a canonical unit happens only in the derived layer.
 * Values that cannot be converted stay in place and carry a diagnostic.
 */
public final class Quantity {
    public final String raw;        // original textual representation, never rewritten
    public final Double value;      // parsed numeric value in `unit`, null if unparsable
    public final String unit;       // declared unit, may be null/unknown
    public final String basis;      // coordinate basis, e.g. "sample_temperature", "time", "heat_flow"
    public final String diagnostic; // non-null when the value could not be normalized

    private Quantity(String raw, Double value, String unit, String basis, String diagnostic) {
        this.raw = raw;
        this.value = value;
        this.unit = unit;
        this.basis = basis;
        this.diagnostic = diagnostic;
    }

    public static Quantity of(String raw, String unit, String basis) {
        Double v = null;
        String diag = null;
        try {
            v = Double.parseDouble(raw.trim());
        } catch (Exception e) {
            diag = "unparsable value kept in place: '" + raw + "'";
        }
        if (v != null && !knownUnit(unit)) {
            diag = "unknown unit '" + unit + "' kept unconverted";
        }
        return new Quantity(raw, v, unit, basis, diag);
    }

    public static Quantity invalid(String raw, String unit, String basis, String diagnostic) {
        return new Quantity(raw, null, unit, basis, diagnostic);
    }

    private static boolean knownUnit(String u) {
        if (u == null) return false;
        return switch (u) {
            case "mW", "W", "W/g", "mW/mg", "J/g", "kJ/mol",
                 "C", "K", "mg", "g", "K/min", "K/s", "C/min", "s", "min", "1" -> true;
            default -> false;
        };
    }

    /** Convert to target unit; returns a diagnostic-carrying quantity when impossible. */
    public Quantity convertTo(String target) {
        if (value == null) return invalid(raw, unit, basis, diagnostic != null ? diagnostic : "no numeric value");
        if (Objects.equals(unit, target)) return this;
        Double out = switch (unit + "->" + target) {
            case "mW->W" -> value / 1000.0;
            case "W->mW" -> value * 1000.0;
            case "C->K" -> value + 273.15;
            case "K->C" -> value - 273.15;
            case "mg->g" -> value / 1000.0;
            case "g->mg" -> value * 1000.0;
            case "K/min->K/s" -> value / 60.0;
            case "C/min->K/min" -> value;       // rate intervals identical
            case "C/min->K/s" -> value / 60.0;
            case "min->s" -> value * 60.0;
            case "s->min" -> value / 60.0;
            case "mW/mg->W/g" -> value;         // 1 mW/mg == 1 W/g
            default -> null;
        };
        if (out == null) {
            return invalid(raw, unit, basis,
                    "cannot convert '" + unit + "' to '" + target + "'; value left at original position");
        }
        return new Quantity(raw, out, target, basis, null);
    }

    public boolean ok() { return value != null && diagnostic == null; }

    @Override public String toString() {
        return raw + " " + (unit == null ? "?" : unit) + (diagnostic != null ? "  !" + diagnostic : "");
    }
}
