package dsc.core;

/** A value that always travels with its unit / coordinate basis. */
public record Quantity(double value, Unit unit) {
    public Quantity to(Unit target) {
        return new Quantity(unit.convert(value, target), target);
    }

    public boolean isConvertibleTo(Unit target) {
        return unit.convertibleTo(target);
    }

    public double as(Unit target) {
        return unit.convert(value, target);
    }

    /** Original representation is preserved verbatim at import; this is the normalized layer. */
    public double canonical() { return unit.toCanonical(value); }

    @Override public String toString() {
        return QuantityFormat.trim(value) + " " + unit.symbol;
    }
}
