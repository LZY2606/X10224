package org.matlab.dsc.unit;

import java.util.Objects;

/**
 * A domain value that always carries its coordinate frame/unit. The raw
 * representation is retained on the source record; this is the normalised
 * view used by the derivation layer.
 */
public final class Quantity {
    private final double value;
    private final Unit unit;

    public Quantity(double value, Unit unit) {
        this.value = value;
        this.unit = Objects.requireNonNull(unit);
    }

    public double value() {
        return value;
    }

    public Unit unit() {
        return unit;
    }

    public Quantity to(Unit target) {
        return new Quantity(valueIn(target), target);
    }

    /** Straight scalar conversion supporting affine temperature scales. */
    public double valueIn(Unit target) {
        if (unit.dimension() != target.dimension()) {
            throw new UnitConversionException(
                    "dimension mismatch: " + unit.symbol() + " -> " + target.symbol());
        }
        double canonical = unit.toCanonical(value);
        return (canonical - target.offset) / target.factor;
    }

    @Override
    public String toString() {
        return value + " " + unit.symbol();
    }
}
