package org.materials.dsc.domain;

import java.util.List;

/**
 * 弦线基线：直接连接积分窗口两端的热流水平。
 * 对峰型事件（熔融/结晶）是最常见的无偏基线。
 */
public final class ChordBaseline implements BaselineModel {

    @Override
    public String key() {
        return "chord";
    }

    @Override
    public String label() {
        return "弦线（窗口端点连线）";
    }

    @Override
    public Fit fit(List<Double> x, List<Double> y, TangentContext context) {
        int n = x.size();
        if (n < 2) {
            return new Fit(List.copyOf(y), 0.0, List.of("degenerate-window: chord equals signal"));
        }
        double y0 = y.get(0);
        double y1 = y.get(n - 1);
        double x0 = x.get(0);
        double x1 = x.get(n - 1);
        double slope = (y1 - y0) / (x1 - x0);
        double[] b = new double[n];
        double sumSq = 0;
        for (int i = 0; i < n; i++) {
            b[i] = y0 + slope * (x.get(i) - x0);
            double d = y.get(i) - b[i];
            sumSq += d * d;
        }
        return new Fit(toList(b), Math.sqrt(sumSq / n), List.of());
    }

    static List<Double> toList(double[] a) {
        Double[] boxed = new Double[a.length];
        for (int i = 0; i < a.length; i++) {
            boxed[i] = a[i];
        }
        return List.of(boxed);
    }
}
