package dsc.model;

/** One versioned sample-mass statement with balance uncertainty. */
public record MassVersion(String massId, double value, String unit, double uncertaintyMg,
                          String note, int revision) {
    public double mg() { return Quantity.toMg(value, unit); }
}
