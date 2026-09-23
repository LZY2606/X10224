package io.dscreview.domain;

import java.util.List;

/**
 * Immutable sampled signal. Temperatures are stored in °C (conversion happens
 * only on the derived layer), heat flow in mW with an explicit endotherm
 * convention. {@code endoPositive} is +1 when positive y means endothermic,
 * -1 when it means exothermic; it is read from instrument metadata, never from
 * the file name.
 */
public record Series(
        List<Double> temperatureC,
        List<Double> heatFlowMw,
        boolean endoPositive) {

    public int size() { return temperatureC.size(); }

    /** Index of the nearest sample to the given temperature. */
    public int nearestIndex(double tempC) {
        int best = 0;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < temperatureC.size(); i++) {
            double d = Math.abs(temperatureC.get(i) - tempC);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    /** Sorted ascending copy; also returns the permutation to trace back. */
    public double[] tempsArray() {
        return temperatureC.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
