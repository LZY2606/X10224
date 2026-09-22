package dsc.calc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dsc.model.J;
import dsc.model.Quantity;

import java.util.ArrayList;
import java.util.List;

/**
 * Event detection (glass transition, crystallization, melting) on one single-direction
 * segment, plus pointwise (non-uniform-spacing-safe) integration and error-source budget.
 */
public final class Peaks {

    private Peaks() {}

    public static final class EventResult {
        public final String kind;
        public final double onsetC, peakC, endsetC;
        public final double areaMJ, enthalpyJPerG;
        public final JsonObject json;
        public final List<String> diagnostics;

        EventResult(String kind, double onsetC, double peakC, double endsetC,
                    double areaMJ, double enthalpyJPerG, JsonObject json, List<String> diagnostics) {
            this.kind = kind;
            this.onsetC = onsetC;
            this.peakC = peakC;
            this.endsetC = endsetC;
            this.areaMJ = areaMJ;
            this.enthalpyJPerG = enthalpyJPerG;
            this.json = json;
            this.diagnostics = diagnostics;
        }
    }

    /**
     * Analyze a region against one baseline candidate.
     *
     * @param direction      "heating" / "cooling" (never mixed)
     * @param signConvention +1 exo-up, -1 exo-down
     * @param rateKPerMin    declared scan rate used for time normalization
     * @param massMg         CURRENT mass revision (NaN if no usable mass)
     */
    public static List<EventResult> detect(Series s, int from, int to,
                                           Baselines.Candidate baseline,
                                           String direction, int signConvention,
                                           double rateKPerMin, double massMg,
                                           List<String> globalDiags) {
        List<EventResult> out = new ArrayList<>();
        int n = to - from + 1;
        if (n < 3) {
            globalDiags.add("EMPTY INTERVAL: region [" + from + "," + to + "] has fewer than 3 points; no event computed");
            return out;
        }
        if (!Segmentation.isSingleDirection(s, from, to)) {
            globalDiags.add("SEGMENT SWITCH inside interval [" + from + "," + to
                    + "]: heating and cooling points must not share a baseline");
            return out;
        }

        double[] r = new double[n];
        for (int i = 0; i < n; i++) r[i] = s.hf[from + i] - baseline.values[i];

        // Peak hunting on the residual, with minimal prominence and separation.
        double noise = robustNoise(r);
        double prom = Math.max(3.0 * noise, 0.05 * (maxAbs(r) + 1e-12));
        List<int[]> extrema = new ArrayList<>();
        for (int i = 1; i < n - 1; i++) {
            boolean max = r[i] > r[i - 1] && r[i] >= r[i + 1] && r[i] > prom;
            boolean min = r[i] < r[i - 1] && r[i] <= r[i + 1] && r[i] < -prom;
            if (max) extrema.add(new int[]{i, +1});
            if (min) extrema.add(new int[]{i, -1});
        }
        mergeCloseExtrema(extrema, r, Math.max(3, n / 200));

        if (extrema.isEmpty()) {
            // A glass transition is a step (no sharp peak): detect by level shift.
            EventResult tg = detectStep(s, r, from, to, baseline, direction, signConvention,
                    rateKPerMin, massMg, globalDiags, noise);
            if (tg != null) out.add(tg);
            else globalDiags.add("no event above noise in interval [" + from + "," + to
                    + "] (robust noise " + fmt(noise) + " mW)");
            return out;
        }
        if (extrema.size() > 1) {
            globalDiags.add("OVERLAPPING TURNS: " + extrema.size()
                    + " extrema in one interval; all legal candidates retained, none merged away");
        }

        double[] boundary = new double[n + 1];
        for (int e = 0; e < extrema.size(); e++) {
            int lo, hi;
            if (e == 0) lo = 0;
            else {
                int a = extrema.get(e - 1)[0], b = extrema.get(e)[0];
                lo = a + deepestValleyBetween(r, a, b);
            }
            if (e == extrema.size() - 1) hi = n - 1;
            else {
                int a = extrema.get(e)[0], b = extrema.get(e + 1)[0];
                hi = a + deepestValleyBetween(r, a, b);
            }
            int peak = extrema.get(e)[0];
            int pol = extrema.get(e)[1];
            out.add(buildEvent(s, r, from, to, baseline, direction, signConvention,
                    rateKPerMin, massMg, globalDiags, noise, lo, peak, hi, pol));
        }
        return out;
    }

    private static EventResult buildEvent(Series s, double[] r, int from, int to,
                                          Baselines.Candidate baseline, String direction,
                                          int signConvention, double rateKPerMin, double massMg,
                                          List<String> globalDiags, double noise,
                                          int lo, int peak, int hi, int polarity) {
        List<String> local = new ArrayList<>();
        // Onset/endset by tangents at, respectively, steepest flank point and peak half-rise.
        int onsetIdx = steepestIndex(r, lo, peak);
        int endsetIdx = steepestIndex(r, peak, hi);
        double onsetT = tangentIntersectionX(s, r, from, lo, onsetIdx);
        double endsetT = tangentIntersectionX(s, r, from, hi, endsetIdx);
        double peakT = s.t[from + peak];

        boolean endoPeak = polarity * signConvention < 0;
        String kind;
        if (direction.equals(Segmentation.HEATING)) {
            kind = endoPeak ? "melting" : "cold-crystallization";
        } else {
            kind = endoPeak ? "anomalous-endotherm-on-cooling" : "crystallization";
            if (!endoPeak) local.add("cooling exotherm consistent with crystallization");
            else local.add("endotherm on cooling is unusual; sign taken from measured signal+convention, not file name");
        }

        double area = areaMJ(s, r, from + lo, from + hi, rateKPerMin);
        double enthalpy = Double.NaN;
        if (!Double.isNaN(massMg) && massMg > 0) {
            enthalpy = area / massMg; // mJ / mg == J/g
        } else {
            local.add("mass revision unusable; specific enthalpy left undetermined, area in mJ retained");
        }
        double rate = rateKPerMin;
        if (!(rate > 0)) local.add("scan rate missing; temperature-basis area only");

        JsonObject o = J.obj();
        J.put(o, "kind", kind);
        J.put(o, "direction", direction);
        J.put(o, "endothermic", endoPeak);
        putTemp(o, "onset", onsetT);
        putTemp(o, "peak", peakT);
        putTemp(o, "endset", endsetT);
        J.put(o, "areaMJ", area);
        if (Double.isNaN(enthalpy)) J.put(o, "enthalpyJPerG", (String) null);
        else J.put(o, "enthalpyJPerG", enthalpy);
        J.put(o, "baselineMethod", baseline.method);
        J.put(o, "region", regionJson(from, to, from + lo, from + peak, from + hi));
        J.put(o, "nonUniformSpacing", s.nonUniformSpacing);
        JsonArray errs = errorBudget(baseline, noise, massMg, rate, s);
        J.put(o, "errorSources", errs);
        for (String d : local) globalDiags.add(d);
        return new EventResult(kind, onsetT, peakT, endsetT, area, enthalpy, o, local);
    }

    private static EventResult detectStep(Series s, double[] r, int from, int to,
                                          Baselines.Candidate baseline, String direction,
                                          int signConvention, double rateKPerMin, double massMg,
                                          List<String> globalDiags, double noise) {
        int n = r.length;
        if (n < 10) return null;
        int q1 = n / 4, q3 = (3 * n) / 4;
        double meanLow = mean(r, 0, q1);
        double meanHigh = mean(r, q3, n);
        double step = meanHigh - meanLow;
        if (Math.abs(step) < Math.max(5.0 * noise, 1e-9)) return null;

        int mid = -1;
        double bestHalf = Double.POSITIVE_INFINITY;
        for (int i = q1; i <= q3; i++) {
            double a = mean(r, 0, i), b = mean(r, i, n);
            double score = scatter(r, 0, i, a) + scatter(r, i, n, b);
            if (score < bestHalf) { bestHalf = score; mid = i; }
        }
        if (mid <= 0) return null;
        double halfY = (meanLow + meanHigh) / 2.0;
        int half = mid;
        for (int i = 1; i < n - 1; i++) {
            if ((r[i - 1] - halfY) * (r[i + 1] - halfY) <= 0) { half = i; break; }
        }
        double onsetT = s.t[from + Math.max(0, mid - n / 10)];
        double endsetT = s.t[from + Math.min(n - 1, mid + n / 10)];
        JsonObject o = J.obj();
        J.put(o, "kind", "glass-transition");
        J.put(o, "direction", direction);
        J.put(o, "endothermic", step * signConvention < 0);
        putTemp(o, "onset", onsetT);
        putTemp(o, "peak", s.t[from + half]);
        putTemp(o, "endset", endsetT);
        J.put(o, "stepMw", step);
        J.put(o, "tgMidC", s.t[from + half]);
        J.put(o, "areaMJ", (String) null);
        J.put(o, "enthalpyJPerG", (String) null);
        J.put(o, "baselineMethod", baseline.method);
        J.put(o, "region", regionJson(from, to, from + Math.max(0, mid - n / 10),
                from + half, from + Math.min(n - 1, mid + n / 10)));
        J.put(o, "nonUniformSpacing", s.nonUniformSpacing);
        J.put(o, "errorSources", errorBudget(baseline, noise, massMg, rateKPerMin, s));
        globalDiags.add("glass-transition reported as step change (no enthalpy of integration)");
        return new EventResult("glass-transition", onsetT, s.t[from + half], endsetT,
                Double.NaN, Double.NaN, o, new ArrayList<>());
    }

    /**
     * Heat-flow integration: A = integral HF dt = (1/|beta|) * integral HF dT.
     * Input r is mW, dT in degC (== K), beta K/min -> result mJ (mW*s = mJ, with /60*60 cancel):
     * mJ = mW * min * 60; integral(mW)*dT/(K/min) gives mW*min; x60 -> mJ.
     * This variant performs the correct normalization used by buildEvent.
     */
    public static double areaMJ(Series s, double[] r, int i0, int i1, double rateKPerMin) {
        if (i1 <= i0 || !(rateKPerMin > 0)) {
            double raw = 0;
            for (int i = Math.max(1, i0); i <= i1; i++) raw += 0.5 * (r[i - 1 - i0] + r[i - i0]) * Math.abs(s.t[i] - s.t[i - 1]);
            return Double.NaN;
        }
        double sum = 0;
        for (int i = i0 + 1; i <= i1; i++) {
            double dT = Math.abs(s.t[i] - s.t[i - 1]);
            sum += 0.5 * (r[i - 1 - i0] + r[i - i0]) * dT;
        }
        return sum / rateKPerMin * 60.0; // mW*min*60 s/min = mJ
    }

    private static JsonArray errorBudget(Baselines.Candidate baseline, double noise,
                                         double massMg, double rate, Series s) {
        JsonArray arr = J.arr();
        arr.add(err("baseline-rms", baseline.rmsResidual, "mW",
                "residual scatter of '" + baseline.method + "' against flank anchors"));
        arr.add(err("signal-noise", noise, "mW", "robust (MAD) residual noise in interval"));
        arr.add(err("temperature-spacing", s.maxStepC - s.minStepC, "degC",
                s.nonUniformSpacing ? "non-uniform grid; pointwise trapezoid, no resampling"
                        : "near-uniform grid"));
        if (Double.isNaN(massMg) || massMg <= 0) {
            JsonObject e = err("mass", Double.NaN, "mg", "no usable mass revision; enthalpy undetermined");
            arr.add(e);
        } else {
            arr.add(err("mass-normalization", 0.0, "mg",
                    "specific enthalpy scales inversely with mass revision " + fmt(massMg) + " mg; mass edits expire all normalizations"));
        }
        if (!(rate > 0)) arr.add(err("scan-rate", Double.NaN, "K/min", "rate missing; time normalization unavailable"));
        return arr;
    }

    private static JsonObject err(String id, double value, String unit, String explanation) {
        JsonObject o = J.obj();
        J.put(o, "source", id);
        if (Double.isNaN(value)) J.put(o, "value", (String) null);
        else J.put(o, "value", value);
        J.put(o, "unit", unit);
        J.put(o, "explanation", explanation);
        return o;
    }

    private static int steepestIndex(double[] r, int a, int b) {
        int step = a <= b ? 1 : -1;
        int best = a;
        double bestV = 0;
        for (int i = a + step; i != b; i += step) {
            double v = Math.abs(r[i] - r[i - step]);
            if (v > bestV) { bestV = v; best = i; }
        }
        return best;
    }

    private static double tangentIntersectionX(Series s, double[] r, int absFrom, int edge, int flank) {
        // Tangent at flank point intersects the local edge baseline level.
        double dy = r[flank] - r[Math.max(0, flank - 1)];
        double dx = s.t[absFrom + flank] - s.t[absFrom + Math.max(0, flank - 1)];
        if (Math.abs(dx) < 1e-12) return s.t[absFrom + edge];
        double m = dy / dx;
        double x = s.t[absFrom + flank] - r[flank] / m;
        return x;
    }

    private static int deepestValleyBetween(double[] r, int a, int b) {
        if (b < a) return deepestValleyBetween(r, b, a);
        int idx = a;
        double best = Double.POSITIVE_INFINITY;
        for (int i = a + 1; i < b; i++) if (r[i] < best) { best = r[i]; idx = i; }
        return idx - a;
    }

    private static void mergeCloseExtrema(List<int[]> extrema, double[] r, int minDist) {
        boolean changed = true;
        while (changed && extrema.size() > 1) {
            changed = false;
            for (int i = 1; i < extrema.size(); i++) {
                int[] p = extrema.get(i - 1), q = extrema.get(i);
                if (p[1] == q[1] && q[0] - p[0] < minDist) {
                    int keep = Math.abs(r[p[0]]) >= Math.abs(r[q[0]]) ? i - 1 : i;
                    extrema.remove(keep);
                    changed = true;
                    break;
                }
            }
        }
    }

    private static double robustNoise(double[] r) {
        double[] d = new double[r.length - 1];
        for (int i = 1; i < r.length; i++) d[i - 1] = r[i] - r[i - 1];
        java.util.Arrays.sort(d);
        double mad = Math.abs(d[d.length / 2]);
        return mad * 1.4826 / Math.sqrt(2);
    }

    private static double maxAbs(double[] r) {
        double m = 0;
        for (double v : r) m = Math.max(m, Math.abs(v));
        return m;
    }

    private static double mean(double[] r, int a, int b) {
        double s = 0;
        for (int i = a; i < b; i++) s += r[i];
        return b > a ? s / (b - a) : 0;
    }

    private static double scatter(double[] r, int a, int b, double c) {
        double s = 0;
        for (int i = a; i < b; i++) s += (r[i] - c) * (r[i] - c);
        return s;
    }

    private static JsonObject regionJson(int from, int to, int lo, int peak, int hi) {
        JsonObject o = J.obj();
        J.put(o, "fromIndex", from);
        J.put(o, "toIndex", to);
        J.put(o, "onsetIndex", lo);
        J.put(o, "peakIndex", peak);
        J.put(o, "endsetIndex", hi);
        return o;
    }

    private static void putTemp(JsonObject o, String key, double c) {
        JsonObject q = J.obj();
        J.put(q, "value", c);
        J.put(q, "unit", "degC");
        J.put(q, "coordinateBasis", "calibrated sample temperature");
        J.put(o, key, q);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.4g", v);
    }
}
