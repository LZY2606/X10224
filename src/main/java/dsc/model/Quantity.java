package dsc.model;

/** A domain value that always carries its unit or coordinate basis. */
public final class Quantity {
    public final double value;
    public final String unit;

    public Quantity(double value, String unit) {
        this.value = value;
        this.unit = unit;
    }

    @Override
    public String toString() {
        return value + " " + unit;
    }
}
