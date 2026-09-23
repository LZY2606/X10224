package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Baseline candidates for one thermal event. Several plausible baselines
 * must coexist; choosing one never deletes the others. Every candidate
 * carries a stable identity so exports and re-imports keep referential
 * integrity.
 */
public final class Baseline {

    public enum Kind { LINEAR, TANGENT, SIGMOIDAL }

    public final String id;          // stable identity, e.g. "bl-<eventId>-LINEAR"
    public final Kind kind;
    public final double tStart;      // interval bounds in temperature coordinate (degC)
    public final double tEnd;
    private final double[] params;   // kind-specific

    public Baseline(String id, Kind kind, double tStart, double tEnd, double[] params) {
        this.id = id; this.kind = kind; this.tStart = tStart; this.tEnd = tEnd; this.params = params;
    }

    public double eval(double temp) {
        switch (kind) {
            case LINEAR: {
                double y0 = params[0], y1 = params[1];
                double f = (temp - tStart) / (tEnd - tStart);
                return y0 + f * (y1 - y0);
            }
            case TANGENT: {
                // two half-tangents blended: left tangent dominates near tStart
                double m0 = params[0], b0 = params[1], m1 = params[2], b1 = params[3];
                double f = smoothstep((temp - tStart) / (tEnd - tStart));
                return (1 - f) * (m0 * temp + b0) + f * (m1 * temp + b1);
            }
            case SIGMOIDAL: {
                double y0 = params[0], y1 = params[1], mid = params[2], width = params[3];
                return y0 + (y1 - y0) / (1.0 + Math.exp(-(temp - mid) / width));
            }
            default: throw new IllegalStateException();
        }
    }

    private static double smoothstep(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** Build all plausible candidates for the points inside [tStart, tEnd]. */
    public static List<Baseline> candidates(String eventId, List<Curve.Pt> pts, double tStart, double tEnd) {
        List<Baseline> out = new ArrayList<>();
        List<Curve.Pt> in = new ArrayList<>();
        for (Curve.Pt p : pts) if (p.tempC >= tStart && p.tempC <= tEnd) in.add(p);
        if (in.size() < 4) return out; // empty/degenerate interval: no baseline fabricated
        double y0 = in.get(0).heatFlow, y1 = in.get(in.size() - 1).heatFlow;
        out.add(new Baseline("bl-" + eventId + "-LINEAR", Kind.LINEAR, tStart, tEnd, new double[]{y0, y1}));

        // endpoint tangents from local linear fits over the first/last fifth
        int k = Math.max(2, in.size() / 5);
        double[] left = fit(in.subList(0, k));
        double[] right = fit(in.subList(in.size() - k, in.size()));
        out.add(new Baseline("bl-" + eventId + "-TANGENT", Kind.TANGENT, tStart, tEnd,
                new double[]{left[0], left[1], right[0], right[1]}));

        double mid = (tStart + tEnd) / 2.0;
        double width = Math.max(0.5, (tEnd - tStart) / 12.0);
        out.add(new Baseline("bl-" + eventId + "-SIGMOIDAL", Kind.SIGMOIDAL, tStart, tEnd,
                new double[]{y0, y1, mid, width}));
        return out;
    }

    private static double[] fit(List<Curve.Pt> pts) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = pts.size();
        for (Curve.Pt p : pts) { sx += p.tempC; sy += p.heatFlow; sxx += p.tempC * p.tempC; sxy += p.tempC * p.heatFlow; }
        double denom = n * sxx - sx * sx;
        if (Math.abs(denom) < 1e-12) return new double[]{0, n > 0 ? sy / n : 0};
        double m = (n * sxy - sx * sy) / denom;
        double b = (sy - m * sx) / n;
        return new double[]{m, b};
    }
}
