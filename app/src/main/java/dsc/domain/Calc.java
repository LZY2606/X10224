package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/** Pure numeric DSC math. No I/O, deterministic — fully covered by tests. */
public final class Calc {
    private Calc() {}

    public static final class Series {
        public final double[] t;
        public final double[] hf;
        public Series(double[] t, double[] hf) { this.t = t; this.hf = hf; }
        public int size() { return t.length; }
    }

    /** Linear interpolation; extrapolates flat outside range. */
    public static double interp(double[] xs, double[] ys, double x) {
        int n = xs.length;
        if (n == 0) return Double.NaN;
        if (x <= xs[0]) return ys[0];
        if (x >= xs[n - 1]) return ys[n - 1];
        int lo = 0, hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (xs[mid] <= x) lo = mid; else hi = mid;
        }
        double span = xs[hi] - xs[lo];
        if (span == 0) return ys[lo];
        return ys[lo] + (ys[hi] - ys[lo]) * (x - lo == 0 ? 0 : (x - xs[lo]) / span);
    }

    /** Trapezoid integration of y over x. */
    public static double trapz(double[] x, double[] y) {
        double a = 0;
        for (int i = 1; i < x.length; i++) {
            a += 0.5 * (y[i - 1] + y[i]) * (x[i] - x[i - 1]);
        }
        return a;
    }

    /**
     * Temperature-grid uniformity check on the raw sampling: non-uniform spacing must be flagged,
     * never silently resampled away.
     */
    public static double[] spacings(double[] t) {
        double[] d = new double[Math.max(0, t.length - 1)];
        for (int i = 1; i < t.length; i++) d[i - 1] = t[i] - t[i - 1];
        return d;
    }

    public static boolean isUniform(double[] t, double relTol) {
        if (t.length < 3) return true;
        double[] d = spacings(t);
        double mean = 0;
        for (double v : d) mean += v;
        mean /= d.length;
        if (mean == 0) return false;
        for (double v : d) if (Math.abs(v - mean) > relTol * Math.abs(mean)) return false;
        return true;
    }

    /** Moving-average smoothing over an (assumed sorted) temperature window. */
    public static double[] smooth(double[] t, double[] y, double windowC) {
        int n = y.length;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0; int cnt = 0;
            for (int j = 0; j < n; j++) {
                if (Math.abs(t[j] - t[i]) <= windowC / 2.0) { sum += y[j]; cnt++; }
            }
            out[i] = sum / cnt;
        }
        return out;
    }

    public static double[] derivative(double[] t, double[] y) {
        int n = y.length;
        double[] d = new double[n];
        for (int i = 0; i < n; i++) {
            int lo = Math.max(0, i - 1), hi = Math.min(n - 1, i + 1);
            double dt = t[hi] - t[lo];
            d[i] = dt == 0 ? 0 : (y[hi] - y[lo]) / dt;
        }
        return d;
    }

    public static int argMax(double[] a, int lo, int hi) {
        int b = lo;
        for (int i = lo; i <= hi && i < a.length; i++) if (a[i] > a[b]) b = i;
        return b;
    }

    public static int argMin(double[] a, int lo, int hi) {
        int b = lo;
        for (int i = lo; i <= hi && i < a.length; i++) if (a[i] < a[b]) b = i;
        return b;
    }

    /**
     * Fit a straight line y = a + b*x to the given index windows (anchor regions of an interval).
     * Returns [intercept, slope].
     */
    public static double[] linearFit(double[] x, double[] y, int lo, int hi) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = 0;
        for (int i = lo; i <= hi; i++) {
            sx += x[i]; sy += y[i]; sxx += x[i] * x[i]; sxy += x[i] * y[i]; n++;
        }
        double det = n * sxx - sx * sx;
        if (det == 0) return new double[]{sy / Math.max(1, n), 0};
        double b = (n * sxy - sx * sy) / det;
        double a = (sy - b * sx) / n;
        return new double[]{a, b};
    }

    public static int[] indexWindow(double[] t, double t0, double t1) {
        int lo = -1, hi = -1;
        for (int i = 0; i < t.length; i++) {
            if (t[i] >= t0 && lo < 0) lo = i;
            if (t[i] <= t1) hi = i;
        }
        return new int[]{Math.max(0, lo), Math.min(t.length - 1, hi)};
    }

    public static List<Integer> localExtrema(double[] y, double minProminence, boolean maxima) {
        List<Integer> out = new ArrayList<>();
        for (int i = 2; i < y.length - 2; i++) {
            boolean extrem = maxima ? y[i] > y[i-1] && y[i] >= y[i+1]
                                    : y[i] < y[i-1] && y[i] <= y[i+1];
            if (!extrem) continue;
            double shoulder = Math.max(Math.abs(y[i] - y[i-2]), Math.abs(y[i] - y[i+2]));
            if (shoulder >= minProminence) out.add(i);
        }
        return out;
    }
}
