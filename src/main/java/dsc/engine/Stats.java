package dsc.engine;

import java.util.Arrays;

public final class Stats {
    private Stats() {
    }

    public static double median(double[] xs) {
        double[] copy = xs.clone();
        Arrays.sort(copy);
        int n = copy.length;
        if (n == 0) {
            return Double.NaN;
        }
        return n % 2 == 1 ? copy[n / 2] : 0.5 * (copy[n / 2 - 1] + copy[n / 2]);
    }

    public static double slope(double x1, double y1, double x2, double y2) {
        if (x2 == x1) {
            return 0;
        }
        return (y2 - y1) / (x2 - x1);
    }

    public static double[] linfit(double[] x, double[] y) {
        int n = x.length;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = 0; i < n; i++) {
            sx += x[i];
            sy += y[i];
            sxx += x[i] * x[i];
            sxy += x[i] * y[i];
        }
        double den = n * sxx - sx * sx;
        double m = den == 0 ? 0 : (n * sxy - sx * sy) / den;
        double b = (sy - m * sx) / n;
        return new double[]{m, b};
    }

    public static double[] polyfit2(double[] x, double[] y) {
        double sx = 0, sx2 = 0, sx3 = 0, sx4 = 0;
        double sy = 0, sxy = 0, sx2y = 0;
        int n = x.length;
        for (int i = 0; i < n; i++) {
            double xi = x[i];
            sx += xi;
            sx2 += xi * xi;
            sx3 += xi * xi * xi;
            sx4 += xi * xi * xi * xi;
            sy += y[i];
            sxy += xi * y[i];
            sx2y += xi * xi * y[i];
        }
        double[][] a = {
                {n, sx, sx2},
                {sx, sx2, sx3},
                {sx2, sx3, sx4}
        };
        double[] d = {sy, sxy, sx2y};
        return gaussian(a, d);
    }

    private static double[] gaussian(double[][] a, double[] d) {
        int n = d.length;
        for (int i = 0; i < n; i++) {
            int pivot = i;
            for (int r = i + 1; r < n; r++) {
                if (Math.abs(a[r][i]) > Math.abs(a[pivot][i])) {
                    pivot = r;
                }
            }
            double[] tmp = a[i];
            a[i] = a[pivot];
            a[pivot] = tmp;
            double td = d[i];
            d[i] = d[pivot];
            d[pivot] = td;
            double piv = a[i][i] == 0 ? 1e-30 : a[i][i];
            for (int r = i + 1; r < n; r++) {
                double f = a[r][i] / piv;
                for (int c = i; c < n; c++) {
                    a[r][c] -= f * a[i][c];
                }
                d[r] -= f * d[i];
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = d[i];
            for (int c = i + 1; c < n; c++) {
                sum -= a[i][c] * x[c];
            }
            x[i] = sum / a[i][i];
        }
        return x;
    }

    public static double polyAt(double[] c, double x) {
        if (c.length == 2) {
            return c[0] * x + c[1];
        }
        return c[0] * x * x + c[1] * x + c[2];
    }

    public static double trapz(double[] x, double[] y) {
        double area = 0;
        for (int i = 1; i < x.length; i++) {
            double dx = x[i] - x[i - 1];
            area += 0.5 * dx * (y[i] + y[i - 1]);
        }
        return area;
    }
}
