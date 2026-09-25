package dsc.core;

import dsc.model.Model.BaselineCandidate;
import dsc.model.Model.Calibration;
import dsc.model.Model.Diagnostic;
import dsc.model.Model.Direction;
import dsc.model.Model.ErrorSource;
import dsc.model.Model.RawPoint;
import dsc.model.Model.RawSeries;
import dsc.model.Model.Segment;
import dsc.model.Model.Status;
import dsc.model.Model.TransitionResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Derived-layer analysis. Normalizes units, builds baseline candidates
 * (all reasonable candidates are kept), finds onset/peak/endset and
 * integrates with the trapezoid rule on the actual (possibly non-uniform)
 * time axis. Empty intervals yield diagnostics, never fabricated numbers.
 */
public final class Analysis {
    private Analysis() {}

    /** Normalized view of one segment: Celsius, mW, seconds. */
    public record NormPoint(double timeS, double tempC, double hfMW) {}

    public record NormSeries(List<NormPoint> points, List<Diagnostic> diagnostics) {}

    public static NormSeries normalize(RawSeries series, Segment seg, Calibration calib) {
        List<NormPoint> out = new ArrayList<>();
        List<Diagnostic> diags = new ArrayList<>();
        double offset = calib != null ? calib.offsetC() : 0.0;
        double slope = calib != null ? calib.slope() : 1.0;
        for (int i = seg.startIndex(); i <= seg.endIndex(); i++) {
            RawPoint p = series.points().get(i);
            Units.Normalized t = Units.temperature(p.temperature(), series.tempUnit());
            Units.Normalized h = Units.heatFlow(p.heatFlow(), series.heatFlowUnit());
            if (t.diagnostic() != null) diags.add(t.diagnostic());
            if (h.diagnostic() != null) diags.add(h.diagnostic());
            if (t.ok() && h.ok()) {
                out.add(new NormPoint(p.time(), t.value() * slope + offset, h.value()));
            }
        }
        return new NormSeries(out, diags);
    }

    /** Points of the segment whose temperature falls inside [t1, t2] (either order). */
    public static List<NormPoint> window(NormSeries ns, double t1, double t2) {
        double lo = Math.min(t1, t2), hi = Math.max(t1, t2);
        List<NormPoint> out = new ArrayList<>();
        for (NormPoint p : ns.points()) {
            if (p.tempC() >= lo && p.tempC() <= hi) out.add(p);
        }
        return out;
    }

    /** Build every reasonable baseline candidate for the interval. */
    public static List<BaselineCandidate> baselineCandidates(NormSeries ns, Segment seg,
                                                             double t1, double t2, int version) {
        List<NormPoint> w = window(ns, t1, t2);
        List<BaselineCandidate> out = new ArrayList<>();
        if (w.size() < 3) return out;
        String base = "bl:" + seg.id() + ":" + (int) Math.round(t1) + "-" + (int) Math.round(t2);
        NormPoint a = w.get(0), b = w.get(w.size() - 1);

        // LINEAR: straight line between interval endpoints.
        out.add(new BaselineCandidate(base + ":linear:v" + version, seg.id(), "LINEAR",
                a.tempC(), b.tempC(),
                List.of(a.tempC(), a.hfMW(), b.tempC(), b.hfMW()), Status.CANDIDATE, version));

        // LEFT_TANGENT / RIGHT_TANGENT: extrapolated tangents from each edge.
        double[] left = edgeLine(w, true);
        double[] right = edgeLine(w, false);
        out.add(new BaselineCandidate(base + ":left-tangent:v" + version, seg.id(), "LEFT_TANGENT",
                a.tempC(), b.tempC(),
                List.of(a.tempC(), left[0] + left[1] * a.tempC(), b.tempC(), left[0] + left[1] * b.tempC()),
                Status.CANDIDATE, version));
        out.add(new BaselineCandidate(base + ":right-tangent:v" + version, seg.id(), "RIGHT_TANGENT",
                a.tempC(), b.tempC(),
                List.of(a.tempC(), right[0] + right[1] * a.tempC(), b.tempC(), right[0] + right[1] * b.tempC()),
                Status.CANDIDATE, version));

        // SIGMOIDAL: s-shaped interpolation weighted by cumulative residual area;
        // the classic choice when two transitions overlap.
        List<Double> knots = new ArrayList<>();
        double cum = 0;
        List<Double> frac = new ArrayList<>();
        for (int i = 0; i < w.size(); i++) {
            NormPoint p = w.get(i);
            double lin = lerp(a.tempC(), a.hfMW(), b.tempC(), b.hfMW(), p.tempC());
            if (i > 0) {
                NormPoint q = w.get(i - 1);
                double linQ = lerp(a.tempC(), a.hfMW(), b.tempC(), b.hfMW(), q.tempC());
                cum += Math.abs((p.hfMW() - lin + q.hfMW() - linQ) / 2.0 * (p.timeS() - q.timeS()));
            }
            frac.add(cum);
        }
        double total = Math.max(cum, 1e-12);
        for (int i = 0; i < w.size(); i++) {
            NormPoint p = w.get(i);
            double f = frac.get(i) / total;
            double lv = left[0] + left[1] * p.tempC();
            double rv = right[0] + right[1] * p.tempC();
            knots.add(p.tempC());
            knots.add(lv * (1 - f) + rv * f);
        }
        out.add(new BaselineCandidate(base + ":sigmoidal:v" + version, seg.id(), "SIGMOIDAL",
                a.tempC(), b.tempC(), knots, Status.CANDIDATE, version));
        return out;
    }

    private static double[] edgeLine(List<NormPoint> w, boolean leftEdge) {
        int n = Math.max(2, w.size() / 5);
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = 0; i < n; i++) {
            NormPoint p = w.get(leftEdge ? i : w.size() - 1 - i);
            sx += p.tempC(); sy += p.hfMW();
            sxx += p.tempC() * p.tempC(); sxy += p.tempC() * p.hfMW();
        }
        double denom = n * sxx - sx * sx;
        double slope = denom == 0 ? 0 : (n * sxy - sx * sy) / denom;
        double intercept = (sy - slope * sx) / n;
        return new double[]{intercept, slope};
    }

    /** Piecewise-linear baseline evaluation at a temperature. */
    public static double evalBaseline(BaselineCandidate bl, double tempC) {
        List<Double> k = bl.coeff();
        if (k.size() < 4) return k.size() >= 2 ? k.get(1) : 0;
        double lo = Math.min(k.get(0), k.get(k.size() - 2));
        double hi = Math.max(k.get(0), k.get(k.size() - 2));
        if (tempC <= lo) return k.get(1);
        if (tempC >= hi) return k.get(k.size() - 1);
        for (int i = 0; i + 3 < k.size(); i += 2) {
            double t0 = k.get(i), t1 = k.get(i + 2);
            if ((tempC - t0) * (tempC - t1) <= 0) {
                return lerp(t0, k.get(i + 1), t1, k.get(i + 3), tempC);
            }
        }
        return k.get(k.size() - 1);
    }

    private static double lerp(double x0, double y0, double x1, double y1, double x) {
        if (x1 == x0) return y0;
        return y0 + (y1 - y0) * (x - x0) / (x1 - x0);
    }

    public record AnalysisOutcome(TransitionResult result, Diagnostic diagnostic) {}

    /**
     * Full transition analysis for one baseline. Direction (endo/exo) follows the
     * instrument convention metadata and the scan direction; it is never guessed
     * from names. An empty interval produces a diagnostic and no result.
     */
    public static AnalysisOutcome analyze(NormSeries ns, Segment seg, BaselineCandidate bl,
                                          Boolean endoUp, Double massMg, Double massUnc,
                                          Calibration calib, String resultId) {
        List<NormPoint> w = window(ns, bl.t1(), bl.t2());
        if (w.isEmpty()) {
            return new AnalysisOutcome(null, Units.diag("EMPTY_INTERVAL",
                    "interval [" + bl.t1() + ", " + bl.t2() + "] C contains no points of " + seg.id()
                            + "; no onset/peak/endset fabricated", bl.id()));
        }
        // residual r = HF - baseline
        double[] r = new double[w.size()];
        int peakIdx = 0;
        for (int i = 0; i < w.size(); i++) {
            r[i] = w.get(i).hfMW() - evalBaseline(bl, w.get(i).tempC());
            if (Math.abs(r[i]) > Math.abs(r[peakIdx])) peakIdx = i;
        }
        boolean positivePeak = r[peakIdx] > 0;
        String endoExo;
        if (endoUp == null) {
            endoExo = "UNDECIDED";
        } else {
            boolean endotherm = positivePeak == endoUp;
            endoExo = endotherm ? "ENDOTHERM" : "EXOTHERM";
        }
        double peakC = w.get(peakIdx).tempC();
        double onsetC = intersectTangent(w, r, peakIdx, true, bl);
        double endsetC = intersectTangent(w, r, peakIdx, false, bl);
        double lo = Math.min(onsetC, endsetC), hi = Math.max(onsetC, endsetC);

        // Trapezoid integration on the actual time axis (non-uniform safe).
        double areaMJ = 0;
        for (int i = 0; i + 1 < w.size(); i++) {
            NormPoint p = w.get(i), q = w.get(i + 1);
            boolean inP = p.tempC() >= lo && p.tempC() <= hi;
            boolean inQ = q.tempC() >= lo && q.tempC() <= hi;
            if (inP && inQ) {
                areaMJ += (r[i] + r[i + 1]) / 2.0 * (q.timeS() - p.timeS());
            }
        }
        double enthalpyJ = areaMJ / 1000.0;
        Double perGram = null;
        List<ErrorSource> errors = new ArrayList<>();
        if (massMg != null && massMg > 0) {
            double grams = massMg / 1000.0;
            perGram = enthalpyJ / grams;
            if (massUnc != null && massUnc > 0) {
                errors.add(new ErrorSource("MASS_UNCERTAINTY",
                        "sample mass " + massMg + " mg ± " + massUnc + " mg",
                        Math.abs(perGram) * massUnc / massMg));
            }
        } else {
            errors.add(new ErrorSource("MASS_MISSING", "no usable sample mass; only absolute J reported", 0));
        }
        if (calib != null && calib.offsetC() != 0) {
            double maxSlope = maxAbsSlope(w, r);
            double widthS = Math.abs(w.get(w.size() - 1).timeS() - w.get(0).timeS());
            double errJ = Math.abs(calib.offsetC()) * maxSlope * widthS / 1000.0;
            double errJg = perGram != null ? errJ / (massMg / 1000.0) : 0;
            errors.add(new ErrorSource("CALIBRATION_OFFSET",
                    "temperature calibration " + calib.id() + " offset " + calib.offsetC() + " C vs "
                            + calib.referenceMaterial(), errJg));
        }
        if (endoUp == null) {
            errors.add(new ErrorSource("DIRECTION_UNKNOWN",
                    "endo/exo undecided: HeatFlowConvention missing", 0));
        }
        TransitionResult tr = new TransitionResult(resultId, bl.id(), seg.id(),
                onsetC, peakC, endsetC, enthalpyJ, perGram, endoExo, errors, Status.CANDIDATE);
        return new AnalysisOutcome(tr, null);
    }

    /** Add the cross-baseline spread as an error source, once all candidates are integrated. */
    public static ErrorSource baselineSpread(List<TransitionResult> results) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (TransitionResult t : results) {
            if (t.enthalpyJPerG() != null) {
                min = Math.min(min, t.enthalpyJPerG());
                max = Math.max(max, t.enthalpyJPerG());
            }
        }
        if (min > max) return new ErrorSource("BASELINE_SPREAD", "single baseline only", 0);
        return new ErrorSource("BASELINE_SPREAD",
                results.size() + " plausible baselines span [" + round(min) + ", " + round(max) + "] J/g",
                (max - min) / 2.0);
    }

    private static double maxAbsSlope(List<NormPoint> w, double[] r) {
        double max = 0;
        for (int i = 0; i + 1 < w.size(); i++) {
            double dT = w.get(i + 1).tempC() - w.get(i).tempC();
            if (dT != 0) max = Math.max(max, Math.abs((r[i + 1] - r[i]) / dT));
        }
        return max;
    }

    /** Intersection of the steepest residual tangent on one side of the peak with the baseline. */
    private static double intersectTangent(List<NormPoint> w, double[] r, int peakIdx,
                                           boolean leading, BaselineCandidate bl) {
        int best = -1;
        double bestSlope = 0;
        int i = leading ? 0 : peakIdx;
        int end = leading ? peakIdx : w.size() - 1;
        for (; i < end; i++) {
            double dT = w.get(i + 1).tempC() - w.get(i).tempC();
            if (dT == 0) continue;
            double s = (r[i + 1] - r[i]) / dT;
            if (Math.abs(s) > Math.abs(bestSlope)) { bestSlope = s; best = i; }
        }
        if (best < 0) return w.get(leading ? 0 : w.size() - 1).tempC();
        NormPoint p = w.get(best);
        // tangent line: r = bestSlope * (T - p.T) + r[best]; intersect with r = 0 relative to baseline
        if (bestSlope == 0) return p.tempC();
        double tCross = p.tempC() - r[best] / bestSlope;
        double lo = Math.min(w.get(0).tempC(), w.get(w.size() - 1).tempC());
        double hi = Math.max(w.get(0).tempC(), w.get(w.size() - 1).tempC());
        return Math.max(lo, Math.min(hi, tCross));
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    /** Guard: never mix heating and cooling points in one analysis window. */
    public static void checkDirection(Segment seg) {
        if (seg.direction() == Direction.ISOTHERMAL) {
            throw new IllegalArgumentException("isothermal segment " + seg.id() + " cannot be integrated as a scan");
        }
    }
}
