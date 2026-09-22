package dsc.derive;

/** Ordinary least squares line y = a + b*x with residual RMS. */
public record Fit(double intercept, double slope, double rms, int n) {
    public double predict(double x) {
        return intercept + slope * x;
    }

    public static Fit leastSquares(double[] xs, double[] ys, int from, int to) {
        int n = to - from;
        double sumX = 0;
        double sumY = 0;
        for (int i = from; i < to; i++) {
            sumX += xs[i];
            sumY += ys[i];
        }
        double meanX = sumX / n;
        double meanY = sumY / n;
        double cov = 0;
        double var = 0;
        for (int i = from; i < to; i++) {
            double dx = xs[i] - meanX;
            cov += dx * (ys[i] - meanY);
            var += dx * dx;
        }
        double slope = var == 0 ? 0 : cov / var;
        double intercept = meanY - slope * meanX;
        double sq = 0;
        for (int i = from; i < to; i++) {
            double residual = ys[i] - (intercept + slope * xs[i]);
            sq += residual * residual;
        }
        return new Fit(intercept, slope, Math.sqrt(sq / n), n);
    }
}
