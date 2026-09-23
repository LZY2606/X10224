package dsc.qty;

/**
 * A domain quantity: numeric value in a stated unit, the original textual
 * representation as imported, and the coordinate/reference basis (e.g. "ITS-90").
 * Values that cannot be parsed keep their raw text and a null value; they stay
 * at the source layer instead of being dropped to make a plot render.
 */
public record Qty(Double value, String unit, String raw, String basis) {

    public Qty {
        if (unit == null) {
            throw new IllegalArgumentException("unit is mandatory");
        }
    }

    public static Qty of(double value, String unit) {
        return new Qty(value, unit, null, null);
    }

    public static Qty of(double value, String unit, String basis) {
        return new Qty(value, unit, null, basis);
    }

    /** Parsed reading; raw text is preserved verbatim. */
    public static Qty parsed(double value, String unit, String raw, String basis) {
        return new Qty(value, unit, raw, basis);
    }

    /** Unparseable reading: keep the original token at its source position. */
    public static Qty unparseable(String raw, String claimedUnit) {
        return new Qty(null, claimedUnit, raw, null);
    }

    public boolean usable() {
        return value != null;
    }

    /** Convert to a target unit of the same dimension. */
    public Qty to(String targetUnit) {
        if (value == null) {
            throw new IllegalStateException("cannot convert an unparseable value: " + raw);
        }
        return new Qty(Unit.convert(value, unit, targetUnit), targetUnit, raw, basis);
    }

    public boolean compatibleWith(String targetUnit) {
        return Unit.compatible(unit, targetUnit);
    }

    public String display() {
        if (value == null) {
            return "RAW[" + raw + " " + unit + "]";
        }
        String s = fmt(value);
        return s + " " + unit;
    }

    private static String fmt(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return Long.toString((long) v);
        }
        return String.format(java.util.Locale.ROOT, "%.6g", v);
    }
}
