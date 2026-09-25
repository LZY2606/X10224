package dsc.domain;

import java.util.Comparator;
import java.util.List;

/**
 * 自然三次样条，用于样条基线候选。节点外按端点斜率线性外推。
 */
final class NaturalCubicSpline {

    private final double[] x;
    private final double[] a;
    private final double[] b;
    private final double[] c;
    private final double[] d;

    NaturalCubicSpline(List<double[]> knots) {
        knots.sort(Comparator.comparingDouble(k -> k[0]));
        int n = knots.size();
        x = new double[n];
        a = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = knots.get(i)[0];
            a[i] = knots.get(i)[1];
        }
        double[] h = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            h[i] = x[i + 1] - x[i];
        }
        double[] alpha = new double[n];
        for (int i = 1; i < n - 1; i++) {
            alpha[i] = 3 * ((a[i + 1] - a[i]) / h[i] - (a[i] - a[i - 1]) / h[i - 1]);
        }
        double[] l = new double[n];
        double[] mu = new double[n];
        double[] z = new double[n];
        l[0] = 1;
        for (int i = 1; i < n - 1; i++) {
            l[i] = 2 * (x[i + 1] - x[i - 1]) - h[i - 1] * mu[i - 1];
            mu[i] = h[i] / l[i];
            z[i] = (alpha[i] - h[i - 1] * z[i - 1]) / l[i];
        }
        l[n - 1] = 1;
        c = new double[n];
        b = new double[n - 1];
        d = new double[n - 1];
        for (int j = n - 2; j >= 0; j--) {
            c[j] = z[j] - mu[j] * c[j + 1];
            b[j] = (a[j + 1] - a[j]) / h[j] - h[j] * (c[j + 1] + 2 * c[j]) / 3;
            d[j] = (c[j + 1] - c[j]) / (3 * h[j]);
        }
    }

    double value(double xx) {
        int n = x.length;
        if (xx <= x[0]) {
            double slope = b[0];
            return a[0] + slope * (xx - x[0]);
        }
        if (xx >= x[n - 1]) {
            int j = n - 2;
            double slope = b[j] + 2 * c[j] * (x[n - 1] - x[j])
                    + 3 * d[j] * (x[n - 1] - x[j]) * (x[n - 1] - x[j]);
            return a[n - 1] + slope * (xx - x[n - 1]);
        }
        int i = 0;
        while (i < n - 2 && xx > x[i + 1]) {
            i++;
        }
        double dx = xx - x[i];
        return a[i] + b[i] * dx + c[i] * dx * dx + d[i] * dx * dx * dx;
    }
}
