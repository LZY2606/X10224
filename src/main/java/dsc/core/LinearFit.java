package dsc.core;

/** 最小二乘直线 y = slope*x + intercept（对好点窗口）。 */
public final class LinearFit {
    public final double slope;
    public final double intercept;
    public final int pointsUsed;

    private LinearFit(double slope, double intercept, int pointsUsed) {
        this.slope = slope;
        this.intercept = intercept;
        this.pointsUsed = pointsUsed;
    }

    public double at(double x) {
        return slope * x + intercept;
    }

    public static LinearFit fit(double[] x, double[] y, boolean[] good, int from, int to) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = 0;
        for (int i = from; i <= to && i < x.length; i++) {
            if (i < 0 || !good[i]) {
                continue;
            }
            sx += x[i];
            sy += y[i];
            sxx += x[i] * x[i];
            sxy += x[i] * y[i];
            n++;
        }
        if (n < 2) {
            return new LinearFit(Double.NaN, Double.NaN, n);
        }
        double det = n * sxx - sx * sx;
        if (det == 0) {
            return new LinearFit(Double.NaN, Double.NaN, n);
        }
        double slope = (n * sxy - sx * sy) / det;
        double intercept = (sy - slope * sx) / n;
        return new LinearFit(slope, intercept, n);
    }
}
