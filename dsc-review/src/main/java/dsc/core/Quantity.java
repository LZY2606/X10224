package dsc.core;

import java.util.Locale;

/**
 * Immutable value plus its unit. The raw representation is preserved elsewhere;
 * this is the normalized layer. A null value means "could not be converted" and
 * must be presented with the archived diagnostic rather than silently dropped.
 */
public final class Quantity {

    private final Double value;
    private final Unit unit;

    public Quantity(Double value, Unit unit) {
        this.value = value;
        this.unit = unit;
    }

    public static Quantity of(double value, Unit unit) {
        return new Quantity(value, unit);
    }

    public Double value() { return value; }
    public Unit unit() { return unit; }
    public boolean isConvertible() { return value != null && unit.toCanonicalFactor() != null; }

    public Quantity canonical() {
        if (value == null) return this;
        Double f = unit.toCanonicalFactor();
        if (f == null) return new Quantity(null, unit);
        return new Quantity(value * f, Unit.canonical(unit.kind()));
    }

    public String format() {
        if (value == null) return "? " + unit.symbol();
        return String.format(Locale.ROOT, "%.4g %s", value, unit.symbol()).trim();
    }

    @Override public String toString() { return format(); }
}
