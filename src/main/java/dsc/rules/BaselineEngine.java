package dsc.rules;

import dsc.model.Series;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds multiple plausible baseline candidates for an interval. All
 * candidates are retained side by side; accepting one never deletes the rest.
 */
public final class BaselineEngine {

    public record Candidate(String id, String method, double t0, double t1,
                            double y0, double y1, String note) {
        public double at(double t) {
            if (t1 == t0) return y0;
            return y0 + (y1 - y0) * (t - t0) / (t1 - t0);
        }
    }

    private BaselineEngine() {}

    /** Least-squares line fit over the series restricted to [a,b]. */
    static double[] fitLine(Series s, double a, double b) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = 0;
        for (int i = 0; i < s.size(); i++) {
            double t = s.temp[i];
            if (t < a || t > b) continue;
            sx += t; sy += s.heatFlow[i]; sxx += t * t; sxy += t * s.heatFlow[i];
            n++;
        }
        if (n < 2) return new double[]{Double.NaN, Double.NaN};
        double denom = n * sxx - sx * sx;
        if (denom == 0) return new double[]{sy / n, 0};
        double slope = (n * sxy - sx * sy) / denom;
        double intercept = (sy - slope * sx) / n;
        return new double[]{intercept, slope};
    }

    public static List<Candidate> candidates(Series s, double t0, double t1) {
        List<Candidate> out = new ArrayList<>();
        double y0 = s.at(t0);
        double y1 = s.at(t1);
        if (Double.isNaN(y0) || Double.isNaN(y1)) return out;

        out.add(new Candidate("bc-linear", "linear", t0, t1, y0, y1,
                "straight line between interval endpoints"));

        double span = t1 - t0;
        double[] left = fitLine(s, t0 - 0.15 * span, t0 + 0.05 * span);
        double[] right = fitLine(s, t1 - 0.05 * span, t1 + 0.15 * span);
        if (!Double.isNaN(left[0]) && !Double.isNaN(right[0])) {
            double ly = left[0] + left[1] * t0;
            double ry = right[0] + right[1] * t1;
            out.add(new Candidate("bc-tangent", "tangential", t0, t1, ly, ry,
                    "edge-window tangent fits joined across the interval"));
        }

        double mid = (t0 + t1) / 2;
        double[] leftIn = fitLine(s, t0, mid);
        double[] rightIn = fitLine(s, mid, t1);
        if (!Double.isNaN(leftIn[0]) && !Double.isNaN(rightIn[0])) {
            out.add(new Candidate("bc-split", "split", t0, t1,
                    leftIn[0] + leftIn[1] * t0, rightIn[0] + rightIn[1] * t1,
                    "two half-interval fits, for asymmetric or overlapping transitions"));
        }
        return out;
    }
}
