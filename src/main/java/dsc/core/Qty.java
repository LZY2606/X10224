package dsc.core;

import java.util.List;
import java.util.Objects;

/**
 * A scalar physical value carrying its unit / coordinate frame.
 *
 * Raw import data keeps its original representation ({@code rawText},
 * {@code rawUnit}); normalization happens only in the derivation layer,
 * which produces canonical quantities. A value that cannot be converted is
 * retained in place and carries a diagnostic instead of being dropped for
 * plotting convenience.
 */
public final class Qty {
    private final double value;
    private final String unit;
    private final String rawText;
    private final String rawUnit;

    public Qty(double value, String unit) {
        this(value, unit, null, null);
    }

    public Qty(double value, String unit, String rawText, String rawUnit) {
        this.value = value;
        this.unit = unit;
        this.rawText = rawText;
        this.rawUnit = rawUnit;
    }

    public static Qty raw(String text, String rawUnit) {
        return new Qty(Double.NaN, rawUnit, text, rawUnit);
    }

    public double value() { return value; }
    public String unit() { return unit; }
    public String rawText() { return rawText; }
    public String rawUnit() { return rawUnit; }
    public boolean isUnconverted() { return Double.isNaN(value); }

    /** Convert to a canonical unit. Temperature is an affine conversion. */
    public Qty to(String targetUnit) {
        if (unit != null && unit.equals(targetUnit)) return this;
        Double converted = Units.convert(value, unit, targetUnit);
        if (converted == null) {
            return new Qty(Double.NaN, targetUnit,
                    rawText != null ? rawText : Double.toString(value),
                    unit);
        }
        return new Qty(converted, targetUnit,
                rawText != null ? rawText : Double.toString(value), unit);
    }

    public Qty mul(double factor, String resultUnit) {
        return new Qty(value * factor, resultUnit,
                rawText != null ? rawText : Double.toString(value),
                rawUnit != null ? rawUnit : unit);
    }

    public Qty withValue(double v) {
        return new Qty(v, unit, rawText, rawUnit);
    }

    public static double[] values(List<Qty> qs) {
        double[] out = new double[qs.size()];
        for (int i = 0; i < out.length; i++) out[i] = qs.get(i).value();
        return out;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Qty q)) return false;
        return Double.compare(q.value, value) == 0 && Objects.equals(q.unit, unit);
    }
    @Override public int hashCode() { return Objects.hash(value, unit); }
    @Override public String toString() {
        if (isUnconverted()) return "unconverted(" + rawText + " " + rawUnit + ")";
        return value + " " + unit;
    }
}
