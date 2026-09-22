package org.materials.dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * S 形阶跃基线：用于玻璃化台阶。模型为
 * y(x) = lo + (hi-lo)/(1+exp(-k(x-x0)))，在最小斜率（台阶中点）处锚定 x0，
 * 对陡度 k 做网格搜索，lo/hi 用该 k 下的线性最小二乘解出。
 * 对玻璃化，焓积分通常不是关键量；该基线主要服务于台阶高度与 onset/endset。
 */
public final class SigmoidStepBaseline implements BaselineModel {

    @Override
    public String key() {
        return "sigmoid-step";
    }

    @Override
    public String label() {
        return "S 形阶跃（玻璃化台阶）";
    }

    @Override
    public Fit fit(List<Double> x, List<Double> y, TangentContext context) {
        int n = x.size();
        List<String> diag = new ArrayList<>();
        if (n < 4) {
            return new Fit(List.copyOf(y), 0, List.of("degenerate-window: sigmoid-step needs >=4 points"));
        }
        double xMin = x.get(0);
        double xMax = x.get(n - 1);
        double span = Math.max(xMax - xMin, 1e-12);

        int midIdx = 0;
        double steepest = Double.NEGATIVE_INFINITY;
        for (int i = 1; i < n - 1; i++) {
            double d = (y.get(i + 1) - y.get(i - 1)) / Math.max(x.get(i + 1) - x.get(i - 1), 1e-12);
            double ad = Math.abs(d);
            if (ad > steepest) {
                steepest = ad;
                midIdx = i;
            }
        }
        double x0 = x.get(midIdx);

        double bestRms = Double.POSITIVE_INFINITY;
        double[] bestB = null;
        double bestLo = 0;
        double bestHi = 0;
        for (double kRel : new double[]{2, 4, 6, 8, 12, 16, 24, 32}) {
            double k = kRel / span;
            double[] w = new double[n];
            double sumW = 0, sumOne = 0, sumWY = 0, sumY = 0;
            for (int i = 0; i < n; i++) {
                double z = 1.0 / (1.0 + Math.exp(-k * (x.get(i) - x0)));
                w[i] = z;
                sumW += z;
                sumOne += 1;
                sumWY += z * y.get(i);
                sumY += y.get(i);
            }
            double denom = sumOne * sumW - sumW * sumW;
            if (Math.abs(denom) < 1e-14) {
                continue;
            }
            double lo = (sumY * sumW - sumWY * sumW) / denom;
            double hi = (sumOne * sumWY - sumW * sumY) / denom;
            double[] b = new double[n];
            double sumSq = 0;
            for (int i = 0; i < n; i++) {
                b[i] = lo + (hi - lo) * w[i];
                double d = y.get(i) - b[i];
                sumSq += d * d;
            }
            double rms = Math.sqrt(sumSq / n);
            if (rms < bestRms) {
                bestRms = rms;
                bestB = b;
                bestLo = lo;
                bestHi = hi;
            }
        }
        if (bestB == null) {
            return new Fit(List.copyOf(y), 0, List.of("sigmoid-fit-failed"));
        }
        if (steepest * span < 0.02 * Math.abs(bestHi - bestLo + 1e-12)) {
            diag.add("weak-step: no clear glass transition step in window");
        }
        return new Fit(ChordBaseline.toList(bestB), bestRms, List.copyOf(diag));
    }
}
