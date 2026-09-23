package dsc.engine;

import dsc.model.BaselineCandidate;
import dsc.model.BaselineParams;
import dsc.model.BaselineType;
import dsc.model.CandidateStatus;
import dsc.model.Diagnostic;
import dsc.model.DiagnosticLevel;
import dsc.model.Interval;
import dsc.model.NormalizedPoint;
import dsc.model.Quantity;
import dsc.model.SegmentLeg;
import dsc.model.TangentSelection;
import dsc.model.TraceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Constructs independently versioned {@link BaselineCandidate}s from tangent
 * selections. Legally different baselines coexist as alternatives; callers
 * never collapse them into one answer. All fitting happens on the calibrated,
 * endo-up normalized trace, restricted to one monotone leg.
 */
public final class BaselineEngine {

    private BaselineEngine() {}

    /** Points of the interval's pinned leg within [start,end], in trace order. */
    public static List<NormalizedPoint> intervalPoints(TraceView trace, Interval interval) {
        SegmentLeg leg = resolveLeg(trace, interval);
        List<NormalizedPoint> out = new ArrayList<>();
        for (NormalizedPoint p : trace.points()) {
            if (p.index() < leg.fromIndex() || p.index() > leg.toIndex()) continue;
            double lo = Math.min(interval.startC(), interval.endC());
            double hi = Math.max(interval.startC(), interval.endC());
            if (p.tC() >= lo - 1e-9 && p.tC() <= hi + 1e-9) out.add(p);
        }
        return out;
    }

    public static SegmentLeg resolveLeg(TraceView trace, Interval interval) {
        if (interval.legId() != null && !interval.legId().isBlank()) {
            for (SegmentLeg leg : trace.legs()) if (leg.legId().equals(interval.legId())) return leg;
            throw new IllegalArgumentException("leg " + interval.legId() + " not found in trace");
        }
        if (trace.legs().size() == 1) return trace.legs().get(0);
        throw new IllegalStateException("trace is cyclic (" + trace.legs().size()
                + " legs); interval must pin a leg so heating/cooling are not mixed");
    }

    public static BaselineCandidate build(TraceView trace, Interval interval, TangentSelection tangent,
                                          int version, long basedOnEventSeq) {
        List<Diagnostic> diags = new ArrayList<>();
        BaselineParams params = paramsOf(tangent);
        SegmentLeg leg;
        try {
            leg = resolveLeg(trace, interval);
        } catch (IllegalStateException e) {
            diags.add(Diagnostic.error("LEG_UNPINNED", e.getMessage()));
            return new BaselineCandidate(tangent.tangentId() + "#b" + version, interval.intervalId(),
                    trace.sequenceId(), tangent.tangentId(), tangent.type(), List.of(),
                    Double.NaN, Double.NaN, Double.NaN, CandidateStatus.ALTERNATIVE,
                    version, basedOnEventSeq, diags);
        }
        if (leg.direction() == dsc.model.SegmentDirection.MIXED) {
            diags.add(Diagnostic.error("MIXED_DIRECTION_INTERVAL",
                    "interval spans heating and cooling points; baselines may not mix directions"));
        }
        if (interval.isEmpty()) {
            diags.add(Diagnostic.error("EMPTY_INTERVAL",
                    "interval [" + Quantity.trim(interval.startC()) + ", " + Quantity.trim(interval.endC())
                            + "] degC is empty; no baseline can be fitted"));
            return new BaselineCandidate(tangent.tangentId() + "#b" + version, interval.intervalId(),
                    trace.sequenceId(), tangent.tangentId(), tangent.type(), List.of(),
                    Double.NaN, Double.NaN, Double.NaN, CandidateStatus.ALTERNATIVE,
                    version, basedOnEventSeq, diags);
        }

        List<NormalizedPoint> anchors = new ArrayList<>();
        collect(trace, leg, tangent.leftStartC(), tangent.leftEndC(), anchors);
        collect(trace, leg, tangent.rightStartC(), tangent.rightEndC(), anchors);
        if (anchors.size() < 2) {
            diags.add(Diagnostic.error("TANGENT_ANCHORS_EMPTY",
                    "tangent anchor regions contain " + anchors.size() + " point(s); at least 2 required"));
        }

        List<Double> coeffs = new ArrayList<>();
        double rmse = Double.NaN, tgMid = Double.NaN, tgWidth = Double.NaN;
        if (tangent.type() == BaselineType.LINEAR_TANGENT && anchors.size() >= 2) {
            double[] lr = linearRegression(anchors);
            coeffs = List.of(lr[0], lr[1]);
            rmse = rmse(anchors, coeffs);
        } else if (tangent.type() == BaselineType.POLYNOMIAL && anchors.size() >= 2) {
            int degree = Math.min(params.polynomialDegree(), anchors.size() - 1);
            coeffs = polyFit(anchors, degree);
            rmse = rmse(anchors, coeffs);
        } else if (tangent.type() == BaselineType.SIGMOID_STEP && anchors.size() >= 2) {
            List<NormalizedPoint> left = new ArrayList<>();
            List<NormalizedPoint> right = new ArrayList<>();
            collect(trace, leg, tangent.leftStartC(), tangent.leftEndC(), left);
            collect(trace, leg, tangent.rightStartC(), tangent.rightEndC(), right);
            if (left.size() < 2 || right.size() < 2) {
                diags.add(Diagnostic.error("TANGENT_ANCHORS_EMPTY",
                        "glass step needs >=2 points in each tangent region (left="
                                + left.size() + ", right=" + right.size() + ")"));
            } else {
                double[] lrL = linearRegression(left);
                double[] lrR = linearRegression(right);
                double lo = Math.min(interval.startC(), interval.endC());
                double hi = Math.max(interval.startC(), interval.endC());
                tgMid = (lo + hi) / 2.0;
                tgWidth = params.sigmoidWidthC() > 0 ? params.sigmoidWidthC() : Math.max(1.0, (hi - lo) / 10.0);
                double yL = lrL[0] + lrL[1] * tgMid;
                double yR = lrR[0] + lrR[1] * tgMid;
                double meanSlope = (lrL[1] + lrR[1]) / 2.0;
                double intercept = (yL + yR) / 2.0 - meanSlope * tgMid;
                coeffs = List.of(intercept, meanSlope, yR - yL, tgMid, tgWidth);
                rmse = stepRmse(trace, leg, coeffs, tangent);
                diags.add(Diagnostic.info("GLASS_STEP_BASELINE",
                        "sigmoid step baseline: Tg midpoint " + Quantity.trim(tgMid)
                                + " degC, width " + Quantity.trim(tgWidth)
                                + " degC, step " + Quantity.trim(yR - yL) + " mW"));
            }
        }
        checkCoverage(trace, leg, interval, diags);
        return new BaselineCandidate(tangent.tangentId() + "#b" + version, interval.intervalId(),
                trace.sequenceId(), tangent.tangentId(), tangent.type(), List.copyOf(coeffs),
                rmse, tgMid, tgWidth, CandidateStatus.ALTERNATIVE, version, basedOnEventSeq, diags);
    }

    private static BaselineParams paramsOf(TangentSelection t) {
        return new BaselineParams(t.leftStartC(), t.leftEndC(), t.rightStartC(), t.rightEndC(),
                t.type() == BaselineType.POLYNOMIAL ? 2 : 1,
                0.0, t.type());
    }

    /**
     * Baseline value (mW) at temperature tC. For SIGMOID_STEP the coefficient
     * list is [intercept, slope, stepHeight, tgMid, tgWidth]: a logistic step
     * on top of the mean tangent line.
     */
    public static double evaluate(BaselineCandidate c, double tC) {
        List<Double> a = c.coeffs();
        if (a.isEmpty()) return Double.NaN;
        if (c.type() == BaselineType.SIGMOID_STEP && a.size() >= 5) {
            double w = Math.max(1e-9, a.get(4));
            double logistic = 1.0 / (1.0 + Math.exp(-(tC - a.get(3)) / (w / 4.0)));
            return a.get(0) + a.get(1) * tC + a.get(2) * logistic;
        }
        double v = 0, x = 1;
        for (double coef : a) { v += coef * x; x *= tC; }
        return v;
    }

    private static double stepRmse(TraceView trace, SegmentLeg leg, List<Double> coeffs,
                                   TangentSelection tangent) {
        List<NormalizedPoint> pts = new ArrayList<>();
        collect(trace, leg, tangent.leftStartC(), tangent.leftEndC(), pts);
        collect(trace, leg, tangent.rightStartC(), tangent.rightEndC(), pts);
        double sse = 0;
        for (NormalizedPoint p : pts) {
            double w = Math.max(1e-9, coeffs.get(4));
            double logistic = 1.0 / (1.0 + Math.exp(-(p.tC() - coeffs.get(3)) / (w / 4.0)));
            double v = coeffs.get(0) + coeffs.get(1) * p.tC() + coeffs.get(2) * logistic;
            double e = v - p.hfMW();
            sse += e * e;
        }
        return Math.sqrt(sse / pts.size());
    }

    private static void collect(TraceView trace, SegmentLeg leg, double a, double b,
                                List<NormalizedPoint> out) {
        double lo = Math.min(a, b), hi = Math.max(a, b);
        for (NormalizedPoint p : trace.points()) {
            if (p.index() < leg.fromIndex() || p.index() > leg.toIndex()) continue;
            if (p.tC() >= lo - 1e-9 && p.tC() <= hi + 1e-9) out.add(p);
        }
    }

    private static void checkCoverage(TraceView trace, SegmentLeg leg, Interval interval,
                                      List<Diagnostic> diags) {
        List<NormalizedPoint> pts = intervalPoints(trace, interval);
        if (pts.isEmpty()) {
            diags.add(Diagnostic.error("INTERVAL_NO_DATA",
                    "interval contains no trace points on leg " + leg.legId()));
            return;
        }
        double span = Math.abs(pts.get(pts.size() - 1).tC() - pts.get(0).tC());
        if (span < 1e-6) {
            diags.add(Diagnostic.error("ZERO_SPAN_INTERVAL",
                    "all interval points share one temperature; integration undefined"));
        }
    }

    private static double[] linearRegression(List<NormalizedPoint> pts) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = pts.size();
        for (NormalizedPoint p : pts) {
            sx += p.tC(); sy += p.hfMW(); sxx += p.tC() * p.tC(); sxy += p.tC() * p.hfMW();
        }
        double den = n * sxx - sx * sx;
        if (Math.abs(den) < 1e-18) return new double[]{sy / n, 0};
        double slope = (n * sxy - sx * sy) / den;
        double intercept = (sy - slope * sx) / n;
        return new double[]{intercept, slope};
    }

    static List<Double> polyFit(List<NormalizedPoint> pts, int degree) {
        int m = degree + 1;
        int n = pts.size();
        double[][] x = new double[n][m];
        double[][] xt = new double[m][n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            double v = 1;
            for (int j = 0; j < m; j++) { x[i][j] = v; xt[j][i] = v; y[i] = pts.get(i).hfMW(); v *= pts.get(i).tC(); }
        }
        double[][] a = new double[m][m];
        double[] b = new double[m];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < m; j++)
                for (int k = 0; k < n; k++) a[i][j] += xt[i][k] * x[k][j];
            for (int k = 0; k < n; k++) b[i] += xt[i][k] * y[k];
        }
        double tMid = pts.stream().mapToDouble(NormalizedPoint::tC).average().orElse(0);
        for (int i = 0; i < m; i++) a[i][i] += 1e-9 * (1 + Math.abs(a[i][i]));
        return gaussianSolve(a, b, tMid);
    }

    private static List<Double> gaussianSolve(double[][] a, double[] b, double tMid) {
        int n = b.length;
        for (int p = 0; p < n; p++) {
            int max = p;
            for (int i = p + 1; i < n; i++) if (Math.abs(a[i][p]) > Math.abs(a[max][p])) max = i;
            double[] tmp = a[p]; a[p] = a[max]; a[max] = tmp;
            double t = b[p]; b[p] = b[max]; b[max] = t;
            for (int i = p + 1; i < n; i++) {
                double f = a[i][p] / a[p][p];
                for (int j = p; j < n; j++) a[i][j] -= f * a[p][j];
                b[i] -= f * b[p];
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = b[i];
            for (int j = i + 1; j < n; j++) sum -= a[i][j] * x[j];
            x[i] = sum / a[i][i];
        }
        List<Double> out = new ArrayList<>();
        for (double coef : x) out.add(coef);
        return out;
    }

    private static double rmse(List<NormalizedPoint> pts, List<Double> coeffs) {
        double sse = 0;
        for (NormalizedPoint p : pts) {
            double v = 0, x = 1;
            for (double c : coeffs) { v += c * x; x *= p.tC(); }
            double e = v - p.hfMW();
            sse += e * e;
        }
        return Math.sqrt(sse / pts.size());
    }

    static String fmt(double v) { return String.format(Locale.ROOT, "%.6g", v); }
}
