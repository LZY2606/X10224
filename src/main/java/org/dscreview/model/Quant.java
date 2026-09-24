package org.dscreview.model;

/**
 * A physical quantity. The original textual representation is always retained;
 * normalization to the internal unit happens only in the derived layer.
 */
public record Quant(double value, String unit, String raw, String basis) {
    public static Quant of(double value, String unit) {
        return new Quant(value, unit, format(value), null);
    }
    public static Quant raw(String raw) {
        return new Quant(Double.NaN, "?", raw, null);
    }
    public boolean convertible() { return !Double.isNaN(value) && unit != null && !"?".equals(unit); }
    public Quant withBasis(String b) { return new Quant(value, unit, raw, b); }
    static String format(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }
}
