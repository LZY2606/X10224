package dsc.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces every legitimate baseline candidate for an interval.
 *
 * Ambiguity is preserved: when several methods fit the accepted pre/post
 * plateau comparably well they are all returned, marked feasible, and the
 * caller keeps their differences instead of collapsing to one answer.
 */
public final class BaselineFitter {

    public record Fit(double slope, double intercept, double rmse) {
        double at(double x) { return slope * x + intercept; }
    }

    /** Least-squares line y = a*x + b. Returns null for fewer than 2 points. */
    public static Fit leastSquares(double[] x, double[] y) {
        int n = 0;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = 0; i < x.length; i++) {
            if (Double.isNaN(x[i]) || Double.isNaN(y[i])) continue;
            n++; sx += x[i]; sy += y[i]; sxx += x[i] * x[i]; sxy += x[i] * y[i];
        }
        if (n < 2) return null;
        double den = n * sxx - sx * sx;
        if (den == 0) return null;
        double a = (n * sxy - sx * sy) / den;
        double b = (sy - a * sx) / n;
        double ss = 0;
        for (int i = 0; i < x.length; i++) {
            if (Double.isNaN(x[i]) || Double.isNaN(y[i])) continue;
            double e = y[i] - (a * x[i] + b);
            ss += e * e;
        }
        return new Fit(a, b, Math.sqrt(ss / n));
    }

    public List<BaselineCandidate> candidates(String intervalId,
                                              List<NormalizedPoint> inside,
                                              TangentSelection tangent) {
        List<BaselineCandidate> out = new ArrayList<>();
        double[] t = new double[inside.size()];
        double[] hf = new double[inside.size()];
        int n = 0;
        for (NormalizedPoint p : inside) {
            if (!p.accepted()) continue;
            t[n] = p.tK(); hf[n] = p.hfW(); n++;
        }
        if (n == 0) return out;
        double[] tt = new double[n]; double[] hh = new double[n];
        System.arraycopy(t, 0, tt, 0, n);
        System.arraycopy(hf, 0, hh, 0, n);

        Fit ls = leastSquares(tt, hh);
        if (ls != null) {
            out.add(new BaselineCandidate(
                    Ids.stable("baseline", intervalId, "linear"),
                    intervalId, "linear-regression",
                    ls.slope(), ls.intercept(), ls.rmse(), true,
                    "least-squares line over all accepted points in the interval"));
        }

        // Pre/post plateau anchors: 20% windows at the two ends of the interval.
        Fit ends = anchoredEnds(tt, hh, 0.2);
        if (ends != null && ls != null) {
            boolean feasible = comparable(ends, ls, tt, hh);
            out.add(new BaselineCandidate(
                    Ids.stable("baseline", intervalId, "plateau"),
                    intervalId, "plateau-anchored",
                    ends.slope(), ends.intercept(), ends.rmse(), feasible,
                    "line through the means of the pre-event and post-event plateau windows"));
        }

        if (tangent != null) {
            Qty l = tangent.leftT().to("K");
            Qty r = tangent.rightT().to("K");
            Qty lh = tangent.leftHf().to("W");
            Qty rh = tangent.rightHf().to("W");
            if (!l.isUnconverted() && !r.isUnconverted()
                    && !lh.isUnconverted() && !rh.isUnconverted()
                    && r.value() != l.value()) {
                double a = (rh.value() - lh.value()) / (r.value() - l.value());
                double b = lh.value() - a * l.value();
                double rmse = rmseAgainst(tt, hh, a, b);
                out.add(new BaselineCandidate(
                        Ids.stable("baseline", intervalId, "tangent"),
                        intervalId, "user-tangent",
                        a, b, rmse, true,
                        "user-selected tangent pair " + tangent.id()
                                + " (archived independently of instrument output)"));
            }
        }
        return out;
    }

    private Fit anchoredEnds(double[] t, double[] h, double frac) {
        int n = t.length;
        int w = Math.max(2, (int) Math.round(n * frac));
        double x1 = 0, y1 = 0, x2 = 0, y2 = 0;
        int m = Math.min(w, n / 2);
        if (m < 1) return null;
        for (int i = 0; i < m; i++) { x1 += t[i]; y1 += h[i]; }
        for (int i = n - m; i < n; i++) { x2 += t[i]; y2 += h[i]; }
        x1 /= m; y1 /= m; x2 /= m; y2 /= m;
        if (x2 == x1) return null;
        double a = (y2 - y1) / (x2 - x1);
        double b = y1 - a * x1;
        return new Fit(a, b, rmseAgainst(t, h, a, b));
    }

    private boolean comparable(Fit candidate, Fit ref, double[] t, double[] h) {
        double band = 0;
        double mean = 0;
        for (double v : h) mean += v;
        mean /= Math.max(1, h.length);
        for (double v : h) band += (v - mean) * (v - mean);
        band = Math.sqrt(band / Math.max(1, h.length));
        double diff = 0;
        for (int i = 0; i < t.length; i++) {
            diff = Math.max(diff, Math.abs(candidate.at(t[i]) - ref.at(t[i])));
        }
        return diff <= Math.max(3.0 * ref.rmse(), 0.5 * band);
    }

    private double rmseAgainst(double[] t, double[] h, double a, double b) {
        double ss = 0;
        for (int i = 0; i < t.length; i++) {
            double e = h[i] - (a * t[i] + b);
            ss += e * e;
        }
        return Math.sqrt(ss / t.length);
    }
}
