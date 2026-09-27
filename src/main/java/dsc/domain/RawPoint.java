package dsc.domain;

import java.util.Objects;

/**
 * A single instrument output sample. Original representations are retained;
 * convertible points additionally expose canonical values for the derivation
 * layer. Invalid points are retained with a diagnostic and excluded from
 * calculation but still plotted.
 */
public final class RawPoint {
    private final int index;
    private final Qty time;
    private final Qty temperature;
    private final Qty heatFlow;
    private final boolean valid;
    private final String diagnostic;

    public RawPoint(int index, Qty time, Qty temperature, Qty heatFlow, boolean valid, String diagnostic) {
        this.index = index;
        this.time = Objects.requireNonNull(time);
        this.temperature = Objects.requireNonNull(temperature);
        this.heatFlow = Objects.requireNonNull(heatFlow);
        this.valid = valid;
        this.diagnostic = diagnostic;
    }

    public int index() {
        return index;
    }

    public Qty time() {
        return time;
    }

    public Qty temperature() {
        return temperature;
    }

    public Qty heatFlow() {
        return heatFlow;
    }

    public boolean valid() {
        return valid;
    }

    public String diagnostic() {
        return diagnostic;
    }

    public boolean computable() {
        return valid && time.convertible() && temperature.convertible() && heatFlow.convertible();
    }

    public double canonicalTimeS() {
        return time.canonical();
    }

    public double canonicalTempK() {
        return temperature.canonical();
    }

    public double canonicalFlowW() {
        return heatFlow.canonical();
    }
}
