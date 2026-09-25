package dsc.domain;

import dsc.model.ScanDirection;
import dsc.model.Segment;

import java.util.ArrayList;
import java.util.List;

/**
 * 相变分析引擎。
 * 焓变由时间积分得到：H = integral((HF - baseline) dt)，
 * 采样按扫描速率把 dT 换算为 dt（分钟），再换算为焦耳。
 * 分类遵循内部约定“吸热为正”：加热段上正峰=熔融，负峰=结晶；
 * 冷却段上正峰（吸热）通常不应出现，冷结晶为负峰。绝不根据文件名判断。
 */
public final class TransitionEngine {

    public static final String KIND_GLASS = "glass-transition";
    public static final String KIND_MELT = "melting";
    public static final String KIND_CRYS = "crystallization";

    /**
     * 在给定温度窗口内对一条基线候选求特征。
     */
    public TransitionFeatures analyze(String transitionId,
                                      CalibratedSeries series,
                                      BaselineCandidate baseline,
                                      double windowStartK,
                                      double windowEndK,
                                      double massMilligramsOrNaN) {
        List<Integer> idx = indicesInWindow(series, windowStartK, windowEndK);
        List<String> errors = new ArrayList<>();
        if (idx.isEmpty()) {
            throw new IllegalArgumentException("empty interval: no samples in window");
        }

        double[] signal = new double[idx.size()];
        double[] temps = new double[idx.size()];
        for (int k = 0; k < idx.size(); k++) {
            int i = idx.get(k);
            temps[k] = series.tempK(i);
            signal[k] = series.heat(i) - baseline.valueAt(series.tempK(i));
        }

        int peakIdx = 0;
        double peakAbs = -1;
        for (int k = 0; k < signal.length; k++) {
            if (Math.abs(signal[k]) > peakAbs) {
                peakAbs = Math.abs(signal[k]);
                peakIdx = k;
            }
        }
        boolean endothermic = signal[peakIdx] >= 0;

        int onsetIdx = footOnset(temps, signal, peakIdx, endothermic);
        int endsetIdx = footEndset(temps, signal, peakIdx, signal.length, endothermic);

        double onsetK = temps[onsetIdx];
        double endsetK = temps[endsetIdx];
        double peakK = temps[peakIdx];
        double peakHeat = signal[peakIdx];

        double enthalpyJ = integrateJoules(series, idx, baseline);
        double massGrams = Double.isNaN(massMilligramsOrNaN) ? Double.NaN : massMilligramsOrNaN / 1000.0;
        double perMass = Double.isNaN(massGrams) || massGrams == 0 ? Double.NaN : enthalpyJ / massGrams;

        if (!series.isUniformSpacing(0.25)) {
            errors.add("non-uniform temperature spacing: trapezoid integration over raw gaps");
        }
        if (idx.size() < 8) {
            errors.add("sparse window: fewer than 8 samples inside interval");
        }
        errors.add("baseline method " + baseline.method() + " rmse="
                + String.format("%.4f", baseline.rmseMilliWatt()) + " mW");
        if (Double.isNaN(perMass)) {
            errors.add("mass unavailable: normalized enthalpy not computed");
        }

        String kind = classify(series.segment(), endothermic);

        return new TransitionFeatures(transitionId, series.segment().segmentId(), kind,
                onsetK, peakK, endsetK, peakHeat, enthalpyJ, perMass,
                Double.NaN, baseline.candidateId(), List.copyOf(errors),
                windowStartK, windowEndK);
    }

    /**
     * 用并列基线候选重算同一窗口，得到焓变散布，作为基线选择引入的不确定度。
     * 保留全部候选——无法唯一决定时差异不被吞掉。
     */
    public List<TransitionFeatures> analyzeAcrossBaselines(String transitionId,
                                                           CalibratedSeries series,
                                                           List<BaselineCandidate> baselines,
                                                           double windowStartK,
                                                           double windowEndK,
                                                           double massMg) {
        List<TransitionFeatures> all = new ArrayList<>();
        for (BaselineCandidate bl : baselines) {
            TransitionFeatures f = analyze(transitionId + "-" + bl.candidateId(),
                    series, bl, windowStartK, windowEndK, massMg);
            double spread = baselineSpreadJoules(series, baselines, windowStartK, windowEndK);
            List<String> errors = new ArrayList<>(f.errorSources());
            errors.add(String.format("parallel-baseline spread=%.4f J across %d candidates",
                    spread, baselines.size()));
            all.add(new TransitionFeatures(f.transitionId(), f.segmentId(), f.kind(),
                    f.onsetK(), f.peakK(), f.endsetK(), f.peakHeatMilliWatt(),
                    f.deltaHJoule(), f.deltaHPerMassJoulePerGram(),
                    spread, f.baselineCandidateId(), List.copyOf(errors),
                    windowStartK, windowEndK));
        }
        return all;
    }

    private double baselineSpreadJoules(CalibratedSeries series, List<BaselineCandidate> baselines,
                                        double lo, double hi) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        List<Integer> idx = indicesInWindow(series, lo, hi);
        for (BaselineCandidate bl : baselines) {
            double h = integrateJoules(series, idx, bl);
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        return max - min;
    }

    /**
     * onset：峰前偏离基线最显著处与峰前最大斜率切线和基线的交点。
     * 简化稳健做法：取峰前信号“上升边”最陡点的切线，与零基线相交。
     */
    private int footOnset(double[] t, double[] s, int peakIdx, boolean endo) {
        if (peakIdx == 0) {
            return 0;
        }
        int steep = -1;
        double bestSlopeAbs = -1;
        for (int k = 1; k <= peakIdx; k++) {
            double slope = (s[k] - s[k - 1]) / (t[k] - t[k - 1]);
            if (Math.abs(slope) > bestSlopeAbs) {
                bestSlopeAbs = Math.abs(slope);
                steep = k;
            }
        }
        if (steep <= 0) {
            return 0;
        }
        double slope = (s[steep] - s[steep - 1]) / (t[steep] - t[steep - 1]);
        if (Math.abs(slope) < 1e-12) {
            return Math.max(0, steep - 1);
        }
        double crossT = t[steep] - s[steep] / slope;
        int insert = 0;
        while (insert < t.length && t[insert] < crossT) {
            insert++;
        }
        return Math.max(0, Math.min(steep, insert));
    }

    private int footEndset(double[] t, double[] s, int peakIdx, int n, boolean endo) {
        if (peakIdx >= n - 1) {
            return n - 1;
        }
        int steep = -1;
        double bestSlopeAbs = -1;
        for (int k = peakIdx + 1; k < n; k++) {
            double slope = (s[k] - s[k - 1]) / (t[k] - t[k - 1]);
            if (Math.abs(slope) > bestSlopeAbs) {
                bestSlopeAbs = Math.abs(slope);
                steep = k;
            }
        }
        if (steep <= 0) {
            return n - 1;
        }
        double slope = (s[steep] - s[steep - 1]) / (t[steep] - t[steep - 1]);
        if (Math.abs(slope) < 1e-12) {
            return Math.min(n - 1, steep);
        }
        double crossT = t[steep] - s[steep] / slope;
        int insert = 0;
        while (insert < t.length && t[insert] < crossT) {
            insert++;
        }
        return Math.max(peakIdx + 1, Math.min(n - 1, insert));
    }

    private List<Integer> indicesInWindow(CalibratedSeries series, double lo, double hi) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < series.size(); i++) {
            double t = series.tempK(i);
            if (t >= lo && t <= hi) {
                out.add(i);
            }
        }
        return out;
    }

    private double integrateJoules(CalibratedSeries series, List<Integer> idx,
                                   BaselineCandidate baseline) {
        Segment seg = series.segment();
        double rate = Math.abs(seg.scanRateKPerMin());
        if (!(rate > 0)) {
            throw new IllegalStateException("isothermal segment cannot be integrated by dT/dt rate");
        }
        double energyMilliJoule = 0;
        for (int k = 1; k < idx.size(); k++) {
            int i0 = idx.get(k - 1);
            int i1 = idx.get(k);
            double dT = Math.abs(series.tempK(i1) - series.tempK(i0));
            double dtMin = dT / rate;
            double dtSec = dtMin * 60.0;
            double s0 = series.heat(i0) - baseline.valueAt(series.tempK(i0));
            double s1 = series.heat(i1) - baseline.valueAt(series.tempK(i1));
            energyMilliJoule += 0.5 * (s0 + s1) * dtSec;
        }
        return energyMilliJoule / 1000.0;
    }

    private String classify(Segment seg, boolean endothermic) {
        if (seg.direction() == ScanDirection.HEATING) {
            return endothermic ? KIND_MELT : KIND_CRYS;
        }
        if (seg.direction() == ScanDirection.COOLING) {
            return endothermic ? "anomalous-endotherm-on-cooling" : KIND_CRYS;
        }
        return "isothermal-event";
    }

}
