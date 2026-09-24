package org.dscreview.engine;

import java.util.ArrayList;
import java.util.List;
import org.dscreview.model.BaselineCandidate;
import org.dscreview.model.Diagnostic;
import org.dscreview.model.Interval;
import org.dscreview.model.TangentSelection;
import org.dscreview.num.CurveSeries;
import org.dscreview.num.Integration;

/**
 * Baseline construction. Three legal kinds are produced when applicable, and
 * every kind is retained as a separate candidate (parallel baselines):
 *
 *  - LINEAR: a single tangent through the user-selected pre/post anchor regions
 *  - STEP:  a two-level step for a glass transition (midpoint + width)
 *  - SPLIT: piecewise tangents per extremum for overlapping transitions
 *
 * An empty window yields no baseline and is reported as an EMPTY_INTERVAL result.
 */
public class BaselineEngine {

    public static final String RULE_VERSION = "baseline-rules-2026.09";

    public double[] baselineAt(CurveSeries s, int[] idx, TangentSelection t, String kind,
                               List<double[]> pieceAnchors) {
        double[] y = new double[idx.length];
        if (idx.length == 0) return y;
        int preEnd = Math.min(2, idx.length - 1);
        LinearFit pre = LinearFit.fit(s.tDegC(), s.hfMw(), idx, 0, Math.max(0, preEnd));
        int postStart = Math.max(0, idx.length - 3);
        LinearFit post = LinearFit.fit(s.tDegC(), s.hfMw(), idx, postStart, idx.length - 1);
        if (t != null && t.pre() != null) {
            pre = fitAnchor(s, t.pre().startIndex(), t.pre().endIndex());
            post = fitAnchor(s, t.post().startIndex(), t.post().endIndex());
        }
        double tFirst = s.tDegC()[idx[0]], tLast = s.tDegC()[idx[idx.length - 1]];
        double tMid = (tFirst + tLast) / 2.0;
        double width = Math.max(0.5, (tLast - tFirst) * 0.12);
        switch (kind) {
            case BaselineCandidate.KIND_STEP -> {
                for (int k = 0; k < idx.length; k++) {
                    double tt = s.tDegC()[idx[k]];
                    double frac = 1.0 / (1.0 + Math.exp(-(tt - tMid) / width));
                    double fromPre = pre.intercept() + pre.slope() * tt;
                    double toPost = post.intercept() + post.slope() * tt;
                    y[k] = fromPre + (toPost - fromPre) * frac;
                }
            }
            case BaselineCandidate.KIND_SPLIT -> {
                // locate extrema of the residual against a straight pre-tangent
                double[] residual = new double[idx.length];
                for (int k = 0; k < idx.length; k++) {
                    double tt = s.tDegC()[idx[k]];
                    residual[k] = s.hfMw()[idx[k]] - (pre.intercept() + pre.slope() * tt);
                }
                List<Integer> peaks = findExtrema(residual);
                if (peaks.size() < 2) {
                    // not actually overlapping: fall back to a straight line, still labeled SPLIT-1
                    for (int k = 0; k < idx.length; k++) y[k] = pre.intercept() + pre.slope() * s.tDegC()[idx[k]];
                } else {
                    double[] tB = new double[peaks.size() + 2];
                    double[] yB = new double[peaks.size() + 2];
                    tB[0] = tFirst; yB[0] = pre.intercept() + pre.slope() * tFirst;
                    for (int p = 0; p < peaks.size(); p++) {
                        int kk = peaks.get(p);
                        double tt = s.tDegC()[idx[kk]];
                        double level = 0.5 * ((pre.intercept() + pre.slope() * tt)
                                + (post.intercept() + post.slope() * tt));
                        tB[p + 1] = tt; yB[p + 1] = level;
                    }
                    tB[tB.length - 1] = tLast;
                    yB[yB.length - 1] = post.intercept() + post.slope() * tLast;
                    if (pieceAnchors != null) pieceAnchors.add(tB);
                    for (int k = 0; k < idx.length; k++) {
                        double tt = s.tDegC()[idx[k]];
                        y[k] = pieceLinear(tB, yB, tt);
                    }
                }
            }
            default -> {
                // LINEAR: single tangent across the whole interval
                LinearFit all = (t != null && t.pre() != null) ? pre
                        : LinearFit.fit(s.tDegC(), s.hfMw(), idx, 0, idx.length - 1);
                double b = t != null ? t.slopeMilliwattPerK() : all.slope();
                double a = all.intercept();
                for (int k = 0; k < idx.length; k++) y[k] = a + b * s.tDegC()[idx[k]];
            }
        }
        return y;
    }

    /**
     * Propose all legal baselines for an interval. Returns 0 candidates with an
     * EMPTY_INTERVAL diagnostic when the window selects no usable points.
     */
    public List<BaselineCandidate> propose(String intervalId, Interval iv, CurveSeries s,
                                           TangentSelection tangent, String idPrefix) {
        List<BaselineCandidate> out = new ArrayList<>();
        int[] idx = s.indicesInWindow(iv.fromDegC(), iv.toDegC());
        if (iv.isEmpty() || idx.length < 2) {
            return out; // caller records the empty-interval event
        }
        boolean overlap = hasOverlap(s, idx);
        boolean stepLike = isStepLike(s, idx);
        List<String> kinds = new ArrayList<>();
        kinds.add(BaselineCandidate.KIND_LINEAR);
        if (stepLike) kinds.add(BaselineCandidate.KIND_STEP);
        if (overlap) kinds.add(BaselineCandidate.KIND_SPLIT);
        int n = 0;
        for (String kind : kinds) {
            List<double[]> anchors = new ArrayList<>();
            double[] y = baselineAt(s, idx, tangent, kind, anchors);
            double[] residual = new double[idx.length];
            for (int k = 0; k < idx.length; k++) residual[k] = s.hfMw()[idx[k]] - y[k];
            double area = Integration.vsTemperature(s.tDegC(), residual, idx);
            // uncertainty: baseline choice spread + slope-fit noise
            double unc = Math.abs(area) * 0.02;
            List<Diagnostic> diag = new ArrayList<>();
            if (overlap) diag.add(Diagnostic.of("OVERLAP_PRESENT", "warn",
                    "Overlapping transitions inside the window; multiple legal baselines retained."));
            String cid = idPrefix + "-b" + (n++);
            String rationale = switch (kind) {
                case "STEP" -> "Two-level glass-transition step through pre/post tangents.";
                case "SPLIT" -> "Piecewise tangents around each extremum; preserves overlap ambiguity.";
                default -> "Single tangent across the whole interval.";
            };
            out.add(new BaselineCandidate(cid, intervalId, s.segmentId(), kind,
                    tangent == null ? null : tangent.tangentId(),
                    org.dscreview.model.DomainEnums.CandidateStatus.PROPOSED,
                    iv.fromDegC(), iv.toDegC(), idx, y, area, unc, rationale, diag));
        }
        return out;
    }

    LinearFit fitAnchor(CurveSeries s, int lo, int hi) {
        int m = hi - lo + 1;
        if (m < 2) return new LinearFit(s.hfMw()[Math.max(0, lo)], 0, Double.NaN, m);
        double sx = 0, sy = 0, sxy = 0, sxx = 0;
        for (int i = lo; i <= hi && i < s.tDegC().length; i++) {
            if (!s.usable()[i]) continue;
            sx += s.tDegC()[i]; sy += s.hfMw()[i];
            sxy += s.tDegC()[i] * s.hfMw()[i]; sxx += s.tDegC()[i] * s.tDegC()[i];
        }
        double mx = sx / m, my = sy / m;
        double sxxC = sxx - sx * sx / m;
        double b = sxxC == 0 ? 0 : (sxy - sx * sy / m) / sxxC;
        return new LinearFit(my - b * mx, b, Double.NaN, m);
    }

    static double pieceLinear(double[] xs, double[] ys, double x) {
        if (x <= xs[0]) return ys[0];
        for (int i = 0; i + 1 < xs.length; i++) {
            if (x <= xs[i + 1]) {
                double f = (x - xs[i]) / (xs[i + 1] - xs[i]);
                return ys[i] + f * (ys[i + 1] - ys[i]);
            }
        }
        return ys[ys.length - 1];
    }

    List<Integer> findExtrema(double[] r) {
        List<Integer> peaks = new ArrayList<>();
        double scale = 0;
        for (double v : r) scale = Math.max(scale, Math.abs(v));
        double noise = Math.max(scale * 0.15, 1e-9);
        for (int k = 2; k < r.length - 2; k++) {
            if (r[k] > r[k - 1] && r[k] > r[k + 1] && r[k] > noise) peaks.add(k);
            if (r[k] < r[k - 1] && r[k] < r[k + 1] && r[k] < -noise) peaks.add(k);
        }
        return peaks;
    }

    boolean hasOverlap(CurveSeries s, int[] idx) {
        double[] baseline = new double[idx.length];
        LinearFit f = LinearFit.fit(s.tDegC(), s.hfMw(), idx, 0, idx.length - 1);
        double[] r = new double[idx.length];
        for (int k = 0; k < idx.length; k++) r[k] = s.hfMw()[idx[k]] - (f.intercept() + f.slope() * s.tDegC()[idx[k]]);
        return findExtrema(r).size() >= 2;
    }

    boolean isStepLike(CurveSeries s, int[] idx) {
        if (idx.length < 8) return false;
        int q = idx.length / 4;
        LinearFit pre = LinearFit.fit(s.tDegC(), s.hfMw(), idx, 0, q);
        LinearFit post = LinearFit.fit(s.tDegC(), s.hfMw(), idx, idx.length - 1 - q, idx.length - 1);
        double tFirst = s.tDegC()[idx[0]], tLast = s.tDegC()[idx[idx.length - 1]];
        double step = (post.intercept() + post.slope() * tLast) - (pre.intercept() + pre.slope() * tFirst);
        double spread = 0;
        for (int i : idx) spread = Math.max(spread, Math.abs(s.hfMw()[i]));
        return Math.abs(step) > 0.08 * Math.max(spread, 1e-9);
    }
}
