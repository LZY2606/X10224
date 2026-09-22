package dsc.derive;

import java.util.ArrayList;
import java.util.List;

/**
 * Numerical primitives. Integration is trapezoidal over whatever abscissa the samples
 * actually carry, so non-uniform temperature spacing is integrated correctly rather than
 * assuming a constant step.
 */
public final class Numeric {
    private Numeric() {}

    /** Trapezoid of y over x; works for non-uniform x. x must be monotonic. */
    public static double trapz(double[] x, double[] y, int lo, int hi) {
        double a = 0;
        for (int i = lo; i < hi; i++) a += 0.5 * (y[i] + y[i + 1]) * (x[i + 1] - x[i]);
        return a;
    }

    public static int minIndex(double[] y, int lo, int hi) {
        int m = lo; for (int i = lo + 1; i <= hi; i++) if (y[i] < y[m]) m = i; return m;
    }

    public static int maxIndex(double[] y, int lo, int hi) {
        int m = lo; for (int i = lo + 1; i <= hi; i++) if (y[i] > y[m]) m = i; return m;
    }

    /** Linear interpolation; index of lower knot. */
    public static int lowerKnot(double[] x, double xv) {
        int lo = 0, hi = x.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (x[mid] <= xv) lo = mid; else hi = mid - 1;
        }
        return Math.min(lo, x.length - 2);
    }

    public static double interp(double[] x, double[] y, double xv) {
        int i = lowerKnot(x, xv);
        double f = (xv - x[i]) / (x[i + 1] - x[i]);
        return y[i] + f * (y[i + 1] - y[i]);
    }

    public static double mean(double[] y, int lo, int hi) {
        double s = 0; int n = 0;
        for (int i = lo; i <= hi; i++) { s += y[i]; n++; }
        return n == 0 ? Double.NaN : s / n;
    }

    /** Simple finite-difference slope over a window, robust for onset tangent. */
    public static double slopeAt(double[] x, double[] y, int idx, int halfWin) {
        int lo = Math.max(0, idx - halfWin), hi = Math.min(x.length - 1, idx + halfWin);
        double sx = 0, sy = 0, sxx = 0, sxy = 0; int n = hi - lo + 1;
        for (int i = lo; i <= hi; i++) { sx += x[i]; sy += y[i]; sxx += x[i]*x[i]; sxy += x[i]*y[i]; }
        double den = n*sxx - sx*sx;
        return den == 0 ? 0 : (n*sxy - sx*sy)/den;
    }

    /**
     * Onset = intersection of the pre-transition tangent (fitted on [lo, foot]) with
     * the tangent through the steepest point. Endset mirrors this on the trailing side.
     */
    public static double[] tangentIntersection(double[] x, double[] y, int lo, int hi, boolean leading) {
        int peak = leading ? maxIndex(y, lo, hi) : minIndex(y, lo, hi);
        int win = Math.max(2, (hi - lo) / 12);
        int footLo = leading ? lo : peak;
        int footHi = leading ? Math.min(peak, lo + win) : hi;
        double mBase = slopeAt(x, y, leading ? lo : hi, win);
        double bBase = y[leading ? lo : hi] - mBase * x[leading ? lo : hi];
        int steep = steepest(x, y, lo, hi);
        double mLead = slopeAt(x, y, steep, win);
        double bLead = y[steep] - mLead * x[steep];
        if (Math.abs(mLead - mBase) < 1e-12) return new double[]{x[steep], mLead, bLead};
        double xstar = (bBase - bLead) / (mLead - mBase);
        return new double[]{xstar, leading ? mLead : mBase, bBase};
    }

    public static int steepest(double[] x, double[] y, int lo, int hi) {
        int best = lo; double bestAbs = -1;
        for (int i = lo + 1; i < hi; i++) {
            double d = Math.abs((y[i+1]-y[i-1])/(x[i+1]-x[i-1]));
            if (d > bestAbs) { bestAbs = d; best = i; }
        }
        return best;
    }

    public static List<double[]> resample(double[] x, double[] y, int n) {
        double x0 = x[0], x1 = x[x.length - 1];
        double[] rx = new double[n], ry = new double[n];
        for (int i = 0; i < n; i++) {
            double xv = x0 + (x1 - x0) * i / (n - 1);
            rx[i] = xv; ry[i] = interp(x, y, xv);
        }
        List<double[]> out = new ArrayList<>(); out.add(rx); out.add(ry); return out;
    }
}
