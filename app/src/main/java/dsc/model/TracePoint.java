package dsc.model;

/**
 * One sampled point. Temperatures are kept in the declared unit (raw) plus a Celsius
 * normalization; heat flow is kept per declared unit plus W/g normalization.
 * {@code timeS} may be null for temperature-programmed files without time.
 */
public final class TracePoint {
    public final int index;
    public final RawField temp;
    public final RawField heatFlow;
    public final Double timeS;

    public TracePoint(int index, RawField temp, RawField heatFlow, Double timeS) {
        this.index = index;
        this.temp = temp;
        this.heatFlow = heatFlow;
        this.timeS = timeS;
    }

    public double tempC() {
        if (temp.normalized() == null) {
            throw new IllegalStateException("Point " + index + " temperature is unconvertible");
        }
        return temp.normalized();
    }

    public double heatWpg() {
        if (heatFlow.normalized() == null) {
            throw new IllegalStateException("Point " + index + " heat flow is unconvertible");
        }
        return heatFlow.normalized();
    }
}
