package org.materials.dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 切线线性基线：用用户选定的左右切线窗口各自拟合直线，再在窗口间线性过渡。
 * 切线是独立来源：没有合法切线窗口时产出带诊断的退化结果，不伪造切线。
 */
public final class TangentLinearBaseline implements BaselineModel {

    @Override
    public String key() {
        return "tangent-linear";
    }

    @Override
    public String label() {
        return "切线线性（用户切线窗口）";
    }

    @Override
    public Fit fit(List<Double> x, List<Double> y, TangentContext context) {
        List<String> diag = new ArrayList<>();
        int n = x.size();
        Line left = fitLine(context.leftPoints());
        Line right = fitLine(context.rightPoints());
        if (left == null) {
            diag.add("missing-left-tangent: falling back to first window point");
            left = Line.constant(n == 0 ? 0 : y.get(0));
        }
        if (right == null) {
            diag.add("missing-right-tangent: falling back to last window point");
            right = Line.constant(n == 0 ? 0 : y.get(n - 1));
        }
        double x0 = n == 0 ? 0 : x.get(0);
        double x1 = n == 0 ? 1 : x.get(n - 1);
        double b0 = left.value(x0);
        double b1 = right.value(x1);
        double span = Math.max(x1 - x0, 1e-12);
        double[] b = new double[n];
        double sumSq = 0;
        for (int i = 0; i < n; i++) {
            b[i] = b0 + (b1 - b0) * (x.get(i) - x0) / span;
            double d = y.get(i) - b[i];
            sumSq += d * d;
        }
        return new Fit(ChordBaseline.toList(b), n == 0 ? 0 : Math.sqrt(sumSq / n), List.copyOf(diag));
    }

    static Line fitLine(List<double[]> points) {
        if (points.size() < 2) {
            return null;
        }
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (double[] p : points) {
            sx += p[0];
            sy += p[1];
            sxx += p[0] * p[0];
            sxy += p[0] * p[1];
        }
        double m = points.size();
        double denom = m * sxx - sx * sx;
        if (Math.abs(denom) < 1e-18) {
            return null;
        }
        double slope = (m * sxy - sx * sy) / denom;
        double intercept = (sy - slope * sx) / m;
        return new Line(slope, intercept);
    }

    record Line(double slope, double intercept) {
        static Line constant(double c) {
            return new Line(0, c);
        }

        double value(double x) {
            return slope * x + intercept;
        }
    }
}
