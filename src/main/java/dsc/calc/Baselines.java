package dsc.calc;

import com.google.gson.JsonObject;
import dsc.model.J;

import java.util.ArrayList;
import java.util.List;

/**
 * Baseline construction for a selected interval.
 *
 * <p>Every reasonable construction is kept as a parallel candidate:
 * <ol>
 *   <li>{@code linear-flanks}: least-squares line through the two user-dragged flank
 *       windows (standard DSC straight baseline).</li>
 *   <li>{@code cubic-flanks}: least-squares cubic that bends with background curvature.</li>
 *   <li>{@code tangent}: the reviewer's separately archived tangent line
 *       (slope/intercept), not guessed from the curve.</li>
 * </ol>
 * Candidate differences are retained after acceptance; choosing one never deletes others.
 */
public final class Baselines {

    private Baselines() {}

    public static final class Candidate {
        public final String method;
        public final double[] values;
        public final double rmsResidual;
        public final JsonObject params;

        Candidate(String method, double[] values, double rms, JsonObject params) {
            this.method = method;
            this.values = values;
            this.rmsResidual = rms;
            this.params = params;
        }

        JsonObject toJson() {
            JsonObject o = J.obj();
            J.put(o, "method", method);
            JsonArrayWrapper.put(o, "values", values);
            J.put(o, "rmsResidual", rmsResidual);
            J.put(o, "params", params);
            return o;
        }
    }

    /**
     * @param leftFrom/leftTo   flank window indices (inclusive), dense array basis
     * @param rightFrom/rightTo
     */
    public static List<Candidate> build(Series s, int regionFrom, int regionTo,
                                        int leftFrom, int leftTo,
                                        int rightFrom, int rightTo,
                                        JsonObject tangentDoc) {
        List<Candidate> out = new ArrayList<>();
        int n = regionTo - regionFrom + 1;
        if (n <= 0) return out;

        List<double[]> fitPts = new ArrayList<>();
        addFlank(s, leftFrom, leftTo, fitPts);
        addFlank(s, rightFrom, rightTo, fitPts);
        if (fitPts.size() < 2) return out;

        double[] line = fitPoly(fitPts, 1);
        Candidate linear = evaluate(s, regionFrom, regionTo, "linear-flanks", line);
        out.add(linear);

        if (fitPts.size() >= 4) {
            double[] cubic = fitPoly(fitPts, 3);
            out.add(evaluate(s, regionFrom, regionTo, "cubic-flanks", cubic));
        }

        if (tangentDoc != null && tangentDoc.has("slope") && tangentDoc.has("intercept")) {
            double m = tangentDoc.get("slope").getAsDouble();
            double b = tangentDoc.get("intercept").getAsDouble();
            double[] vals = new double[n];
            for (int i = 0; i < n; i++) vals[i] = m * s.t[regionFrom + i] + b;
            JsonObject p = J.obj();
            J.put(p, "slope", m);
            J.put(p, "intercept", b);
            J.put(p, "tangentSourceId", J.str(tangentDoc, "sourceId", ""));
            double rms = rms(vals, residual(s, regionFrom, regionTo, vals));
            out.add(new Candidate("tangent", vals, rms, p));
        }
        return out;
    }

    private static void addFlank(Series s, int from, int to, List<double[]> pts) {
        if (from > to) return;
        for (int i = Math.max(0, from); i <= Math.min(s.t.length - 1, to); i++) {
            pts.add(new double[]{s.t[i], s.hf[i]});
        }
    }

    private static Candidate evaluate(Series s, int from, int to, String method, double[] coef) {
        int n = to - from + 1;
        double[] vals = new double[n];
        for (int i = 0; i < n; i++) vals[i] = poly(coef, s.t[from + i]);
        JsonObject p = J.obj();
        JsonArrayWrapper coefs = new JsonArrayWrapper();
        for (double c : coef) coefs.add(c);
        J.put(p, "coef", coefs.build());
        return new Candidate(method, vals, rms(vals, residual(s, from, to, vals)), p);
    }

    private static double[] residual(Series s, int from, int to, double[] vals) {
        double[] r = new double[vals.length];
        for (int i = 0; i < vals.length; i++) r[i] = s.hf[from + i] - vals[i];
        return r;
    }

    private static double rms(double[] vals, double[] residual) {
        double sum = 0;
        for (double v : residual) sum += v * v;
        return Math.sqrt(sum / residual.length);
    }

    /** Normal-equation polynomial fit, degree d, ascending power coefficients. */
    static double[] fitPoly(List<double[]> pts, int degree) {
        int m = degree + 1;
        double[][] a = new double[m][m];
        double[] y = new double[m];
        for (double[] pt : pts) {
            double x = pt[0], yy = pt[1];
            double[] xp = new double[2 * degree + 1];
            xp[0] = 1;
            for (int k = 1; k < xp.length; k++) xp[k] = xp[k - 1] * x;
            for (int i = 0; i < m; i++) {
                y[i] += yy * xp[i];
                for (int j = 0; j < m; j++) a[i][j] += xp[i + j];
            }
        }
        return solve(a, y);
    }

    private static double poly(double[] c, double x) {
        double v = 0, xp = 1;
        for (double coef : c) { v += coef * xp; xp *= x; }
        return v;
    }

    private static double[] solve(double[][] a, double[] b) {
        int n = b.length;
        for (int p = 0; p < n; p++) {
            int max = p;
            for (int i = p + 1; i < n; i++) if (Math.abs(a[i][p]) > Math.abs(a[max][p])) max = i;
            double[] tmp = a[p]; a[p] = a[max]; a[max] = tmp;
            double t = b[p]; b[p] = b[max]; b[max] = t;
            for (int i = p + 1; i < n; i++) {
                double f = a[i][p] / a[p][p];
                b[i] -= f * b[p];
                for (int j = p; j < n; j++) a[i][j] -= f * a[p][j];
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = b[i];
            for (int j = i + 1; j < n; j++) sum -= a[i][j] * x[j];
            x[i] = sum / a[i][i];
        }
        return x;
    }

    private static final class JsonArrayWrapper {
        private final com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        static void put(JsonObject o, String k, double[] v) {
            JsonArrayWrapper w = new JsonArrayWrapper();
            for (double d : v) w.add(d);
            J.put(o, k, w.build());
        }
        void add(double d) { a.add(d); }
        com.google.gson.JsonArray build() { return a; }
    }
}
