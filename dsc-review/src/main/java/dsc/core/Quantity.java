package dsc.core;

import java.util.Objects;

/**
 * A numeric value together with its unit. Quantities are the only numbers that
 * cross the raw/derived boundary: raw import keeps the instrument's original
 * representation ({@link RawValue}), the derived layer converts to canonical
 * units via this type.
 */
public final class Quantity {
    private final double value;
    private final Unit unit;

    public Quantity(double value, Unit unit) {
        this.value = value;
        this.unit = Objects.requireNonNull(unit);
    }

    public double value() { return value; }
    public Unit unit() { return unit; }

    public Quantity to(Unit target) {
        return new Quantity(Units.convert(value, unit, target), target);
    }

    public double valueIn(Unit target) {
        return Units.convert(value, unit, target);
    }

    @Override public boolean equals(Object o) {
        return o instanceof Quantity q && Double.compare(q.value, value) == 0 && q.unit.equals(unit);
    }
    @Override public int hashCode() { return Objects.hash(value, unit); }
    @Override public String toString() { return Units.fmt(value) + " " + unit.symbol(); }
}
