package dsc.derive;

import java.util.List;

/**
 * Small numerical utilities used by the rules. All integration uses explicit
 * temperature increments, so non-uniform temperature grids integrate correctly
 * (trapezoids use each interval's own width).
 */
final class Numeric {

    private Numeric() {}

    static double[] linearFit(double[] x, double[] y) {
        int n = x.length;
        if (n < 2) throw new IllegalArgumentException("fit needs >= 2 points");
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = 0; i < n; i++) { sx += x[i]; sy += y[i]; sxx += x[i] * x[i]; sxy += x[i] * y[i]; }
        double det = n * sxx - sx * sx;
        if (Math.abs(det) < 1e-18) throw new IllegalArgumentException("degenerate fit window");
        double slope = (n * sxy - sx * sy) / det;
        double intercept = (sy - slope * sx) / n;
        return new double[]{slope, intercept};
    }

    static double rmse(double[] x, double[] y, double slope, double intercept) {
        double acc = 0;
        for (int i = 0; i < x.length; i++) {
            double r = y[i] - (slope * x[i] + intercept);
            acc += r * r;
        }
        return Math.sqrt(acc / x.length);
    }

    /** Central derivative, one-sided at the ends; works on non-uniform grids. */
    static double derivative(List<double[]> pts, int i) {
        int n = pts.size();
        if (n < 2) return 0;
        if (i == 0) {
            double dx = pts.get(1)[0] - pts.get(0)[0];
            return dx == 0 ? 0 : (pts.get(1)[1] - pts.get(0)[1]) / dx;
        }
        if (i == n - 1) {
            double dx = pts.get(n - 1)[0] - pts.get(n - 2)[0];
            return dx == 0 ? 0 : (pts.get(n - 1)[1] - pts.get(n - 2)[1]) / dx;
        }
        double x1 = pts.get(i - 1)[0], x2 = pts.get(i + 1)[0];
        return x2 == x1 ? 0 : (pts.get(i + 1)[1] - pts.get(i - 1)[1]) / (x2 - x1);
    }

    /**
     * Trapezoid integral of y over x using each interval's own width.
     * Works for increasing and decreasing x (heating/cooling). The caller
     * decides the sign convention; this function integrates along x order.
     */
    static double trapz(double[] x, double[] y) {
        double acc = 0;
        for (int i = 1; i < x.length; i++) {
            acc += (y[i - 1] + y[i]) * 0.5 * (x[i] - x[i - 1]);
        }
        return acc;
    }

    /** Signed area between the signal and the baseline over the region. */
    static double signedPeakArea(double[] t, double[] hf, double slope, double intercept) {
        double[] excess = new double[t.length];
        for (int i = 0; i < t.length; i++) excess[i] = hf[i] - (slope * t[i] + intercept);
        return trapz(t, excess);
    }
}
