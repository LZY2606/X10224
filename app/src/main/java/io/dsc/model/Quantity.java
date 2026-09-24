package io.dsc.model;

import java.util.Objects;
import java.util.Optional;

/**
 * A domain quantity: numeric value in a stated unit, plus the original textual
 * representation as imported. Display values can always cite both the
 * normalised and the original representation.
 */
public final class Quantity {
    private final double value;
    private final Unit unit;
    private final String rawText;

    private Quantity(double value, Unit unit, String rawText) {
        this.value = value;
        this.unit = unit;
        this.rawText = rawText;
    }

    public static Quantity of(double value, Unit unit) {
        return new Quantity(value, unit, null);
    }

    /** Keeps raw text exactly as found in the import. */
    public static Quantity parsed(double value, Unit unit, String rawText) {
        return new Quantity(value, unit, rawText);
    }

    public double value() { return value; }
    public Unit unit() { return unit; }
    public Optional<String> rawText() { return Optional.ofNullable(rawText); }

    /** Normalised value in the canonical base unit (derivation layer only). */
    public double baseValue() { return unit.toBase(value); }

    public Quantity convertedTo(Unit target) {
        if (!unit.convertibleTo(target)) {
            throw new IllegalArgumentException(unit + " not convertible to " + target);
        }
        return new Quantity(target.fromBase(unit.toBase(value)), target, rawText);
    }

    @Override public boolean equals(Object o) {
        return o instanceof Quantity q && Double.compare(q.value, value) == 0 && q.unit == unit;
    }
    @Override public int hashCode() { return Objects.hash(value, unit); }
    @Override public String toString() {
        return trim(value) + " " + unit.symbol();
    }

    static String trim(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
        return String.format("%.6g", v);
    }
}
