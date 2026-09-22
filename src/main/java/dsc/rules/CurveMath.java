package dsc.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Numeric helpers for DSC curves in canonical coordinates: temperature [degC],
 * heat flow [W/g] (endotherm-positive), time [s].
 */
public final class CurveMath {

    private CurveMath() {
    }

    /** y = a + b*x least squares fit over the supplied points. */
    public static double[] linearFit(List<double[]> pts) {
        int n = pts.size();
        if (n < 2) {
            throw new IllegalArgumentException("linear fit needs >= 2 points, got " + n);
        }
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (double[] p : pts) {
            sx += p[0];
            sy += p[1];
            sxx += p[0] * p[0];
            sxy += p[0] * p[1];
        }
        double den = n * sxx - sx * sx;
        double b = (n * sxy - sx * sy) / den;
        double a = (sy - b * sx) / n;
        return new double[]{a, b};
    }

    /** Linearly interpolate (or extrapolate, when {@code allowExtrapolate}) y at x. */
    public static double interp(List<double[]> pts, double x, boolean allowExtrapolate) {
        if (pts.isEmpty()) {
            throw new IllegalArgumentException("interpolate on empty series");
        }
        if (pts.size() == 1) {
            return pts.get(0)[1];
        }
        for (int i = 0; i < pts.size() - 1; i++) {
            double[] p0 = pts.get(i);
            double[] p1 = pts.get(i + 1);
            if ((x >= p0[0] && x <= p1[0]) || (x >= p1[0] && x <= p0[0])) {
                double f = (x - p0[0]) / (p1[0] - p0[0]);
                return p0[1] + f * (p1[1] - p0[1]);
            }
        }
        if (!allowExtrapolate) {
            double[] edge = x < pts.get(0)[0] ? pts.get(0) : pts.get(pts.size() - 1);
            return edge[1];
        }
        double[] p0 = x < pts.get(0)[0] ? pts.get(0) : pts.get(pts.size() - 2);
        double[] p1 = x < pts.get(0)[0] ? pts.get(1) : pts.get(pts.size() - 1);
        double f = (x - p0[0]) / (p1[0] - p0[0]);
        return p0[1] + f * (p1[1] - p0[1]);
    }

    /**
     * Points of the measured series that fall inside the inclusive window [t0, t1].
     * The series may be non-monotonic in temperature (switch-backs near heating/cooling
     * cycle boundaries); every sample inside the window is retained.
     */
    public static List<double[]> within(List<double[]> series, double t0, double t1) {
        List<double[]> out = new ArrayList<>();
        for (double[] p : series) {
            if (p[0] >= t0 - 1e-9 && p[0] <= t1 + 1e-9) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Trapezoidal integral along the sample path, ordered by time.
     * Works for equal and non-uniform temperature spacing alike, because integration is
     * over time (the actual independent variable of the measurement).
     * Requires monotonic non-decreasing time.
     */
    public static double integrateByTime(List<double[]> txy) {
        double acc = 0;
        for (int i = 1; i < txy.size(); i++) {
            double[] a = txy.get(i - 1);
            double[] b = txy.get(i);
            double dt = b[0] - a[0];
            if (dt < -1e-9) {
                throw new IllegalArgumentException("time must be non-decreasing");
            }
            if (dt < 0) {
                dt = 0;
            }
            acc += 0.5 * (a[2] + b[2]) * dt;
        }
        return acc;
    }

    /** Index of the extremum (max when findMax) y; ties resolve to the first. */
    public static int extremumIndex(List<double[]> pts, boolean findMax) {
        int idx = -1;
        double best = findMax ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (int i = 0; i < pts.size(); i++) {
            double y = pts.get(i)[1];
            if ((findMax && y > best) || (!findMax && y < best)) {
                best = y;
                idx = i;
            }
        }
        return idx;
    }

    public static double rms(List<double[]> measured, List<double[]> baseline) {
        double acc = 0;
        int n = 0;
        for (double[] p : measured) {
            double b = interp(baseline, p[0], true);
            double d = p[1] - b;
            acc += d * d;
            n++;
        }
        return n == 0 ? 0 : Math.sqrt(acc / n);
    }

    /**
     * Non-uniformity score of temperature spacing: ratio of max to median positive
     * spacing (in the direction of time). Values near 1 are equally spaced grids.
     */
    public static double temperatureSpacingRatio(List<double[]> txy) {
        List<Double> gaps = new ArrayList<>();
        double maxGap = 0;
        for (int i = 1; i < txy.size(); i++) {
            double g = Math.abs(txy.get(i)[1] - txy.get(i - 1)[1]);
            if (g > 1e-9) {
                gaps.add(g);
                maxGap = Math.max(maxGap, g);
            }
        }
        if (gaps.size() < 2) {
            return 1.0;
        }
        gaps.sort(Double::compare);
        double median = gaps.get(gaps.size() / 2);
        return median == 0 ? 1.0 : maxGap / median;
    }
}
