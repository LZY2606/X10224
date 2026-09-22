package dsc.derive;

import java.util.ArrayList;
import java.util.List;

/**
 * (temperature, value) series on a possibly non-uniform temperature grid.
 * Integration is always trapezoidal over the actual grid — never assumes equal spacing.
 */
public final class Curve {
    public final double[] t;
    public final double[] y;

    public Curve(double[] t, double[] y) {
        if (t.length != y.length || t.length == 0) {
            throw new IllegalArgumentException("bad curve");
        }
        this.t = t;
        this.y = y;
    }

    public int size() {
        return t.length;
    }

    public boolean uniformSpacing() {
        double d0 = Math.abs(t[1] - t[0]);
        for (int i = 2; i < t.length; i++) {
            if (Math.abs(Math.abs(t[i] - t[i - 1]) - d0) > 1e-9) {
                return false;
            }
        }
        return true;
    }

    /** Linear interpolation at temperature x; clamps to endpoints outside the grid. */
    public double at(double x) {
        if (x <= t[0]) {
            return y[0];
        }
        if (x >= t[t.length - 1]) {
            return y[y.length - 1];
        }
        for (int i = 1; i < t.length; i++) {
            if (t[i] >= x) {
                double f = (x - t[i - 1]) / (t[i] - t[i - 1]);
                return y[i - 1] + f * (y[i] - y[i - 1]);
            }
        }
        return y[y.length - 1];
    }

    /** Indices of grid points strictly inside [from, to]. */
    public List<Integer> inside(double from, double to) {
        List<Integer> idx = new ArrayList<>();
        double lo = Math.min(from, to);
        double hi = Math.max(from, to);
        for (int i = 0; i < t.length; i++) {
            if (t[i] >= lo && t[i] <= hi) {
                idx.add(i);
            }
        }
        return idx;
    }

    /** Trapezoidal integral of (y - baseline) dT over [from,to] on the real grid. */
    public double integrateMinus(double from, double to, BaselineFn baseline) {
        double lo = Math.min(from, to);
        double hi = Math.max(from, to);
        List<Integer> idx = inside(lo, hi);
        if (idx.isEmpty()) {
            return Double.NaN;
        }
        double area = 0.0;
        double prevT = lo;
        double prevV = at(lo) - baseline.value(lo);
        for (int i : idx) {
            double v = y[i] - baseline.value(t[i]);
            area += (v + prevV) / 2.0 * (t[i] - prevT);
            prevT = t[i];
            prevV = v;
        }
        double endV = at(hi) - baseline.value(hi);
        area += (endV + prevV) / 2.0 * (hi - prevT);
        return area;
    }

    /** Index of the extremum of (y - baseline) within [from,to], by absolute deviation. */
    public int peakIndex(double from, double to, BaselineFn baseline) {
        List<Integer> idx = inside(from, to);
        if (idx.isEmpty()) {
            return -1;
        }
        int best = idx.get(0);
        double bestAbs = -1;
        for (int i : idx) {
            double d = Math.abs(y[i] - baseline.value(t[i]));
            if (d > bestAbs) {
                bestAbs = d;
                best = i;
            }
        }
        return best;
    }

    public interface BaselineFn {
        double value(double x);
    }
}
