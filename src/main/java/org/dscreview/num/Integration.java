package org.dscreview.num;

/**
 * Numerical integration on possibly non-uniform temperature grids.
 * Uses the trapezoid rule on the actual (time, signal) samples; when integrating
 * against temperature the non-equal spacing is respected explicitly.
 */
public final class Integration {
    private Integration() {}

    /** Trapezoid area of y(x) over points lo..hi inclusive. Returns 0 for an empty range. */
    public static double trapezoid(double[] x, double[] y, int lo, int hi) {
        if (x == null || y == null || hi - lo < 1 || lo < 0 || hi >= x.length) return 0.0;
        double area = 0.0;
        for (int i = lo; i < hi; i++) {
            double dx = x[i + 1] - x[i];
            area += (y[i] + y[i + 1]) * 0.5 * dx;
        }
        return area;
    }

    /** Integrate signal vs temperature over the given index span (x in degC). */
    public static double vsTemperature(double[] tDegC, double[] signalMw, int[] idx) {
        if (idx.length < 2) return 0.0;
        double area = 0.0;
        for (int k = 0; k + 1 < idx.length; k++) {
            int i = idx[k], j = idx[k + 1];
            double dt = tDegC[j] - tDegC[i];
            area += (signalMw[i] + signalMw[j]) * 0.5 * dt;
        }
        return area; // mW * degC
    }
}
