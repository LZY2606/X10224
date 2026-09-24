package dsc;

import java.util.List;

/**
 * A single heating or cooling segment of one sample history.
 *
 * Coordinates are kept in the instrument's original representation as well as
 * in canonical form (K, W). {@code heatFlowSignConvention} states explicitly,
 * in the archived instrument metadata, which plotted direction is exothermic;
 * it must never be inferred from a file name.
 */
public record Segment(
        String segmentId,
        String cycle,
        String direction,          // "heating" | "cooling"
        List<Double> tempRaw,     // as imported
        String tempUnit,
        List<Double> heatRaw,     // as imported
        String heatUnit,
        List<Double> tempK,       // canonical
        List<Double> heatW,       // canonical
        double scanRateKPerMin,
        String heatFlowSignConvention, // "exo-up" | "exo-down"
        Diagnostics diagnostics,
        List<Integer> unconvertedRows // rows that could not be converted; kept in raw arrays
) {
    public int size() {
        return tempK.size();
    }

    public boolean isHeating() {
        return "heating".equals(direction);
    }

    /**
     * Heat flow with exothermic events positive regardless of plotting
     * convention. Heating and cooling curves are only compared after this
     * alignment and still never merged with each other.
     */
    public double exoUpWatts(int i) {
        return "exo-down".equals(heatFlowSignConvention) ? -heatW.get(i) : heatW.get(i);
    }

    /** Monotonicity of temperature with row index, in canonical K. */
    public boolean isMonotonicTemperature() {
        if (tempK.size() < 2) return true;
        boolean up = tempK.get(1) > tempK.get(0);
        for (int i = 1; i < tempK.size(); i++) {
            if (up && tempK.get(i) <= tempK.get(i - 1)) return false;
            if (!up && tempK.get(i) >= tempK.get(i - 1)) return false;
        }
        return true;
    }

    /** True when sampling in temperature is non-uniform beyond rounding noise. */
    public boolean isNonUniformSampling(double relativeTolerance) {
        if (tempK.size() < 3) return false;
        double[] d = new double[tempK.size() - 1];
        double sum = 0;
        for (int i = 0; i < d.length; i++) {
            d[i] = Math.abs(tempK.get(i + 1) - tempK.get(i));
            sum += d[i];
        }
        double mean = sum / d.length;
        if (mean == 0) return true;
        for (double v : d) {
            if (Math.abs(v - mean) / mean > relativeTolerance) return true;
        }
        return false;
    }
}
