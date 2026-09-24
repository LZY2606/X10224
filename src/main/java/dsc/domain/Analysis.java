package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Transition analysis: onset / peak / endset by tangent intersection, and
 * enthalpy by trapezoid integration over actual sample times — non-uniform
 * temperature spacing is handled exactly, never assumed uniform.
 */
public final class Analysis {

    public static final class ErrorSource {
        public final String name;
        public final double contributionJg;
        public ErrorSource(String name, double contributionJg) {
            this.name = name;
            this.contributionJg = contributionJg;
        }
    }

    public static final class Result {
        public String id;                 // stable identity of this derived result
        public String segmentId;
        public String baselineId;
        public String kind;               // "glass_transition" | "crystallization" | "melting" | "undecided"
        public Double onsetC, peakC, endsetC;
        public Double enthalpyJg;         // null for pure step (glass transition) events
        public boolean endothermic;       // from canonical endo-up basis, never from file names
        public final List<ErrorSource> errorSources = new ArrayList<>();
        public final List<String> diagnostics = new ArrayList<>();
    }

    /** Integrate one transition inside [t0,t1] of one segment against one baseline. */
    public static Result integrate(NormalizedCurve nc, Segment seg, Baseline baseline, String kindHint) {
        Result r = new Result();
        r.segmentId = seg.id;
        r.baselineId = baseline.id;
        r.id = "int:" + baseline.id;
        r.kind = kindHint;
        if (!baseline.valid) {
            r.diagnostics.add("baseline invalid: " + baseline.diagnostic);
            return r;
        }
        double lo = Math.min(baseline.t0, baseline.t1), hi = Math.max(baseline.t0, baseline.t1);
        List<Integer> idx = new ArrayList<>();
        for (int i = seg.fromPoint; i < seg.toPoint; i++) {
            if (nc.tempC[i] >= lo && nc.tempC[i] <= hi) idx.add(i);
        }
        if (idx.size() < 3) {
            r.diagnostics.add("interval holds fewer than 3 samples; no transition computed");
            return r;
        }

        // peak = extremum of (signal - baseline) inside interval
        int pk = idx.get(0);
        double best = 0;
        for (int i : idx) {
            double d = nc.heatFlowWg[i] - baseline.eval(nc.tempC[i]);
            if (Math.abs(d) > Math.abs(best)) { best = d; pk = i; }
        }
        r.peakC = nc.tempC[pk];
        r.endothermic = best > 0; // canonical endo-up basis

        // onset/endset: tangent at steepest flanks intersected with baseline
        r.onsetC = tangentIntersection(nc, baseline, idx, true);
        r.endsetC = tangentIntersection(nc, baseline, idx, false);

        // enthalpy: trapezoid over real time axis (handles non-uniform spacing)
        double area = 0;
        for (int k = 1; k < idx.size(); k++) {
            int a = idx.get(k - 1), b = idx.get(k);
            double da = nc.heatFlowWg[a] - baseline.eval(nc.tempC[a]);
            double db = nc.heatFlowWg[b] - baseline.eval(nc.tempC[b]);
            area += 0.5 * (da + db) * (nc.timeS[b] - nc.timeS[a]); // W/g * s = J/g
        }
        r.enthalpyJg = area;

        // error sources, read back to the reviewer with contributions
        r.errorSources.add(new ErrorSource("baseline_choice_spread", 0)); // filled by caller comparing candidates
        r.errorSources.add(new ErrorSource("mass_uncertainty", Math.abs(area) * 0.005));
        r.errorSources.add(new ErrorSource("temperature_calibration", Math.abs(area) * 0.002));
        return r;
    }

    /** Steepest-flank tangent intersection with baseline; scans first/last third of the interval. */
    private static Double tangentIntersection(NormalizedCurve nc, Baseline bl, List<Integer> idx, boolean left) {
        int n = idx.size();
        int from = left ? 0 : n / 2;
        int to = left ? n / 2 : n - 1;
        double bestSlope = 0;
        int bestAt = -1;
        for (int k = Math.max(from, 1); k <= to; k++) {
            int a = idx.get(k - 1), b = idx.get(k);
            double dt = nc.tempC[b] - nc.tempC[a];
            if (dt == 0) continue;
            double s = (nc.heatFlowWg[b] - nc.heatFlowWg[a]) / dt;
            if (bestAt < 0 || Math.abs(s) > Math.abs(bestSlope)) { bestSlope = s; bestAt = b; }
        }
        if (bestAt < 0) return null;
        // tangent line: y - y1 = m (T - T1); baseline: y = c0 + c1 T  (linear part only)
        double m = bestSlope, T1 = nc.tempC[bestAt], y1 = nc.heatFlowWg[bestAt];
        double denom = (m - bl.c1);
        if (Math.abs(denom) < 1e-12) return null;
        return (bl.c0 - y1 + m * T1) / denom;
    }

    /** Fill the baseline-choice error source by comparing two candidate results. */
    public static void attributeBaselineSpread(Result a, Result b) {
        if (a.enthalpyJg == null || b.enthalpyJg == null) return;
        double spread = Math.abs(a.enthalpyJg - b.enthalpyJg) / 2.0;
        a.errorSources.set(0, new ErrorSource("baseline_choice_spread", spread));
        b.errorSources.set(0, new ErrorSource("baseline_choice_spread", spread));
    }
}
