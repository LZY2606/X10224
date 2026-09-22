package dsc.engine;

import dsc.domain.Fit;
import dsc.state.State;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Produces every defensible baseline for a window. Multiple candidates are
 * retained with their fit zones and residuals; nothing is merged into a single
 * answer without a human decision.
 */
final class BaselineCandidates {

    private BaselineCandidates() {
    }

    record Candidate(String method, double[] baseline, double rmsResidualW,
                     Map<String, Object> detail, List<Map<String, Object>> diagnostics) {
    }

    static List<Candidate> build(WindowData w, Map<String, Object> tangent,
                                 Map<String, Object> runMeta) {
        List<Candidate> out = new ArrayList<>();
        if (w.empty()) {
            return out;
        }
        List<Map<String, Object>> diagnostics = new ArrayList<>();
        out.add(chord(w, diagnostics));
        Candidate tangentCandidate = tangentLinear(w, tangent);
        if (tangentCandidate != null) {
            out.add(tangentCandidate);
        } else {
            diagnostics.add(WindowExtractor.diag("TANGENT_MISSING",
                    "no user-selected tangent archived for this window; "
                            + "two-tangent candidate unavailable"));
        }
        out.add(quadratic(w));
        // attach diagnostics to every candidate via the chord candidate copy
        for (Candidate c : out) {
            c.diagnostics().addAll(diagnostics);
        }
        return out;
    }

    /** Straight line between window endpoints. */
    private static Candidate chord(WindowData w, List<Map<String, Object>> diagnostics) {
        int n = w.tK().length;
        double[] b = new double[n];
        for (int i = 0; i < n; i++) {
            double frac = (w.tK()[i] - w.tK()[0]) / Math.max(1e-12, w.tK()[n - 1] - w.tK()[0]);
            b[i] = w.heatFlowW()[0]
                    + frac * (w.heatFlowW()[n - 1] - w.heatFlowW()[0]);
        }
        double rms = residual(w.heatFlowW(), b);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("fitZone", List.of("window:start", "window:end"));
        detail.put("note", "endpoint chord; safe for isolated peaks");
        return new Candidate("chord", b, rms, detail, new ArrayList<>());
    }

    /**
     * Linear baseline fitted through the user-selected pre/post tangent fit
     * zones (a separately archived TANGENT source). The two fitted tangent
     * lines are bridged at the midpoint, which is the standard two-tangent
     * construction for overlapping events.
     */
    private static Candidate tangentLinear(WindowData w, Map<String, Object> tangent) {
        if (tangent == null) {
            return null;
        }
        Double preStart = dbl(tangent.get("preStartK"));
        Double preEnd = dbl(tangent.get("preEndK"));
        Double postStart = dbl(tangent.get("postStartK"));
        Double postEnd = dbl(tangent.get("postEndK"));
        if (preStart == null || preEnd == null || postStart == null || postEnd == null) {
            return null;
        }
        Fit.Line pre = fitZone(w, preStart, preEnd);
        Fit.Line post = fitZone(w, postStart, postEnd);
        if (pre == null || post == null) {
            return null;
        }
        int n = w.tK().length;
        double[] b = new double[n];
        double tBridge = 0.5 * (w.tK()[0] + w.tK()[n - 1]);
        for (int i = 0; i < n; i++) {
            b[i] = w.tK()[i] <= tBridge ? pre.y(w.tK()[i]) : post.y(w.tK()[i]);
        }
        double rms = residual(w.heatFlowW(), b);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("tangentId", tangent.get("tangentId"));
        detail.put("preSlope", pre.slope());
        detail.put("postSlope", post.slope());
        detail.put("bridgeK", tBridge);
        detail.put("fitZone", List.of("tangent:pre", "tangent:post"));
        List<Map<String, Object>> diags = new ArrayList<>();
        if (tangent.get("sourceEventId") != null) {
            // provenance only; no problem
        }
        return new Candidate("two_tangent_linear", b, rms, detail, diags);
    }

    /** Least-squares quadratic over the window; a curved baseline alternative. */
    private static Candidate quadratic(WindowData w) {
        int n = w.tK().length;
        double[] x0 = new double[n];
        double mean = Arrays.stream(w.tK()).average().orElse(0.0);
        for (int i = 0; i < n; i++) {
            x0[i] = w.tK()[i] - mean;
        }
        // normal equations for y = a + b*x + c*x^2
        double s0 = n;
        double s1 = 0.0;
        double s2 = 0.0;
        double s3 = 0.0;
        double s4 = 0.0;
        double y0 = 0.0;
        double y1 = 0.0;
        double y2 = 0.0;
        for (int i = 0; i < n; i++) {
            double x = x0[i];
            double xx = x * x;
            s1 += x;
            s2 += xx;
            s3 += xx * x;
            s4 += xx * xx;
            y0 += w.heatFlowW()[i];
            y1 += w.heatFlowW()[i] * x;
            y2 += w.heatFlowW()[i] * xx;
        }
        double[] coef = solve3(new double[][]{
                {s0, s1, s2}, {s1, s2, s3}, {s2, s3, s4}
        }, new double[]{y0, y1, y2});
        double[] b = new double[n];
        for (int i = 0; i < n; i++) {
            double x = x0[i];
            b[i] = coef[0] + coef[1] * x + coef[2] * x * x;
        }
        double rms = residual(w.heatFlowW(), b);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("a", coef[0]);
        detail.put("b", coef[1]);
        detail.put("c", coef[2]);
        detail.put("fitZone", List.of("window:all"));
        detail.put("note", "curved alternative; not preferred when shoulders drift");
        return new Candidate("quadratic", b, rms, detail, new ArrayList<>());
    }

    private static Fit.Line fitZone(WindowData w, double lo, double hi) {
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        for (int i = 0; i < w.tK().length; i++) {
            if (w.tK()[i] >= Math.min(lo, hi) && w.tK()[i] <= Math.max(lo, hi)) {
                xs.add(w.tK()[i]);
                ys.add(w.heatFlowW()[i]);
            }
        }
        if (xs.size() < 2) {
            return null;
        }
        return Fit.linear(
                xs.stream().mapToDouble(Double::doubleValue).toArray(),
                ys.stream().mapToDouble(Double::doubleValue).toArray());
    }

    private static double residual(double[] y, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < y.length; i++) {
            double d = y[i] - b[i];
            sum += d * d;
        }
        return Math.sqrt(sum / Math.max(1, y.length));
    }

    private static Double dbl(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    /** Gaussian elimination for the 3x3 normal equations. */
    static double[] solve3(double[][] a, double[] rhs) {
        double[][] m = new double[3][4];
        for (int i = 0; i < 3; i++) {
            System.arraycopy(a[i], 0, m[i], 0, 3);
            m[i][3] = rhs[i];
        }
        for (int col = 0; col < 3; col++) {
            int pivot = col;
            for (int r = col + 1; r < 3; r++) {
                if (Math.abs(m[r][col]) > Math.abs(m[pivot][col])) {
                    pivot = r;
                }
            }
            double[] tmp = m[col];
            m[col] = m[pivot];
            m[pivot] = tmp;
            if (Math.abs(m[col][col]) < 1e-18) {
                return new double[]{0, 0, 0};
            }
            for (int r = 0; r < 3; r++) {
                if (r == col) {
                    continue;
                }
                double f = m[r][col] / m[col][col];
                for (int c = col; c <= 3; c++) {
                    m[r][c] -= f * m[col][c];
                }
            }
        }
        return new double[]{
                m[0][3] / m[0][0], m[1][3] / m[1][1], m[2][3] / m[2][2]};
    }
}
