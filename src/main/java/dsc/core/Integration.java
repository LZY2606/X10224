package dsc.core;

import java.util.function.DoubleUnaryOperator;

/**
 * Numerical integration that stays valid for non-uniform temperature grids.
 * DSC power (W) is integrated against time (s) with a trapezoid rule, so
 * irregular sampling does not bias the area; the baseline is evaluated at the
 * actual sample temperatures via interpolation.
 */
public final class Integration {

    private Integration() {
    }

    /**
     * Trapezoid integral of y(x) over arbitrary abscissae.
     */
    public static double trapTime(double[] xs, double[] ys, int from, int to) {
        double area = 0;
        for (int i = from + 1; i <= to; i++) {
            double dx = xs[i] - xs[i - 1];
            area += 0.5 * (ys[i - 1] + ys[i]) * dx;
            if (dx <= 0) {
                throw new IllegalArgumentException("abscissae must be strictly increasing at index " + i);
            }
        }
        return area;
    }

    /**
     * Excess energy (J) inside an interval.
     *
     * @param timeS      strictly increasing times
     * @param tempK      measured temperatures (may be non-uniform in spacing)
     * @param flowW      measured heat flow in canonical W
     * @param from       first included index
     * @param to         last included index
     * @param baselineAt function temperature(K) -> baseline heat flow (W)
     */
    public static double excessEnergyJ(double[] timeS, double[] tempK, double[] flowW,
                                       int from, int to, DoubleUnaryOperator baselineAt) {
        double[] excess = new double[to - from + 1];
        for (int i = from; i <= to; i++) {
            excess[i - from] = flowW[i] - baselineAt.applyAsDouble(tempK[i]);
        }
        double[] t = new double[to - from + 1];
        System.arraycopy(timeS, from, t, 0, t.length);
        return trapTime(t, excess, 0, t.length - 1);
    }

    /**
     * Linear interpolation/extrapolation lookup on sorted arrays.
     */
    public static double interp(double[] xs, double[] ys, double x) {
        int n = xs.length;
        if (n == 1) {
            return ys[0];
        }
        if (x <= xs[0]) {
            double f = (ys[1] - ys[0]) / (xs[1] - xs[0]);
            return ys[0] + f * (x - xs[0]);
        }
        if (x >= xs[n - 1]) {
            double f = (ys[n - 1] - ys[n - 2]) / (xs[n - 1] - xs[n - 2]);
            return ys[n - 1] + f * (x - xs[n - 1]);
        }
        int lo = 0;
        int hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (xs[mid] <= x) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double f = (x - xs[lo]) / (xs[hi] - xs[lo]);
        return ys[lo] + f * (ys[hi] - ys[lo]);
    }
}
