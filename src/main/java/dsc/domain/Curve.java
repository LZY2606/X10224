package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * One DSC segment (a single heating or cooling ramp) with its original
 * coordinates and units. Raw representation is preserved; normalization
 * happens only in the derived layer.
 */
public final class Curve {
    public final String instrumentId;
    public final int segmentIndex;
    /** "heating" | "cooling" — never mixed across segments. */
    public final String mode;
    /** "exo_up" | "exo_down" | null when the header does not declare it. */
    public final String convention;
    public final String tempUnit;
    public final String heatFlowUnit;
    public final double[] temperature;
    public final double[] heatFlow;
    public final List<String> diagnostics = new ArrayList<>();

    public Curve(String instrumentId, int segmentIndex, String mode, String convention,
                 String tempUnit, String heatFlowUnit, double[] temperature, double[] heatFlow) {
        this.instrumentId = instrumentId;
        this.segmentIndex = segmentIndex;
        this.mode = mode;
        this.convention = convention;
        this.tempUnit = tempUnit;
        this.heatFlowUnit = heatFlowUnit;
        this.temperature = temperature;
        this.heatFlow = heatFlow;
    }

    public int size() {
        return temperature.length;
    }

    /** Indices i with lo <= temperature[i] <= hi (interval in temperature coordinates). */
    public List<Integer> indicesIn(double lo, double hi) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < temperature.length; i++) {
            if (temperature[i] >= lo && temperature[i] <= hi) out.add(i);
        }
        return out;
    }

    /** Linear interpolation of heat flow at temperature t (clamped to range). */
    public double heatFlowAt(double t) {
        double[] x = temperature;
        if (t <= x[0]) return heatFlow[0];
        if (t >= x[x.length - 1]) return heatFlow[x.length - 1];
        for (int i = 1; i < x.length; i++) {
            if (t <= x[i]) {
                double f = (t - x[i - 1]) / (x[i] - x[i - 1]);
                return heatFlow[i - 1] + f * (heatFlow[i] - heatFlow[i - 1]);
            }
        }
        return heatFlow[x.length - 1];
    }
}
