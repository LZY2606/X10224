package dsc.domain;

/**
 * One sampled row. Temperature and heat flow keep their original lexical token
 * and unit; {@code index} is the stable within-trace position. Time is optional
 * (kept as raw seconds when present).
 */
public record SamplePoint(int index, Quantity temperatureC, Quantity heatFlowMw, Quantity timeS) {

    /** Canonical temperature in degrees Celsius; NaN if the row was not convertible. */
    public double t() { return temperatureC.value(); }

    /** Canonical heat flow in milliwatts as recorded (instrument sign, not canonical sign). */
    public double hf() { return heatFlowMw.value(); }

    public boolean usable() {
        return temperatureC.convertible() && heatFlowMw.convertible();
    }
}
