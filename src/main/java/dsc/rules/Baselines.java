package dsc.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Baseline candidate generator. Several legitimate baselines are always produced and
 * retained; none is collapsed into a single answer. Candidates are ranked by anchor
 * residual, and "reasonable" means within a fixed factor of the best anchor error.
 */
public final class Baselines {

    public static final String RULE_VERSION = "baseline/v4";

    private Baselines() {
    }

    /** One candidate curve sampled at the input temperatures. */
    public record Candidate(String kind, double[] values, double anchorRmse,
                            double[] parameters, boolean reasonable, String note) {
    }

    /**
     * @param t      calibrated temperature, strictly increasing
     * @param y      oriented heat flow (exothermic-up)
     * @param userLine optional two-point tangent {x0,y0,x1,y1}, or null
     */
    public static List<Candidate> generate(double[] t, double[] y, double[] userLine) {
        int n = t.length;
        List<Candidate> out = new ArrayList<>();
        if (n < 4) {
            return out;
        }
        double span = t[n - 1] - t[0];
        double edge = Math.max(span * 0.18, (t[1] - t[0]) * 2);

        List<Integer> anchors = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (t[i] <= t[0] + edge || t[i] >= t[n - 1] - edge) {
                anchors.add(i);
            }
        }

        // 1) straight line through the two outer anchor windows
        double[] linear = leastSquares(t, y, anchors, 1);
        double[] linVal = evalPoly(t, linear);
        double linRmse = rmse(t, y, linVal, anchors);
        out.add(new Candidate("LINEAR_ENDS", linVal, linRmse, linear, true,
                "least squares line through outer 18% anchor windows"));

        // 2) sigmoid step joining left/right asymptotes (glass transition shape)
        Candidate sigmoid = fitSigmoid(t, y, edge);
        if (sigmoid != null) {
            out.add(sigmoid);
        }

        // 3) cubic anchored to the outer windows (broad curvature / overlap shoulders)
        if (anchors.size() >= 4 && span > 0) {
            double[] cubic = leastSquares(t, y, anchors, 3);
            double[] cubVal = evalPoly(t, cubic);
            double cubRmse = rmse(t, y, cubVal, anchors);
            out.add(new Candidate("CUBIC_ANCHOR", cubVal, cubRmse, cubic, true,
                    "cubic least squares on outer anchor windows"));
        }

        if (userLine != null) {
            double slope = (userLine[3] - userLine[1]) / (userLine[2] - userLine[0]);
            double intercept = userLine[1] - slope * userLine[0];
            double[] usrVal = evalPoly(t, new double[] {intercept, slope});
            double usrRmse = rmse(t, y, usrVal, anchors);
            out.add(new Candidate("USER_TANGENT", usrVal, usrRmse,
                    new double[] {intercept, slope}, true,
                    "user selected tangent (archived separately)"));
        }

        double best = out.stream().mapToDouble(Candidate::anchorRmse).min().orElse(0.0);
        List<Candidate> ranked = new ArrayList<>();
        for (Candidate c : out) {
            boolean reasonable = c.anchorRmse <= best * 2.0 + 1e-9;
            ranked.add(new Candidate(c.kind(), c.values(), c.anchorRmse(), c.parameters(),
                    reasonable, c.note()));
        }
        ranked.sort((a, b) -> Double.compare(a.anchorRmse(), b.anchorRmse()));
        return ranked;
    }

    private static Candidate fitSigmoid(double[] t, double[] y, double edge) {
        double bestSse = Double.POSITIVE_INFINITY;
        double bestT0 = 0;
        double bestW = 1;
        double[] bestBeta = null;
        double[] widths = {0.5, 1.0, 2.0, 4.0, 8.0};
        for (int gi = 0; gi <= 24; gi++) {
            double t0 = t[0] + (t[t.length - 1] - t[0]) * gi / 24.0;
            for (double w : widths) {
                double[][] x = new double[t.length][4];
                for (int i = 0; i < t.length; i++) {
                    double s = 1.0 / (1.0 + Math.exp(-(t[i] - t0) / w));
                    x[i][0] = 1 - s;
                    x[i][1] = (1 - s) * t[i];
                    x[i][2] = s;
                    x[i][3] = s * t[i];
                }
                double[] beta = lstsq(x, y);
                if (beta == null) {
                    continue;
                }
                double sse = 0;
                for (int i = 0; i < t.length; i++) {
                    double v = x[i][0] * beta[0] + x[i][1] * beta[1]
                            + x[i][2] * beta[2] + x[i][3] * beta[3];
                    sse += (v - y[i]) * (v - y[i]);
                }
                if (sse < bestSse) {
                    bestSse = sse;
                    bestT0 = t0;
                    bestW = w;
                    bestBeta = beta;
                }
            }
        }
        if (bestBeta == null) {
            return null;
        }
        double[] vals = new double[t.length];
        List<Integer> anchors = new ArrayList<>();
        for (int i = 0; i < t.length; i++) {
            double s = 1.0 / (1.0 + Math.exp(-(t[i] - bestT0) / bestW));
            vals[i] = bestBeta[0] * (1 - s) + bestBeta[1] * (1 - s) * t[i]
                    + bestBeta[2] * s + bestBeta[3] * s * t[i];
            if (t[i] <= t[0] + edge || t[i] >= t[t.length - 1] - edge) {
                anchors.add(i);
            }
        }
        return new Candidate("SIGMOID_STEP", vals, rmse(t, y, vals, anchors),
                new double[] {bestBeta[0], bestBeta[1], bestBeta[2], bestBeta[3], bestT0, bestW},
                true, "logistic step joining pre/post asymptotes (Tg shape)");
    }

    private static double[] leastSquares(double[] t, double[] y, List<Integer> idx, int degree) {
        double[][] x = new double[idx.size()][degree + 1];
        double[] yy = new double[idx.size()];
        for (int r = 0; r < idx.size(); r++) {
            int i = idx.get(r);
            for (int d = 0; d <= degree; d++) {
                x[r][d] = Math.pow(t[i], d);
            }
            yy[r] = y[i];
        }
        return lstsq(x, yy);
    }

    /** Normal equations least squares (small fixed systems). */
    static double[] lstsq(double[][] x, double[] y) {
        int p = x[0].length;
        double[][] a = new double[p][p];
        double[] b = new double[p];
        for (int r = 0; r < x.length; r++) {
            for (int i = 0; i < p; i++) {
                b[i] += x[r][i] * y[r];
                for (int j = 0; j < p; j++) {
                    a[i][j] += x[r][i] * x[r][j];
                }
            }
        }
        return gaussian(a, b);
    }

    private static double[] gaussian(double[][] a, double[] b) {
        int n = b.length;
        double[][] m = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, m[i], 0, n);
            m[i][n] = b[i];
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(m[r][col]) > Math.abs(m[pivot][col])) {
                    pivot = r;
                }
            }
            double[] tmp = m[pivot];
            m[pivot] = m[col];
            m[col] = tmp;
            if (Math.abs(m[col][col]) < 1e-12) {
                return null;
            }
            for (int r = 0; r < n; r++) {
                if (r == col) {
                    continue;
                }
                double f = m[r][col] / m[col][col];
                for (int c = col; c <= n; c++) {
                    m[r][c] -= f * m[col][c];
                }
            }
        }
        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = m[i][n] / m[i][i];
        }
        return x;
    }

    private static double[] evalPoly(double[] t, double[] beta) {
        double[] v = new double[t.length];
        for (int i = 0; i < t.length; i++) {
            double acc = 0;
            double tn = 1;
            for (double b : beta) {
                acc += b * tn;
                tn *= t[i];
            }
            v[i] = acc;
        }
        return v;
    }

    private static double rmse(double[] t, double[] y, double[] fit, List<Integer> idx) {
        double sse = 0;
        for (int i : idx) {
            double d = fit[i] - y[i];
            sse += d * d;
        }
        return Math.sqrt(sse / idx.size());
    }

    static double[] copyOf(double[] v) {
        return Arrays.copyOf(v, v.length);
    }
}
