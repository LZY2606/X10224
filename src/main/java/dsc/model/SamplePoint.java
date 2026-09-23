package dsc.model;

import dsc.units.Quantity;

/**
 * One instrument sample: a temperature / heat-flow pair exactly as emitted
 * by the instrument. {@code temperature} and {@code heatFlow} retain the
 * original unit and representation; points that cannot be converted are
 * kept with {@link Quantity#unconvertible(String, String)} plus a
 * diagnostic string rather than being removed.
 */
public record SamplePoint(int index, Quantity temperature, Quantity heatFlow, String diagnostic) {

    public boolean parseable() {
        return temperature.convertible() && heatFlow.convertible();
    }

    /** Temperature in K. */
    public double tK() {
        return temperature.canonical();
    }

    /** Heat flow in W. */
    public double hfW() {
        return heatFlow.canonical();
    }
}
