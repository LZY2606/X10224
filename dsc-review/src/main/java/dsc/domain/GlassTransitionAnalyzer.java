package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 玻璃化转变：DSC 上表现为热容台阶（热流阶跃）。
 * 这里给出 onset / midpoint / endset 温度与台阶高度；
 * 台阶上的小焓松弛峰不按熔融积分，避免把重叠转折混为一个事件。
 */
public final class GlassTransitionAnalyzer {

    public record GlassResult(double onsetK,
                              double midpointK,
                              double endsetK,
                              double stepMilliWatt,
                              List<String> notes) {
    }

    public GlassResult analyze(CalibratedSeries series,
                               BaselineCandidate preBaseline,
                               BaselineCandidate postBaseline,
                               double windowStartK,
                               double windowEndK) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < series.size(); i++) {
            double t = series.tempK(i);
            if (t >= windowStartK && t <= windowEndK) {
                idx.add(i);
            }
        }
        if (idx.size() < 4) {
            throw new IllegalArgumentException("empty/too-small interval for glass transition");
        }
        int n = idx.size();
        double[] resid = new double[n];
        double[] temps = new double[n];
        for (int k = 0; k < n; k++) {
            int i = idx.get(k);
            temps[k] = series.tempK(i);
            double mid = 0.5 * (preBaseline.valueAt(temps[k]) + postBaseline.valueAt(temps[k]));
            resid[k] = series.heat(i) - mid;
        }

        int q1 = Math.max(1, n / 4);
        int q3 = Math.min(n - 2, 3 * n / 4);
        double preLevel = average(resid, 0, q1);
        double postLevel = average(resid, q3, n);
        double step = postLevel - preLevel;
        double target = preLevel + step / 2.0;
        int midIdx = 0;
        double best = Double.POSITIVE_INFINITY;
        for (int k = 0; k < n; k++) {
            if (Math.abs(resid[k] - target) < best) {
                best = Math.abs(resid[k] - target);
                midIdx = k;
            }
        }
        List<String> notes = new ArrayList<>();
        notes.add("step-like heat-capacity change; enthalpy relaxation peak, if any, is a separate event");
        if (!series.isUniformSpacing(0.25)) {
            notes.add("non-uniform spacing: step levels averaged on raw samples");
        }
        return new GlassResult(temps[0], temps[midIdx], temps[n - 1], step, notes);
    }

    private double average(double[] v, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) {
            sum += v[i];
        }
        return sum / (to - from);
    }
}
