package dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Baseline + transition analysis. Everything here is deterministic and side-effect free.
 * The engine never decides which candidate is "the answer": callers keep all legal candidates.
 */
public final class BaselineEngine {
    private BaselineEngine() {}

    public static class Input {
        public String segmentId;
        public Enums.Direction direction;
        public Enums.SignConvention signConvention;
        public double rateCperMin;          // signed metadata rate (heating>0, cooling<0), NaN if unknown
        public double t0C, t1C;
        public double[] tC, hfWg;           // normalized frame
        public double anchorFrac = 0.12;    // anchor region fraction of interval width
        public RuleSet rules;
        public boolean crossesSegments;     // interval boundary crosses a heat/cool segment
    }

    public static List<BaselineResult> allCandidates(Input in) {
        List<BaselineResult> out = new ArrayList<>();
        out.add(analyze("linear", in));
        out.add(analyze("tangent-step", in));
        return out;
    }

    public static BaselineResult analyze(String method, Input in) {
        BaselineResult r = new BaselineResult();
        r.method = method;
        if (in.crossesSegments) {
            r.crossesSegments = true;
            r.fired.add(Enums.PolicyRule.HEATING_COOLING_NOT_MIXED);
            r.fired.add(Enums.PolicyRule.NO_MIXED_SEGMENT_FOR_INTERVAL);
            return r;
        }
        int[] w = Calc.indexWindow(in.tC, in.t0C, in.t1C);
        int lo = w[0], hi = w[1];
        if (hi - lo < 4 || in.t1C <= in.t0C) {
            r.empty = true;
            r.fired.add(Enums.PolicyRule.EMPTY_INTERVAL);
            return r;
        }
        double[] t = slice(in.tC, lo, hi);
        double[] hf = slice(in.hfWg, lo, hi);

        if (t[t.length - 1] < t[0]) r.reversed = true;
        if (!Calc.isUniform(t, 0.25)) r.nonUniform = true;
        if (r.reversed) r.fired.add(Enums.PolicyRule.REVERSED_HEATFLOW_SUSPECT);
        if (r.nonUniform) r.fired.add(Enums.PolicyRule.NONUNIFORM_TEMPERATURE);

        double win = in.rules.smoothingWindowC;
        double[] sm = Calc.smooth(t, hf, win);
        double[] d = Calc.derivative(t, sm);

        int n = t.length;
        int aw = Math.max(2, (int) (n * in.anchorFrac));
        double[] pre, post;
        if ("tangent-step".equals(method)) {
            // wider pre/post anchors; step tangents computed at the inflection flanks
            pre = Calc.linearFit(t, sm, 0, Math.min(aw + 2, n - 1));
            post = Calc.linearFit(t, sm, Math.max(0, n - 1 - aw - 2), n - 1);
        } else {
            pre = Calc.linearFit(t, sm, 0, aw);
            post = Calc.linearFit(t, sm, n - 1 - aw, n - 1);
        }
        // single straight baseline: average the two anchor lines (both candidates retained)
        r.a = (pre[0] + post[0]) / 2.0;
        r.b = (pre[1] + post[1]) / 2.0;

        double[] corr = new double[n];
        for (int i = 0; i < n; i++) corr[i] = sm[i] - (r.a + r.b * t[i]);

        classifyAndMeasure(method, t, sm, corr, d, pre, post, in, r);
        return r;
    }

    private static void classifyAndMeasure(String method, double[] t, double[] sm, double[] corr,
                                           double[] d, double[] pre, double[] post,
                                           Input in, BaselineResult r) {
        int n = t.length;
        int maxI = Calc.argMax(corr, 0, n - 1);
        int minI = Calc.argMin(corr, 0, n - 1);
        double up = corr[maxI], down = -corr[minI];
        double prom = in.rules.peakProminenceWg;

        List<Integer> maxima = Calc.localExtrema(corr, prom, true);
        List<Integer> minima = Calc.localExtrema(corr, prom, false);
        if (maxima.size() >= 2) r.overlapping = true;
        if (minima.size() >= 2) r.overlapping = true;

        boolean peakUp = up >= down && up > prom;
        boolean peakDown = down > up && down > prom;
        boolean step = !peakUp && !peakDown && Math.abs(meanTail(corr, true) - meanTail(corr, false))
                >= in.rules.glassStepMinWg;

        if (step) {
            r.isGlass = true;
            glassStep(method, t, sm, corr, d, pre, post, in, r);
        } else if (peakUp || peakDown) {
            int pk = peakUp ? maxI : minI;
            r.peakIdx.add(pk);
            r.peakC = t[pk];
            r.peakHfWg = sm[pk];
            List<Integer> extrema = peakUp ? maxima : minima;
            for (int e : extrema) r.subPeakC.add(t[e]);
            if (extrema.size() >= 2) r.overlapping = true;
            onsetEndset(peakUp, pk, t, sm, d, pre, post, in, r);
        } else {
            // flat / no detectable transition: keep result honest
            r.empty = true;
            r.fired.add(Enums.PolicyRule.EMPTY_INTERVAL);
            return;
        }

        // Enthalpy: integral of (hf-baseline)dT divided by |scan rate|.
        // Sign is kept in the instrument frame; the EXO_UP/EXO_DOWN declaration decides
        // endo/exo semantics — a file name never does.
        double rateCperS = Double.isNaN(in.rateCperMin) ? Double.NaN : Math.abs(in.rateCperMin) / 60.0;
        r.areaWgC = Calc.trapz(t, corr);
        r.deltaH = Double.isNaN(rateCperS) || rateCperS == 0 ? Double.NaN : r.areaWgC / rateCperS;

        if (in.signConvention == Enums.SignConvention.UNKNOWN) {
            r.fired.add(Enums.PolicyRule.SIGN_NOT_FROM_FILENAME);
        }
        if (r.overlapping) {
            r.fired.add(Enums.PolicyRule.UNRESOLVED_KEEP_ALL);
        }
    }

    private static void onsetEndset(boolean up, int pk, double[] t, double[] sm, double[] d,
                                    double[] pre, double[] post, Input in, BaselineResult r) {
        int n = t.length;
        // leading flank = onset side (in temperature order); trailing = endset side
        int leadLo = 0, leadHi = pk;
        int trailLo = pk, trailHi = n - 1;
        int onTangentIdx = up ? steepest(d, leadLo, leadHi, true) : steepest(d, leadLo, leadHi, false);
        int offTangentIdx = up ? steepest(d, trailLo, trailHi, false) : steepest(d, trailLo, trailHi, true);

        double[] tl = tangentLine(t, sm, d, onTangentIdx);
        double[] tr = tangentLine(t, sm, d, offTangentIdx);
        // onset: leading tangent intersects the pre anchor line
        double onT = lineIntersectT(tl, pre);
        double offT = lineIntersectT(tr, post);
        r.onsetC = clamp(onT, t[0], t[n - 1]);
        r.endsetC = clamp(offT, t[0], t[n - 1]);
        r.onsetHfWg = r.a + r.b * r.onsetC;
        r.endsetHfWg = r.a + r.b * r.endsetC;
    }

    private static void glassStep(String method, double[] t, double[] sm, double[] corr, double[] d,
                                  double[] pre, double[] post, Input in, BaselineResult r) {
        // step midpoint: mean level change location at 50% between the anchor plateaus
        double loLevel = meanTail(corr, false), hiLevel = meanTail(corr, true);
        double target = (loLevel + hiLevel) / 2.0;
        int best = 0;
        double bestD = Double.POSITIVE_INFINITY;
        for (int i = 0; i < corr.length; i++) {
            double dd = Math.abs(corr[i] - target);
            if (dd < bestD) { bestD = dd; best = i; }
        }
        r.tgMidC = t[best];
        r.onsetC = extrapolateStep(t, corr, pre, best, true);
        r.endsetC = extrapolateStep(t, corr, post, best, false);
        r.onsetHfWg = r.a + r.b * r.onsetC;
        r.endsetHfWg = r.a + r.b * r.endsetC;
        r.peakC = t[best];
        r.peakHfWg = sm[best];
    }

    private static double extrapolateStep(double[] t, double[] corr, double[] line, int mid, boolean onsetSide) {
        // tangent at the steepest point of the step intersects the anchor extension
        double[] dd = Calc.derivative(t, corr);
        int k = onsetSide ? steepest(dd, 0, mid, true) : steepest(dd, mid, t.length - 1, true);
        double[] tan = tangentLine(t, corr, dd, k);
        return clamp(lineIntersectT(tan, line), t[0], t[t.length - 1]);
    }

    private static double[] tangentLine(double[] t, double[] y, double[] d, int i) {
        // line through (t_i, y_i) with slope d_i
        return new double[]{y[i] - d[i] * t[i], d[i]};
    }

    private static double lineIntersectT(double[] l1, double[] l2) {
        double denom = l1[1] - l2[1];
        if (denom == 0) return Double.NaN;
        return (l2[0] - l1[0]) / denom;
    }

    private static int steepest(double[] d, int lo, int hi, boolean positive) {
        int b = lo;
        for (int i = lo; i <= hi; i++) {
            if (positive ? d[i] > d[b] : d[i] < d[b]) b = i;
        }
        return b;
    }

    private static double meanTail(double[] corr, boolean highSide) {
        int n = corr.length, k = Math.max(2, n / 8);
        double s = 0;
        int start = highSide ? n - k : 0;
        for (int i = start; i < start + k; i++) s += corr[i];
        return s / k;
    }

    private static double clamp(double x, double lo, double hi) {
        if (Double.isNaN(x)) return Double.NaN;
        return Math.max(lo, Math.min(hi, x));
    }

    private static double[] slice(double[] a, int lo, int hi) {
        double[] o = new double[hi - lo + 1];
        System.arraycopy(a, lo, o, 0, o.length);
        return o;
    }

    /** Human+machine-readable error-source breakdown for the displayed enthalpy. */
    public static Map<String, Object> enthalpyErrorSources(Input in, BaselineResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rateKnown", !Double.isNaN(in.rateCperMin) && in.rateCperMin != 0);
        m.put("massNormalized", true);
        m.put("signConvention", in.signConvention.name());
        m.put("baselineMethod", r.method);
        m.put("nonUniformSampling", r.nonUniform);
        m.put("overlappingTransitions", r.overlapping);
        m.put("emptyInterval", r.empty);
        m.put("crossesSegments", r.crossesSegments);
        m.put("reversedTemperatureOrder", r.reversed);
        List<String> missing = new ArrayList<>();
        if (Boolean.FALSE.equals(m.get("rateKnown"))) missing.add("SCAN_RATE");
        if (in.signConvention == Enums.SignConvention.UNKNOWN) missing.add("SIGN_CONVENTION");
        if (r.empty) missing.add("POINTS_IN_INTERVAL");
        m.put("missingSources", missing);
        return m;
    }
}
