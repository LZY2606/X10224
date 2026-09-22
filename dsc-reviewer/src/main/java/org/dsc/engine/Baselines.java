package org.dsc.engine;

import org.dsc.domain.Models.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 基线候选生成。多个合理基线（两点切线 / 窗口线性回归 / 平台常数）并列保留差异，
 * 不因“想要唯一答案”而吞掉多解。候选的 RMSE 与锚点数作为质量指标随结果存档。
 */
public final class Baselines {
    private Baselines() {}

    public static final class Fit {
        public final double slope, intercept, rmse;
        public final int points;
        Fit(double slope, double intercept, double rmse, int points) {
            this.slope = slope;
            this.intercept = intercept;
            this.rmse = rmse;
            this.points = points;
        }
        double at(double tK) { return slope * tK + intercept; }
    }

    /** 普通最小二乘 y = a*x + b；points<2 或退化时返回 null（调用方记诊断）。 */
    public static Fit linearFit(List<double[]> xy) {
        int n = xy.size();
        if (n < 2) return null;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (double[] p : xy) {
            sx += p[0]; sy += p[1]; sxx += p[0] * p[0]; sxy += p[0] * p[1];
        }
        double den = n * sxx - sx * sx;
        if (den == 0) return null;
        double slope = (n * sxy - sx * sy) / den;
        double intercept = (sy - slope * sx) / n;
        double sse = 0;
        for (double[] p : xy) {
            double r = p[1] - (slope * p[0] + intercept);
            sse += r * r;
        }
        return new Fit(slope, intercept, Math.sqrt(sse / n), n);
    }

    /** 窗口内点（允许空区间——返回空列表由调用方判定）。 */
    public static List<Point> pointsInWindow(Segment seg, TempWindow w) {
        List<Point> out = new ArrayList<>();
        for (Point p : seg.points()) if (w.contains(p.temperature())) out.add(p);
        return out;
    }

    public static List<Point> anchorPoints(Segment seg, TempWindow anchor) {
        return pointsInWindow(seg, anchor);
    }

    /**
     * 并列候选：
     *  1) LINEAR_TANGENT：左右两个锚定区间各自取均值点连成直线（用户切线）
     *  2) LINEAR_REGRESSION：左右锚点合并做 OLS
     *  3) CONSTANT：锚点均值的平台线
     * 锚点不足时对应候选省略并由调用方记录诊断；绝不编造。
     */
    public static List<Fit> candidates(Segment seg, TangentSelection tan) {
        List<Point> left = anchorPoints(seg, tan.leftWindow());
        List<Point> right = anchorPoints(seg, tan.rightWindow());
        List<Fit> out = new ArrayList<>();

        if (!left.isEmpty() && !right.isEmpty()) {
            double[] l = meanXY(left);
            double[] r = meanXY(right);
            double den = r[0] - l[0];
            if (den != 0) {
                double slope = (r[1] - l[1]) / den;
                double intercept = l[1] - slope * l[0];
                double rmse = rmse(List.of(l, r), slope, intercept);
                out.add(new Fit(slope, intercept, rmse, left.size() + right.size()));
            }
        }
        List<double[]> pooled = new ArrayList<>();
        left.forEach(p -> pooled.add(new double[]{p.temperature(), p.heatFlow()}));
        right.forEach(p -> pooled.add(new double[]{p.temperature(), p.heatFlow()}));
        Fit ols = linearFit(pooled);
        if (ols != null) out.add(ols);

        if (!pooled.isEmpty()) {
            double mean = pooled.stream().mapToDouble(p -> p[1]).average().orElse(0);
            double sse = 0;
            for (double[] p : pooled) sse += (p[1] - mean) * (p[1] - mean);
            out.add(new Fit(0, mean, Math.sqrt(sse / pooled.size()), pooled.size()));
        }
        return out;
    }

    private static double[] meanXY(List<Point> ps) {
        double t = 0, h = 0;
        for (Point p : ps) { t += p.temperature(); h += p.heatFlow(); }
        return new double[]{t / ps.size(), h / ps.size()};
    }

    private static double rmse(List<double[]> xy, double slope, double intercept) {
        double sse = 0;
        for (double[] p : xy) {
            double r = p[1] - (slope * p[0] + intercept);
            sse += r * r;
        }
        return Math.sqrt(sse / xy.size());
    }
}
