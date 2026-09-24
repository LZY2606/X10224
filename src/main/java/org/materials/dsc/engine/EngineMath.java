package org.materials.dsc.engine;

import java.util.ArrayList;
import java.util.List;

/** 引擎数值工具：线性最小二乘、非等间隔检测、非等距梯形积分。 */
public final class EngineMath {
    private EngineMath() {
    }

    public record Line(double slope, double intercept, double rms) {
        public double at(double x) {
            return slope * x + intercept;
        }
    }

    public static Line fit(double[] xs, double[] ys, int lo, int hi) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = hi - lo + 1;
        for (int i = lo; i <= hi; i++) {
            sx += xs[i];
            sy += ys[i];
            sxx += xs[i] * xs[i];
            sxy += xs[i] * ys[i];
        }
        double denom = n * sxx - sx * sx;
        double m = denom == 0 ? 0 : (n * sxy - sx * sy) / denom;
        double b = (sy - m * sx) / n;
        double rss = 0;
        for (int i = lo; i <= hi; i++) {
            double r = ys[i] - (m * xs[i] + b);
            rss += r * r;
        }
        return new Line(m, b, Math.sqrt(rss / Math.max(1, n)));
    }

    /** 相邻点温升的离散程度：中位步长与最大相对偏差。非等间隔温度据此判定。 */
    public record StepStats(double medianStep, double maxRelDeviation, boolean nonUniform) {
    }

    public static StepStats stepStats(double[] t) {
        List<Double> steps = new ArrayList<>();
        for (int i = 1; i < t.length; i++) {
            double d = Math.abs(t[i] - t[i - 1]);
            if (d > 1e-12) {
                steps.add(d);
            }
        }
        if (steps.isEmpty()) {
            return new StepStats(0, 0, false);
        }
        List<Double> sorted = new ArrayList<>(steps);
        sorted.sort(Double::compare);
        double median = sorted.get(sorted.size() / 2);
        double maxRel = 0;
        for (double s : steps) {
            maxRel = Math.max(maxRel, Math.abs(s - median) / Math.max(1e-9, median));
        }
        return new StepStats(median, maxRel, maxRel > 0.35);
    }

    /**
     * 非等距梯形积分。温度轴单位 °C、热流 mW，结果 mW·°C；
     * 积分区间以温度方向归一（净面积按数据顺序），调用方按扫描方向解释符号。
     */
    public static double trapezoidCelsius(double[] t, double[] y, int lo, int hi) {
        double area = 0;
        int step = hi >= lo ? 1 : -1;
        for (int i = lo; i != hi; i += step) {
            int j = i + step;
            area += 0.5 * (y[i] + y[j]) * (t[j] - t[i]);
        }
        return area;
    }
}
