package dsc.core;

/**
 * A fitted baseline evaluated on the analysis interval.
 * {@code values[i]} is the baseline heat flow (W) at point index i.
 */
public record BaselineModel(
        String kind,
        double[] indexValues,
        double[] values,
        double fitRmseW,
        double[] params,
        String basis
) {
}
