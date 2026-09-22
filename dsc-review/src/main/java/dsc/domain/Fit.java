package dsc.domain;

/** Least-squares fit and residual helpers for DSC baselines/tangents. */
public final class Fit {

    private Fit() {
    }

    public record Line(double slope, double intercept) {
        public double y(double x) {
            return slope * x + intercept;
        }
    }

    public static Line linear(double[] x, double[] y) {
        int n = x.length;
        if (n < 2) {
            double v = n == 1 ? y[0] : 0.0;
            return new Line(0.0, v);
        }
        double sx = 0.0;
        double sy = 0.0;
        double sxx = 0.0;
        double sxy = 0.0;
        for (int i = 0; i < n; i++) {
            sx += x[i];
            sy += y[i];
            sxx += x[i] * x[i];
            sxy += x[i] * y[i];
        }
        double denom = n * sxx - sx * sx;
        if (Math.abs(denom) < 1e-18) {
            return new Line(0.0, sy / n);
        }
        double slope = (n * sxy - sx * sy) / denom;
        double intercept = (sy - slope * sx) / n;
        return new Line(slope, intercept);
    }

    public static double rmsResidual(double[] x, double[] y, Line line) {
        double sum = 0.0;
        for (int i = 0; i < x.length; i++) {
            double d = y[i] - line.y(x[i]);
            sum += d * d;
        }
        return Math.sqrt(sum / Math.max(1, x.length));
    }
}
