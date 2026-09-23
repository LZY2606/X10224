package com.dsc.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure DSC numerics. Functions never assume uniform temperature spacing;
 * empty selections produce diagnostics instead of fabricated numbers.
 */
public final class DscMath {
    private DscMath() {}

    public record Series(double[] temp, double[] hf) {
        public int size() { return temp.length; }
        /** Indices with temp in [t0, t1] (inclusive), order preserved. */
        public int[] window(double t0, double t1) {
            double lo = Math.min(t0, t1), hi = Math.max(t0, t1);
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < temp.length; i++) {
                if (temp[i] >= lo && temp[i] <= hi) idx.add(i);
            }
            return idx.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    public record Diagnostic(String code, String message) {}

    /** y = a*T + b */
    public record Line(double a, double b) {
        public double at(double t) { return a * t + b; }
    }

    /** Linear baseline through the two interval endpoints. */
    public static Line linearBaseline(Series s, double t0, double t1) {
        int[] w = s.window(t0, t1);
        if (w.length < 2) throw new EmptyInterval(t0, t1);
        double ta = s.temp()[w[0]], tb = s.temp()[w[w.length - 1]];
        double ya = s.hf()[w[0]], yb = s.hf()[w[w.length - 1]];
        if (tb == ta) throw new EmptyInterval(t0, t1);
        double a = (yb - ya) / (tb - ta);
        return new Line(a, ya - a * ta);
    }

    /** Least-squares line over the given indices (edge windows). */
    public static Line leastSquares(Series s, int[] idx) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = idx.length;
        if (n < 2) throw new EmptyInterval(Double.NaN, Double.NaN);
        for (int i : idx) {
            double x = s.temp()[i], y = s.hf()[i];
            sx += x; sy += y; sxx += x * x; sxy += x * y;
        }
        double denom = n * sxx - sx * sx;
        if (denom == 0) throw new EmptyInterval(Double.NaN, Double.NaN);
        double a = (n * sxy - sx * sy) / denom;
        return new Line(a, (sy - a * sx) / n);
    }

    /**
     * Sigmoidal (logistic) baseline between pre/post edge lines, used for
     * glass transitions. Blends the two edge asymptotes with a smoothstep
     * centred at the interval midpoint.
     */
    public static double[] sigmoidalBaseline(Series s, double t0, double t1, int edgePts) {
        int[] w = s.window(t0, t1);
        if (w.length < 2 * edgePts + 2) throw new EmptyInterval(t0, t1);
        int[] pre = new int[edgePts], post = new int[edgePts];
        for (int i = 0; i < edgePts; i++) {
            pre[i] = w[i];
            post[i] = w[w.length - edgePts + i];
        }
        Line lpre = leastSquares(s, pre);
        Line lpost = leastSquares(s, post);
        double[] out = new double[w.length];
        double ta = s.temp()[w[0]], tb = s.temp()[w[w.length - 1]];
        for (int k = 0; k < w.length; k++) {
            double t = s.temp()[w[k]];
            double x = (t - ta) / (tb - ta);
            double blend = x * x * (3 - 2 * x); // smoothstep 0..1
            out[k] = lpre.at(t) * (1 - blend) + lpost.at(t) * blend;
        }
        return out;
    }

    /** Trapezoidal integral of (hf - baselineAt(t)) over [t0,t1]; non-uniform safe. */
    public static double integrate(Series s, BaselineFn base, double t0, double t1) {
        int[] w = s.window(t0, t1);
        if (w.length < 2) throw new EmptyInterval(t0, t1);
        double area = 0;
        for (int k = 1; k < w.length; k++) {
            int i0 = w[k - 1], i1 = w[k];
            double d0 = s.hf()[i0] - base.at(s.temp()[i0]);
            double d1 = s.hf()[i1] - base.at(s.temp()[i1]);
            area += 0.5 * (d0 + d1) * (s.temp()[i1] - s.temp()[i0]);
        }
        return area;
    }

    @FunctionalInterface
    public interface BaselineFn { double at(double t); }

    /** Index of the extremum of (hf - baseline) inside [t0,t1]; sign = +1 max, -1 min. */
    public static Peak peak(Series s, BaselineFn base, double t0, double t1, int sign) {
        int[] w = s.window(t0, t1);
        if (w.length < 1) throw new EmptyInterval(t0, t1);
        int best = w[0];
        double bestDev = sign * (s.hf()[best] - base.at(s.temp()[best]));
        for (int i : w) {
            double dev = sign * (s.hf()[i] - base.at(s.temp()[i]));
            if (dev > bestDev) { bestDev = dev; best = i; }
        }
        return new Peak(s.temp()[best], s.hf()[best], bestDev * sign);
    }

    public record Peak(double temp, double hf, double deviation) {}

    /**
     * Onset: intersection of the baseline with the tangent at the steepest
     * point of the leading edge inside [t0, t1].
     */
    public static double onset(Series s, BaselineFn base, double t0, double t1, int sign) {
        int[] w = s.window(t0, t1);
        if (w.length < 3) throw new EmptyInterval(t0, t1);
        int pk = indexOfPeak(s, peak(s, base, t0, t1, sign).temp());
        // steepest slope between window start and peak
        int best = w[0];
        double bestSlope = -Double.MAX_VALUE;
        for (int k = w[0]; k < pk; k++) {
            double slope = sign * (s.hf()[k + 1] - s.hf()[k]) / (s.temp()[k + 1] - s.temp()[k]);
            if (slope > bestSlope) { bestSlope = slope; best = k; }
        }
        double slope = (s.hf()[best + 1] - s.hf()[best]) / (s.temp()[best + 1] - s.temp()[best]);
        double intercept = s.hf()[best] - slope * s.temp()[best];
        // intersection with baseline approximated by secant of baseline at edges
        double ta = s.temp()[w[0]], tb = s.temp()[w[w.length - 1]];
        double ba = base.at(ta), bb = base.at(tb);
        double bSlope = (bb - ba) / (tb - ta);
        double bInt = ba - bSlope * ta;
        if (Math.abs(slope - bSlope) < 1e-12) return ta;
        return (bInt - intercept) / (slope - bSlope);
    }

    /** Endset: mirror of onset on the trailing edge. */
    public static double endset(Series s, BaselineFn base, double t0, double t1, int sign) {
        int[] w = s.window(t0, t1);
        if (w.length < 3) throw new EmptyInterval(t0, t1);
        int pk = indexOfPeak(s, peak(s, base, t0, t1, sign).temp());
        int best = pk;
        double bestSlope = -Double.MAX_VALUE;
        for (int k = pk; k < w[w.length - 1]; k++) {
            double slope = -sign * (s.hf()[k + 1] - s.hf()[k]) / (s.temp()[k + 1] - s.temp()[k]);
            if (slope > bestSlope) { bestSlope = slope; best = k; }
        }
        double slope = (s.hf()[best + 1] - s.hf()[best]) / (s.temp()[best + 1] - s.temp()[best]);
        double intercept = s.hf()[best] - slope * s.temp()[best];
        double ta = s.temp()[w[0]], tb = s.temp()[w[w.length - 1]];
        double ba = base.at(ta), bb = base.at(tb);
        double bSlope = (bb - ba) / (tb - ta);
        double bInt = ba - bSlope * ta;
        if (Math.abs(slope - bSlope) < 1e-12) return tb;
        return (bInt - intercept) / (slope - bSlope);
    }

    private static int indexOfPeak(Series s, double temp) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < s.size(); i++) {
            double d = Math.abs(s.temp()[i] - temp);
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    /**
     * Split point between two overlapping transitions: the local minimum of
     * |hf - baseline| between the two peak temperatures.
     */
    public static double overlapSplit(Series s, BaselineFn base, double peakA, double peakB) {
        double lo = Math.min(peakA, peakB), hi = Math.max(peakA, peakB);
        int[] w = s.window(lo, hi);
        if (w.length < 3) throw new EmptyInterval(lo, hi);
        int best = w[0];
        double bestAbs = Double.MAX_VALUE;
        for (int i : w) {
            double d = Math.abs(s.hf()[i] - base.at(s.temp()[i]));
            if (d < bestAbs) { bestAbs = d; best = i; }
        }
        return s.temp()[best];
    }

    /** RMSE of (hf - baseline) over edge windows: baseline fit error source. */
    public static double edgeRmse(Series s, BaselineFn base, double t0, double t1, int edgePts) {
        int[] w = s.window(t0, t1);
        if (w.length < 2 * edgePts + 1) return Double.NaN;
        double sum = 0;
        int n = 0;
        for (int k = 0; k < edgePts; k++) {
            int i0 = w[k], i1 = w[w.length - 1 - k];
            double d0 = s.hf()[i0] - base.at(s.temp()[i0]);
            double d1 = s.hf()[i1] - base.at(s.temp()[i1]);
            sum += d0 * d0 + d1 * d1;
            n += 2;
        }
        return Math.sqrt(sum / n);
    }

    public static final class EmptyInterval extends RuntimeException {
        public final double t0, t1;
        public EmptyInterval(double t0, double t1) {
            super("empty interval [" + t0 + ", " + t1 + "]");
            this.t0 = t0;
            this.t1 = t1;
        }
    }
}
