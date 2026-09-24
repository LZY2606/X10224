package io.dsc.engine;

/** Least-squares fit y = slope*x + intercept, with RMS residual. */
public record LinearFit(double slope, double intercept, double rms) {

    public static LinearFit of(double[] xs, double[] ys) {
        if (xs.length != ys.length || xs.length < 2) {
            throw new IllegalArgumentException("fit needs >=2 paired points");
        }
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = xs.length;
        for (int i = 0; i < n; i++) {
            sx += xs[i];
            sy += ys[i];
            sxx += xs[i] * xs[i];
            sxy += xs[i] * ys[i];
        }
        double denom = n * sxx - sx * sx;
        double slope = denom == 0 ? 0 : (n * sxy - sx * sy) / denom;
        double intercept = (sy - slope * sx) / n;
        double sumSq = 0;
        for (int i = 0; i < n; i++) {
            double r = ys[i] - (slope * xs[i] + intercept);
            sumSq += r * r;
        }
        return new LinearFit(slope, intercept, Math.sqrt(sumSq / n));
    }

    public double predict(double x) {
        return slope * x + intercept;
    }
}
