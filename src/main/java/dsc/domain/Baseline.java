package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * A baseline candidate under a user-selected interval [t0,t1] in °C.
 * Several plausible baselines are kept side by side; selecting one never
 * deletes the others. An interval containing no samples is not dropped:
 * the candidate stays in place with a diagnostic ("empty interval").
 */
public final class Baseline {
    public enum Type { LINEAR, SIGMOIDAL, TANGENT }

    public final String id;        // stable identity: "<segmentId>/<type>@<t0>-<t1>"
    public final Type type;
    public final String segmentId;
    public final double t0, t1;    // interval bounds, °C (coordinate basis: calibrated sample temperature)
    public final boolean valid;
    public final String diagnostic;

    // LINEAR/TANGENT: y = c0 + c1*T.  SIGMOIDAL: y = c0 + c1*T + h/(1+exp(-(T-tm)/w))
    public final double c0, c1, h, tm, w;

    private Baseline(String id, Type type, String segmentId, double t0, double t1,
                     boolean valid, String diagnostic, double c0, double c1, double h, double tm, double w) {
        this.id = id;
        this.type = type;
        this.segmentId = segmentId;
        this.t0 = t0;
        this.t1 = t1;
        this.valid = valid;
        this.diagnostic = diagnostic;
        this.c0 = c0; this.c1 = c1; this.h = h; this.tm = tm; this.w = w;
    }

    public double eval(double tempC) {
        if (type == Type.SIGMOIDAL) {
            return c0 + c1 * tempC + h / (1.0 + Math.exp(-(tempC - tm) / w));
        }
        return c0 + c1 * tempC;
    }

    public static Baseline invalid(String segmentId, Type type, double t0, double t1, String diagnostic) {
        return new Baseline(segmentId + "/" + type + "@" + r(t0) + "-" + r(t1),
                type, segmentId, t0, t1, false, diagnostic, 0, 0, 0, 0, 1);
    }

    /** Build all plausible candidates for an interval inside one segment. */
    public static List<Baseline> candidates(NormalizedCurve nc, Segment seg, double t0, double t1) {
        List<Baseline> out = new ArrayList<>();
        double lo = Math.min(t0, t1), hi = Math.max(t0, t1);
        List<Integer> idx = new ArrayList<>();
        for (int i = seg.fromPoint; i < seg.toPoint; i++) {
            if (nc.tempC[i] >= lo && nc.tempC[i] <= hi) idx.add(i);
        }
        if (idx.isEmpty()) {
            String d = "empty interval [" + r(lo) + "," + r(hi) + "] °C: no samples inside segment "
                    + seg.id + "; candidate retained but excluded from computation";
            out.add(invalid(seg.id, Type.LINEAR, lo, hi, d));
            return out;
        }
        // endpoints for anchoring: use first/last samples inside the interval
        int i0 = idx.get(0), i1 = idx.get(idx.size() - 1);
        double x0 = nc.tempC[i0], y0 = nc.heatFlowWg[i0];
        double x1 = nc.tempC[i1], y1 = nc.heatFlowWg[i1];
        double slope = (x1 - x0) != 0 ? (y1 - y0) / (x1 - x0) : 0.0;
        double intercept = y0 - slope * x0;
        out.add(new Baseline(seg.id + "/LINEAR@" + r(lo) + "-" + r(hi), Type.LINEAR, seg.id, lo, hi,
                true, null, intercept, slope, 0, 0, 1));

        // sigmoidal candidate (glass-transition style step): fit height & midpoint from data
        double hStep = y1 - y0;
        double tm = (x0 + x1) / 2.0;
        double w = Math.max((x1 - x0) / 8.0, 0.5);
        out.add(new Baseline(seg.id + "/SIGMOIDAL@" + r(lo) + "-" + r(hi), Type.SIGMOIDAL, seg.id, lo, hi,
                true, null, intercept, slope, hStep, tm, w));
        return out;
    }

    static String r(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
