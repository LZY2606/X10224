package dsc.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Baseline fitting.
 *
 * <ul>
 *   <li>{@code linear}: weighted least squares line through the two tangent windows
 *       (user selected tangents) — valid for crystallization / melting.</li>
 *   <li>{@code step}: logistic step + line, fitted for glass transitions.</li>
 * </ul>
 *
 * Both candidates may be legitimate for the same interval; parallel candidates are
 * kept as separate versions rather than collapsed into one "winner".
 */
public final class BaselineFitter {

    private BaselineFitter() {}

    public static BaselineModel linear(WorkingCurve curve, Interval interval, TangentSpec spec) {
        int[] in = indicesIn(curve, interval);
        List<int[]> windows = tangentWindows(curve, in, spec);
        List<double[]> pts = new ArrayList<>();
        for (int[] win : windows) {
            for (int idx : win) {
                pts.add(new double[]{curve.t(idx), curve.hf(idx)});
            }
        }
        double[] ab = weightedLeastSquares(pts);
        double[] vals = new double[in.length];
        double[] idxs = new double[in.length];
        double se = 0;
        int counted = 0;
        for (int k = 0; k < in.length; k++) {
            int i = in[k];
            idxs[k] = i;
            vals[k] = ab[0] * curve.t(i) + ab[1];
            boolean inWindow = false;
            for (int[] win : windows) {
                for (int w : win) {
                    if (w == i) {
                        inWindow = true;
                    }
                }
            }
            if (inWindow) {
                se += Math.pow(curve.hf(i) - vals[k], 2);
                counted++;
            }
        }
        double rmse = counted > 0 ? Math.sqrt(se / counted) : Double.NaN;
        return new BaselineModel("linear", idxs, vals, rmse,
                new double[]{ab[0], ab[1]}, "rules/v1#linear-tangent");
    }

    public static BaselineModel step(WorkingCurve curve, Interval interval) {
        int[] in = indicesIn(curve, interval);
        double[] t = new double[in.length];
        double[] y = new double[in.length];
        double[] idxs = new double[in.length];
        for (int k = 0; k < in.length; k++) {
            t[k] = curve.t(in[k]);
            y[k] = curve.hf(in[k]);
            idxs[k] = in[k];
        }
        double[] p = fitLogistic(t, y);
        double[] vals = new double[in.length];
        double se = 0;
        for (int k = 0; k < in.length; k++) {
            vals[k] = evalLogistic(p, t[k]);
            se += Math.pow(y[k] - vals[k], 2);
        }
        double rmse = in.length > 0 ? Math.sqrt(se / in.length) : Double.NaN;
        return new BaselineModel("step", idxs, vals, rmse, p, "rules/v1#step-sigmoid");
    }

    /** Evaluate baseline value at raw point index. */
    public static double at(BaselineModel model, int pointIndex) {
        double[] idxs = model.indexValues();
        double[] vals = model.values();
        if (idxs.length == 0) {
            return Double.NaN;
        }
        if (pointIndex <= idxs[0]) {
            return vals[0];
        }
        int last = idxs.length - 1;
        if (pointIndex >= idxs[last]) {
            return vals[last];
        }
        for (int k = 1; k <= last; k++) {
            if (pointIndex <= idxs[k]) {
                double frac = (pointIndex - idxs[k - 1]) / Math.max(1e-12, idxs[k] - idxs[k - 1]);
                return vals[k - 1] + frac * (vals[k] - vals[k - 1]);
            }
        }
        return vals[last];
    }

    /** Glass transition temperature = logistic midpoint. */
    public static double midpointK(BaselineModel step) {
        return step.params().length >= 5 ? step.params()[3] : Double.NaN;
    }

    /** Delta Cp in W/K (height of the fitted step). Divide by scan rate & mass upstream. */
    public static double stepHeightW(BaselineModel step) {
        return step.params().length >= 5 ? step.params()[0] : Double.NaN;
    }

    private static int[] indicesIn(WorkingCurve curve, Interval interval) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < curve.length(); i++) {
            if (interval.covers(curve.t(i))) {
                list.add(i);
            }
        }
        int[] out = new int[list.size()];
        for (int k = 0; k < out.length; k++) {
            out[k] = list.get(k);
        }
        return out;
    }

    private static List<int[]> tangentWindows(WorkingCurve curve, int[] in, TangentSpec spec) {
        List<int[]> result = new ArrayList<>();
        if (spec != null && spec.valid()) {
            result.add(indicesBetween(curve, spec.leftLoK(), spec.leftHiK()));
            result.add(indicesBetween(curve, spec.rightLoK(), spec.rightHiK()));
        }
        boolean empty = result.isEmpty()
                || result.stream().anyMatch(a -> a.length == 0);
        if (empty) {
            // Fall back to outer quartiles of the interval, marked in basis.
            result.clear();
            int q = Math.max(2, in.length / 5);
            int[] l = new int[Math.min(q, in.length)];
            int[] r = new int[Math.min(q, in.length)];
            for (int k = 0; k < l.length; k++) {
                l[k] = in[k];
                r[k] = in[in.length - 1 - k];
            }
            result.add(l);
            result.add(r);
        }
        return result;
    }

    private static int[] indicesBetween(WorkingCurve curve, double loK, double hiK) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < curve.length(); i++) {
            if (curve.t(i) >= loK && curve.t(i) <= hiK) {
                list.add(i);
            }
        }
        int[] out = new int[list.size()];
        for (int k = 0; k < out.length; k++) {
            out[k] = list.get(k);
        }
        return out;
    }

    private static double[] weightedLeastSquares(List<double[]> pts) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0, wsum = 0;
        for (double[] p : pts) {
            double w = 1;
            sx += w * p[0];
            sy += w * p[1];
            sxx += w * p[0] * p[0];
            sxy += w * p[0] * p[1];
            wsum += w;
        }
        double det = wsum * sxx - sx * sx;
        if (Math.abs(det) < 1e-18) {
            return new double[]{0, pts.isEmpty() ? 0 : pts.get(0)[1]};
        }
        double a = (wsum * sxy - sx * sy) / det;
        double b = (sxx * sy - sx * sxy) / det;
        return new double[]{a, b};
    }

    /**
     * Logistic model: y = L/(1+exp(-k*(t-t0))) + a*t + b
     * params = {L, k, a, t0, b}. Coordinate descent on a grid followed by local
     * Gauss-Newton polishing — deterministic and dependency free.
     */
    private static double[] fitLogistic(double[] t, double[] y) {
        double tMin = t[0];
        double tMax = t[t.length - 1];
        double yMin = Double.POSITIVE_INFINITY;
        double yMax = Double.NEGATIVE_INFINITY;
        for (double v : y) {
            yMin = Math.min(yMin, v);
            yMax = Math.max(yMax, v);
        }
        double span = Math.max(yMax - yMin, 1e-9);
        double trange = Math.max(tMax - tMin, 1e-9);

        double[] best = null;
        double bestSse = Double.POSITIVE_INFINITY;
        for (int gi = 0; gi <= 24; gi++) {
            double t0 = tMin + trange * gi / 24.0;
            for (int gs = 1; gs <= 10; gs++) {
                double k = 2.0 / trange * gs;
                double[] p = polish(t, y, new double[]{span, k, 0, t0, yMin});
                double sse = sse(t, y, p);
                if (sse < bestSse) {
                    bestSse = sse;
                    best = p;
                }
            }
        }
        return best;
    }

    private static double[] polish(double[] t, double[] y, double[] start) {
        double[] p = start.clone();
        double lambda = 1e-3;
        double prev = sse(t, y, p);
        for (int iter = 0; iter < 40; iter++) {
            double sse0 = sse(t, y, p);
            double[][] jtj = new double[5][5];
            double[] jtr = new double[5];
            for (int i = 0; i < t.length; i++) {
                double e = Math.exp(-p[1] * (t[i] - p[3]));
                double g = 1.0 / (1 + e);
                double dg = g * (1 - g);
                double r = y[i] - evalLogistic(p, t[i]);
                double[] j = {g, p[0] * dg * (t[i] - p[3]), t[i], p[0] * dg * p[1], 1};
                for (int a = 0; a < 5; a++) {
                    jtr[a] += j[a] * r;
                    for (int b = 0; b < 5; b++) {
                        jtj[a][b] += j[a] * j[b];
                    }
                }
            }
            double[] next = null;
            for (int attempt = 0; attempt < 8; attempt++) {
                double[][] m = new double[5][5];
                for (int a = 0; a < 5; a++) {
                    System.arraycopy(jtj[a], 0, m[a], 0, 5);
                    m[a][a] += lambda * Math.max(1e-12, jtj[a][a]);
                }
                double[] step = solve5(m, jtr);
                if (step == null) {
                    break;
                }
                double[] cand = new double[5];
                for (int a = 0; a < 5; a++) {
                    cand[a] = p[a] + step[a];
                }
                double sseC = sse(t, y, cand);
                if (sseC < sse0) {
                    next = cand;
                    lambda = Math.max(1e-6, lambda / 3);
                    break;
                }
                lambda *= 4;
            }
            if (next == null) {
                break;
            }
            p = next;
            if (Math.abs(prev - sse0) < 1e-12 * Math.max(1, sse0)) {
                break;
            }
            prev = sse0;
        }
        return p;
    }

    private static double[] solve5(double[][] a, double[] b) {
        double[][] m = new double[5][6];
        for (int i = 0; i < 5; i++) {
            System.arraycopy(a[i], 0, m[i], 0, 5);
            m[i][5] = b[i];
        }
        for (int col = 0; col < 5; col++) {
            int piv = col;
            for (int r = col + 1; r < 5; r++) {
                if (Math.abs(m[r][col]) > Math.abs(m[piv][col])) {
                    piv = r;
                }
            }
            double[] tmp = m[col];
            m[col] = m[piv];
            m[piv] = tmp;
            if (Math.abs(m[col][col]) < 1e-15) {
                return null;
            }
            for (int r = 0; r < 5; r++) {
                if (r == col) {
                    continue;
                }
                double f = m[r][col] / m[col][col];
                for (int c = col; c <= 5; c++) {
                    m[r][c] -= f * m[col][c];
                }
            }
        }
        double[] x = new double[5];
        for (int i = 0; i < 5; i++) {
            x[i] = m[i][5] / m[i][i];
        }
        return x;
    }

    private static double evalLogistic(double[] p, double t) {
        double e = Math.exp(-p[1] * (t - p[3]));
        return p[0] / (1 + e) + p[2] * t + p[4];
    }

    private static double sse(double[] t, double[] y, double[] p) {
        double s = 0;
        for (int i = 0; i < t.length; i++) {
            double r = y[i] - evalLogistic(p, t[i]);
            if (!Double.isFinite(r)) {
                return Double.POSITIVE_INFINITY;
            }
            s += r * r;
        }
        return s;
    }
}
