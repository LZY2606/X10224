package dsc.model;

/** A domain quantity that always carries its unit or coordinate basis. */
public record Quantity(double value, String unit) {
    @Override
    public String toString() {
        return value + " " + unit;
    }
}
