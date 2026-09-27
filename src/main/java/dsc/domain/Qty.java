package dsc.domain;

import java.util.Objects;

/**
 * An original measured value kept verbatim together with its unit.
 * Normalization to canonical units happens only in the derivation layer.
 * Unconvertible values remain in place with {@code convertible=false} and a
 * diagnostic rather than being discarded for plotting.
 */
public final class Qty {
    private final double originalValue;
    private final String originalUnit;
    private final boolean convertible;
    private final String diagnostic;

    private Qty(double originalValue, String originalUnit, boolean convertible, String diagnostic) {
        this.originalValue = originalValue;
        this.originalUnit = originalUnit;
        this.convertible = convertible;
        this.diagnostic = diagnostic;
    }

    public static Qty of(double value, String unitSymbol) {
        Unit u = Unit.fromSymbol(unitSymbol);
        if (u == null) {
            return new Qty(value, unitSymbol, false,
                    "unknown unit '" + unitSymbol + "': value kept in original representation");
        }
        return new Qty(value, u.symbol(), true, null);
    }

    public static Qty canonical(double value, Unit unit) {
        return new Qty(unit.fromCanonical(value), unit.symbol(), true, null);
    }

    public double originalValue() {
        return originalValue;
    }

    public String originalUnit() {
        return originalUnit;
    }

    public boolean convertible() {
        return convertible;
    }

    public String diagnostic() {
        return diagnostic;
    }

    public Unit unit() {
        return Unit.fromSymbol(originalUnit);
    }

    /** Canonical value; only available when the unit is convertible. */
    public double canonical() {
        if (!convertible) {
            throw new IllegalStateException("value not convertible: " + diagnostic);
        }
        return unit().toCanonical(originalValue);
    }

    public String format() {
        return formatValue(originalValue) + " " + (originalUnit == null ? "" : originalUnit);
    }

    public static String formatValue(double v) {
        if (v == 0) {
            return "0";
        }
        double a = Math.abs(v);
        if (a >= 1e6 || a < 1e-3) {
            return String.format("%.6g", v);
        }
        return String.format("%.4g", v);
    }

    @Override
    public String toString() {
        return format();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Qty)) return false;
        Qty qty = (Qty) o;
        return Double.compare(qty.originalValue, originalValue) == 0
                && Objects.equals(originalUnit, qty.originalUnit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalValue, originalUnit);
    }
}
