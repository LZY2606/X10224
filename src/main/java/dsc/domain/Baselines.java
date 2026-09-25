package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Baseline candidates for one integration interval. Multiple plausible
 * baselines are always kept side by side; adopting one never deletes the
 * others. An interval containing no measured points yields a diagnostic,
 * never a fabricated baseline.
 */
public final class Baselines {

    public enum Kind { LINEAR_CHORD, LEFT_TANGENT, RIGHT_TANGENT, CUBIC_OUTER }

    public static final class Baseline {
        public final Kind kind;
        // y = c0 + c1*(T - Tref) [+ c2*(T-Tref)^2 + c3*(T-Tref)^3]
        public final double[] coeffs;
        public final double tref;
        public final String rationale;

        public Baseline(Kind kind, double[] coeffs, double tref, String rationale) {
            this.kind = kind;
            this.coeffs = coeffs;
            this.tref = tref;
            this.rationale = rationale;
        }

        public double at(double t) {
            double x = t - tref;
            double y = 0;
            double xp = 1;
            for (double c : coeffs) {
                y += c * xp;
                xp *= x;
            }
            return y;
        }
    }

    public static final class CandidateSet {
        public final List<Baseline> candidates = new ArrayList<>();
        public final List<String> diagnostics = new ArrayList<>();
        public boolean emptyInterval;
    }

    public static CandidateSet candidates(Curve c, double t0, double t1) {
        return candidates(c, t0, t1, 12);
    }

    public static CandidateSet candidates(Curve c, double t0, double t1, int wing) {
        CandidateSet set = new CandidateSet();
        List<Double> inT = new ArrayList<>();
        List<Double> inH = new ArrayList<>();
        List<Double> outT = new ArrayList<>();
        List<Double> outH = new ArrayList<>();
        for (int i = 0; i < c.size(); i++) {
            double t = c.tempC[i];
            if (t >= t0 && t <= t1) {
                inT.add(t);
                inH.add(c.heatMwPerMg[i]);
            } else {
                outT.add(t);
                outH.add(c.heatMwPerMg[i]);
            }
        }
        if (inT.isEmpty()) {
            set.emptyInterval = true;
            set.diagnostics.add("EMPTY_INTERVAL: no measured points inside [" + t0 + ", " + t1
                    + "] degC; no baseline fabricated");
            return set;
        }
        double tref = (t0 + t1) / 2.0;
        // 1. linear chord between interval edges (interpolated)
        double h0 = interp(c, t0);
        double h1 = interp(c, t1);
        double slope = (h1 - h0) / (t1 - t0);
        set.candidates.add(new Baseline(Kind.LINEAR_CHORD,
                new double[]{h0 + slope * (t0 - tref), slope}, tref,
                "straight chord between interval edges"));

        // 2./3. tangents fitted on the wings outside the interval
        double[] left = fitLine(wingWindow(c, t0, true, wing));
        double[] right = fitLine(wingWindow(c, t1, false, wing));
        if (left != null) {
            set.candidates.add(new Baseline(Kind.LEFT_TANGENT,
                    shift(left, tref), tref, "pre-event tangent extended across interval"));
        } else {
            set.diagnostics.add("LEFT_TANGENT_UNAVAILABLE: not enough pre-interval points");
        }
        if (right != null) {
            set.candidates.add(new Baseline(Kind.RIGHT_TANGENT,
                    shift(right, tref), tref, "post-event tangent extended across interval"));
        } else {
            set.diagnostics.add("RIGHT_TANGENT_UNAVAILABLE: not enough post-interval points");
        }

        // 4. cubic through points outside the interval
        if (outT.size() >= 6) {
            double[] cubic = fitPoly(outT, outH, tref, 3);
            if (cubic != null) {
                set.candidates.add(new Baseline(Kind.CUBIC_OUTER, cubic, tref,
                        "cubic fitted to points outside the interval"));
            }
        } else {
            set.diagnostics.add("CUBIC_OUTER_UNAVAILABLE: only " + outT.size()
                    + " points outside interval");
        }
        return set;
    }

    private static double[] wingWindow(Curve c, double edge, boolean before, int n) {
        double[] t = new double[n];
        double[] h = new double[n];
        int found = 0;
        if (before) {
            for (int i = c.size() - 1; i >= 0 && found < n; i--) {
                if (c.tempC[i] < edge) {
                    t[found] = c.tempC[i];
                    h[found] = c.heatMwPerMg[i];
                    found++;
                }
            }
        } else {
            for (int i = 0; i < c.size() && found < n; i++) {
                if (c.tempC[i] > edge) {
                    t[found] = c.tempC[i];
                    h[found] = c.heatMwPerMg[i];
                    found++;
                }
            }
        }
        if (found < 3) return null;
        double[] out = new double[found * 2];
        for (int i = 0; i < found; i++) {
            out[i] = t[i];
            out[found + i] = h[i];
        }
        return out;
    }

    private static double[] fitLine(double[] th) {
        if (th == null) return null;
        int n = th.length / 2;
        double st = 0, sh = 0, stt = 0, sth = 0;
        for (int i = 0; i < n; i++) {
            st += th[i];
            sh += th[n + i];
            stt += th[i] * th[i];
            sth += th[i] * th[n + i];
        }
        double det = n * stt - st * st;
        if (Math.abs(det) < 1e-12) return null;
        double b = (n * sth - st * sh) / det;
        double a = (sh - b * st) / n;
        return new double[]{a, b}; // y = a + b*T
    }

    private static double[] shift(double[] ab, double tref) {
        // y = a + b*T  ->  y = (a + b*tref) + b*(T - tref)
        return new double[]{ab[0] + ab[1] * tref, ab[1]};
    }

    /** Least-squares polynomial via normal equations (small degrees only). */
    static double[] fitPoly(List<Double> xs, List<Double> ys, double tref, int degree) {
        int m = degree + 1;
        double[][] ata = new double[m][m];
        double[] aty = new double[m];
        for (int k = 0; k < xs.size(); k++) {
            double x = xs.get(k) - tref;
            double[] xp = new double[2 * m - 1];
            xp[0] = 1;
            for (int i = 1; i < xp.length; i++) xp[i] = xp[i - 1] * x;
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < m; j++) ata[i][j] += xp[i + j];
                aty[i] += xp[i] * ys.get(k);
            }
        }
        return solve(ata, aty);
    }

    static double[] solve(double[][] a, double[] b) {
        int n = b.length;
        double[][] m = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, m[i], 0, n);
            m[i][n] = b[i];
        }
        for (int col = 0; col < n; col++) {
            int piv = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(m[r][col]) > Math.abs(m[piv][col])) piv = r;
            }
            if (Math.abs(m[piv][col]) < 1e-14) return null;
            double[] tmp = m[col];
            m[col] = m[piv];
            m[piv] = tmp;
            for (int r = 0; r < n; r++) {
                if (r == col) continue;
                double f = m[r][col] / m[col][col];
                for (int cc = col; cc <= n; cc++) m[r][cc] -= f * m[col][cc];
            }
        }
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = m[i][n] / m[i][i];
        return x;
    }

    /** Linear interpolation of the curve at temperature t (grid may be non-uniform). */
    public static double interp(Curve c, double t) {
        double[] xs = c.tempC;
        if (t <= xs[0]) return c.heatMwPerMg[0];
        if (t >= xs[xs.length - 1]) return c.heatMwPerMg[xs.length - 1];
        for (int i = 1; i < xs.length; i++) {
            if (xs[i] >= t) {
                double f = (t - xs[i - 1]) / (xs[i] - xs[i - 1]);
                return c.heatMwPerMg[i - 1] + f * (c.heatMwPerMg[i] - c.heatMwPerMg[i - 1]);
            }
        }
        return c.heatMwPerMg[xs.length - 1];
    }
}
