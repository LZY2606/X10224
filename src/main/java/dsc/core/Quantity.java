package dsc.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * A physical value that always carries its unit/coordinate basis plus the exact
 * textual representation the instrument produced. Normalisation happens only in
 * the derivation layer; a value that cannot be converted is retained verbatim
 * with a diagnostic instead of being dropped for plotting.
 */
public final class Quantity {
    @JsonProperty public double value;
    @JsonProperty public String unit;
    @JsonProperty public String raw;

    public Quantity() {}

    public Quantity(double value, String unit) {
        this(value, unit, format(value, unit));
    }

    public Quantity(double value, String unit, String raw) {
        this.value = value;
        this.unit = unit == null ? "" : unit;
        this.raw = raw == null ? format(value, this.unit) : raw;
    }

    /** Retains an unconverted value in place: numeric sentinel is NaN. */
    public static Quantity unconverted(String raw, String requestedUnit) {
        return new Quantity(Double.NaN, requestedUnit, raw);
    }

    public boolean convertible() { return !Double.isNaN(value); }

    private static String format(double v, String u) {
        if (Double.isNaN(v)) return "";
        String s = (v == Math.rint(v)) ? String.valueOf((long) v) : String.valueOf(v);
        return u == null || u.isEmpty() ? s : s + " " + u;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Quantity q)) return false;
        return Double.compare(value, q.value) == 0 && unit.equals(q.unit) && raw.equals(q.raw);
    }
    @Override public int hashCode() { return Objects.hash(value, unit, raw); }
    @Override public String toString() { return convertible() ? raw : raw + " [unconverted -> " + unit + "]"; }
}
