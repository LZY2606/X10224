package dsc.core;

/** Ordinary least-squares line y = a + b*x. */
public final class LinearFit {

    public final double intercept;
    public final double slope;
    public final double rmsResidual;

    private LinearFit(double intercept, double slope, double rmsResidual) {
        this.intercept = intercept;
        this.slope = slope;
        this.rmsResidual = rmsResidual;
    }

    public double at(double x) { return intercept + slope * x; }

    public static LinearFit of(double[] x, double[] y) {
        if (x.length != y.length || x.length < 2) {
            throw new IllegalArgumentException("need at least two points");
        }
        int n = x.length;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = 0; i < n; i++) {
            sx += x[i]; sy += y[i]; sxx += x[i] * x[i]; sxy += x[i] * y[i];
        }
        double den = n * sxx - sx * sx;
        if (Math.abs(den) < 1e-18) {
            throw new IllegalArgumentException("degenerate x values for line fit");
        }
        double slope = (n * sxy - sx * sy) / den;
        double intercept = (sy - slope * sx) / n;
        double ss = 0;
        for (int i = 0; i < n; i++) {
            double r = y[i] - (intercept + slope * x[i]);
            ss += r * r;
        }
        return new LinearFit(intercept, slope, Math.sqrt(ss / n));
    }
}
