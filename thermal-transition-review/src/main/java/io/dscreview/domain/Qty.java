package io.dscreview.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * A scalar physical quantity carrying its unit and the original textual
 * representation exactly as imported. Normalisation happens only on the
 * derived layer; if conversion fails the value stays where it was together
 * with a diagnostic.
 */
public final class Qty {
    private final double value;
    private final String unit;
    private final String rawText;

    public Qty(double value, String unit, String rawText) {
        this.value = value;
        this.unit = Objects.requireNonNull(unit, "unit");
        this.rawText = rawText == null ? format(value, unit) : rawText;
    }

    public static Qty of(double value, String unit) {
        return new Qty(value, unit, null);
    }

    public double value() { return value; }
    public String unit() { return unit; }
    public String rawText() { return rawText; }

    private static String format(double v, String u) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return String.format(Locale.ROOT, "%d %s", (long) v, u);
        }
        return String.format(Locale.ROOT, "%s %s", Double.toString(v), u);
    }

    /** Convert to a target unit. Returns null when conversion is impossible. */
    public Double convertTo(String targetUnit) {
        return Units.convert(value, unit, targetUnit);
    }

    @Override public String toString() { return rawText; }
}
