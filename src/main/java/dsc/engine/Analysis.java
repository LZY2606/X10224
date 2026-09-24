package dsc.engine;

import java.util.ArrayList;
import java.util.List;

/** Small deterministic numeric helpers used by the derivation layer. */
public final class Analysis {
    private Analysis() {}

    public record Line(double m, double b) {
        public double at(double x) { return m * x + b; }
    }

    /** Least-squares line over a contiguous index range [lo, hi]. */
    public static Line fit(double[] x, double[] y, int lo, int hi) {
        int n = hi - lo + 1;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = lo; i <= hi; i++) {
            sx += x[i]; sy += y[i]; sxx += x[i] * x[i]; sxy += x[i] * y[i];
        }
        double det = n * sxx - sx * sx;
        if (Math.abs(det) < 1e-18) return new Line(0, y[lo]);
        double m = (n * sxy - sx * sy) / det;
        double b = (sy - m * sx) / n;
        return new Line(m, b);
    }

    /** Intersection x-coordinate of two lines. */
    public static double intersectX(Line a, Line c) {
        if (Math.abs(a.m() - c.m()) < 1e-15) return Double.NaN;
        return (c.b() - a.b()) / (a.m() - c.m());
    }

    public static double[] subArray(double[] a, int lo, int hi) {
        double[] r = new double[hi - lo + 1];
        System.arraycopy(a, lo, r, 0, r.length);
        return r;
    }

    public static List<Integer> localExtrema(double[] y, int minSeparation) {
        List<Integer> out = new ArrayList<>();
        for (int i = 1; i < y.length - 1; i++) {
            if (y[i] >= y[i - 1] && y[i] >= y[i + 1] && y[i] > y[i - 1] && y[i] > y[i + 1]) {
                if (out.isEmpty() || i - out.get(out.size() - 1) >= minSeparation) out.add(i);
            }
        }
        return out;
    }
}
