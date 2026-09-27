package dsc.core;

/** Ordinary least-squares line y = slope*x + intercept. */
public final class LinearFit {
    public final double slope;
    public final double intercept;
    public final double r2;
    public final int n;

    private LinearFit(double slope, double intercept, double r2, int n) {
        this.slope = slope;
        this.intercept = intercept;
        this.r2 = r2;
        this.n = n;
    }

    public static LinearFit fit(double[] xs, double[] ys, int from, int to) {
        int n = to - from + 1;
        if (n < 2) {
            throw new IllegalArgumentException("need at least 2 points for a fit, got " + n);
        }
        double sx = 0, sy = 0, sxx = 0, sxy = 0, syy = 0;
        for (int i = from; i <= to; i++) {
            double x = xs[i];
            double y = ys[i];
            sx += x;
            sy += y;
            sxx += x * x;
            sxy += x * y;
            syy += y * y;
        }
        double denom = n * sxx - sx * sx;
        double slope;
        double intercept;
        double r2;
        if (Math.abs(denom) < 1e-18) {
            slope = 0;
            intercept = sy / n;
            r2 = 0;
        } else {
            slope = (n * sxy - sx * sy) / denom;
            intercept = (sy - slope * sx) / n;
            double ssTot = syy - sy * sy / n;
            if (ssTot <= 0) {
                r2 = 1.0;
            } else {
                double ssRes = 0;
                for (int i = from; i <= to; i++) {
                    double r = ys[i] - (slope * xs[i] + intercept);
                    ssRes += r * r;
                }
                r2 = Math.max(0, 1 - ssRes / ssTot);
            }
        }
        return new LinearFit(slope, intercept, r2, n);
    }
}
