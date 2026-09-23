package dsc.derive;

import dsc.model.BaselineFit;
import dsc.model.Interval;
import dsc.model.Trace;
import dsc.model.Types;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic baseline construction. The engine never picks a single "answer": heating
 * and cooling are kept on their own segments, and every legitimate candidate is returned.
 *
 * Candidates:
 *  - LINEAR_TANGENT: straight line through the reviewer's pre/post tangent anchor windows
 *  - PIECEWISE_TANGENT: pre/post tangents joined at the event (glass step style)
 *  - CURVATURE: least-squares quadratic through the anchor windows
 */
public final class BaselineEngine {

    /** Anchor window [loK, hiK] of the pre- or post-event tangent, in calibrated K. */
    public record Window(double loK, double hiK) {
        boolean contains(double t) {
            return t >= loK && t <= hiK;
        }
    }

    private BaselineEngine() {}

    public static List<BaselineFit> candidates(String intervalId, Trace trace, Interval interval,
                                               Window pre, Window post, String idPrefix,
                                               int baselineVersion) {
        List<BaselineFit> out = new ArrayList<>();
        double[] l = linearThroughWindows(trace, pre, post);
        if (l != null) {
            out.add(new BaselineFit(idPrefix + ":linear", intervalId, BaselineFit.Kind.LINEAR_TANGENT,
                    l[0], l[1], 0.0, rms(trace, pre, post, l[0], l[1], 0.0),
                    Types.Decision.UNDECIDED, null,
                    "straight line through pre/post tangent windows", baselineVersion));
        }
        double[] q = quadraticThroughWindows(trace, pre, post);
        if (q != null) {
            out.add(new BaselineFit(idPrefix + ":curvature", intervalId, BaselineFit.Kind.CURVATURE,
                    q[0], q[1], q[2], rms(trace, pre, post, q[0], q[1], q[2]),
                    Types.Decision.UNDECIDED, null,
                    "curvature-corrected baseline from tangent anchors", baselineVersion));
        }
        double[] preL = linearInWindow(trace, pre);
        double[] postL = linearInWindow(trace, post);
        if (preL != null && postL != null) {
            double mid = (interval.startK + interval.endK) / 2.0;
            // Piecewise: pre tangent up to join point, post tangent after. Encode with a != 0 flag
            // handled specially in valueAt via PieceBaseline below; store join in metadata via id
            // mapping is unnecessary because features read anchors directly.
            out.add(new PieceBaseline(idPrefix + ":piecewise", intervalId,
                    preL[0], preL[1], postL[0], postL[1], mid,
                    Types.Decision.UNDECIDED, null,
                    "independent pre/post tangents joined at event window", baselineVersion));
        }
        return out;
    }

    /** Baseline strictly inside an empty window: no anchors ⇒ no unique baseline possible. */
    public static List<BaselineFit> emptyWindowCandidates(String intervalId, String idPrefix, int version) {
        // Deliberately returns no candidates; caller records an EMPTY_INTERVAL diagnostic event.
        return List.of();
    }

    static double[] linearThroughWindows(Trace trace, Window pre, Window post) {
        List<double[]> pts = collect(trace, pre);
        pts.addAll(collect(trace, post));
        return fitLinear(pts);
    }

    static double[] linearInWindow(Trace trace, Window w) {
        return fitLinear(collect(trace, w));
    }

    static double[] quadraticThroughWindows(Trace trace, Window pre, Window post) {
        List<double[]> pts = collect(trace, pre);
        pts.addAll(collect(trace, post));
        return fitQuadratic(pts);
    }

    private static List<double[]> collect(Trace trace, Window w) {
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < trace.size(); i++) {
            double t = trace.calibratedTempK(i);
            double h = trace.endoUpHeatFlowW(i);
            if (!Double.isNaN(t) && !Double.isNaN(h) && w.contains(t)) {
                pts.add(new double[]{t, h});
            }
        }
        return pts;
    }

    /** Least squares y = a + b x. Returns null for fewer than 2 points. */
    public static double[] fitLinear(List<double[]> pts) {
        int n = pts.size();
        if (n < 2) return null;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (double[] p : pts) {
            sx += p[0]; sy += p[1]; sxx += p[0] * p[0]; sxy += p[0] * p[1];
        }
        double den = n * sxx - sx * sx;
        if (Math.abs(den) < 1e-30) return null;
        double b = (n * sxy - sx * sy) / den;
        double a = (sy - b * sx) / n;
        return new double[]{a, b};
    }

    /** Least squares y = a + b x + c x^2 via 3x3 normal equations. Returns null if degenerate. */
    static double[] fitQuadratic(List<double[]> pts) {
        int n = pts.size();
        if (n < 3) return null;
        double[] m = new double[9];
        double[] rhs = new double[3];
        for (double[] p : pts) {
            double x = p[0], y = p[1];
            double x2 = x * x, x3 = x2 * x, x4 = x3 * x;
            m[0] += 1; m[1] += x; m[2] += x2;
            m[3] += x; m[4] += x2; m[5] += x3;
            m[6] += x2; m[7] += x3; m[8] += x4;
            rhs[0] += y; rhs[1] += x * y; rhs[2] += x2 * y;
        }
        return solve3(m, rhs) == null ? null : rhs;
    }

    private static double[] solve3(double[] m, double[] r) {
        for (int col = 0; col < 3; col++) {
            int piv = col;
            for (int i = col + 1; i < 3; i++) {
                if (Math.abs(m[i * 3 + col]) > Math.abs(m[piv * 3 + col])) piv = i;
            }
            if (Math.abs(m[piv * 3 + col]) < 1e-24) return null;
            for (int j = 0; j < 3; j++) {
                double tmp = m[col * 3 + j];
                m[col * 3 + j] = m[piv * 3 + j];
                m[piv * 3 + j] = tmp;
            }
            double tr = r[col]; r[col] = r[piv]; r[piv] = tr;
            for (int i = 0; i < 3; i++) {
                if (i == col) continue;
                double f = m[i * 3 + col] / m[col * 3 + col];
                for (int j = col; j < 3; j++) m[i * 3 + j] -= f * m[col * 3 + j];
                r[i] -= f * r[col];
            }
        }
        double[] x = new double[3];
        for (int i = 0; i < 3; i++) x[i] = r[i] / m[i * 3 + i];
        return x;
    }

    private static double rms(Trace trace, Window pre, Window post, double a, double b, double c) {
        List<double[]> pts = collect(trace, pre);
        pts.addAll(collect(trace, post));
        if (pts.isEmpty()) return Double.NaN;
        double sum = 0;
        for (double[] p : pts) {
            double d = p[1] - (a + b * p[0] + c * p[0] * p[0]);
            sum += d * d;
        }
        return Math.sqrt(sum / pts.size());
    }
}
