package org.dscreview.engine;

/** Least-squares line y = a + b*x with standard error of the slope. */
public record LinearFit(double intercept, double slope, double slopeStdErr, int n) {
    public static LinearFit fit(double[] x, double[] y, int[] idx, int lo, int hi) {
        int m = hi - lo + 1;
        if (m < 2) return new LinearFit(idx.length == 0 ? 0 : y[idx[0]], 0, Double.NaN, m);
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int k = lo; k <= hi; k++) {
            int i = idx[k];
            sx += x[i]; sy += y[i]; sxx += x[i] * x[i]; sxy += x[i] * y[i];
        }
        double mx = sx / m, my = sy / m;
        double sxx2 = 0, sxy2 = 0;
        for (int k = lo; k <= hi; k++) {
            int i = idx[k];
            sxx2 += (x[i] - mx) * (x[i] - mx);
            sxy2 += (x[i] - mx) * (y[i] - y[i]);
        }
        double sxyC = sxy - sx * sy / m;
        double sxxC = sxx - sx * sx / m;
        double b = sxxC == 0 ? 0 : sxyC / sxxC;
        double a = my - b * mx;
        double ssr = 0;
        for (int k = lo; k <= hi; k++) {
            int i = idx[k];
            double r = y[i] - (a + b * x[i]);
            ssr += r * r;
        }
        double se = m > 2 && sxxC > 0 ? Math.sqrt((ssr / (m - 2)) / sxxC) : Double.NaN;
        return new LinearFit(a, b, se, m);
    }
}
