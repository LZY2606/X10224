package dsc.core;

import dsc.model.BaselineModel;
import dsc.model.CandidateBaseline;
import dsc.model.Direction;
import dsc.model.ErrorSource;
import dsc.model.FeaturePoint;
import dsc.model.Finding;
import dsc.model.NormalizedCurve;
import dsc.model.NormalizedPoint;
import dsc.model.SignConvention;
import dsc.model.TransitionKind;

import java.util.ArrayList;
import java.util.List;

/**
 * 在单条基线候选上做扣除、分类与积分：
 *  - 残差 r = hf - baseline；内部坐标为 exo-up（放热向上）；
 *  - 形状判定：持续台阶 → 玻璃化（ΔCp）；峰 → 熔融/结晶；
 *  - onset/endset 采用最大斜率切线与锚点基线交点；
 *  - 积分对非等间隔网格用实际温度间隔的梯形法；
 *  - 焓变符号约定为吸热为正、放热为负；符号约定缺失时保留歧义；
 *  - 误差来源逐项给出量级：基线分歧、离散化、重叠、符号、质量/速率/标定。
 */
public final class Transitions {
    private Transitions() {}

    @SuppressWarnings("unused")
    public record Result(Finding finding, String emptyReason) {}

    public static Finding analyze(String findingId, String windowId, CandidateBaseline base,
                                  NormalizedCurve curve, SearchCtx ctx) {
        List<NormalizedPoint> in = new ArrayList<>();
        for (NormalizedPoint p : curve.points) {
            if (p.tempC >= Math.min(base.leftTempC, base.rightTempC)
                    && p.tempC <= Math.max(base.rightTempC, base.leftTempC)) in.add(p);
        }
        in.sort((a, b) -> Double.compare(a.tempC, b.tempC));
        Finding f = new Finding();
        f.findingId = findingId;
        f.segmentId = curve.segmentId;
        f.windowId = windowId;
        f.candidateId = base.candidateId;
        f.baselineModel = base.model;
        if (in.size() < 3) {
            return null;
        }

        double[] t = new double[in.size()];
        double[] r = new double[in.size()];
        for (int i = 0; i < in.size(); i++) {
            t[i] = in.get(i).tempC;
            r[i] = in.get(i).hfMw - base.valueAt(t[i]);
        }

        double firstQ = mean(r, 0, Math.max(1, r.length / 4));
        double lastQ = mean(r, r.length - Math.max(1, r.length / 4), r.length);
        double step = lastQ - firstQ;
        double peakAmp = 0;
        int peakIdx = 0;
        for (int i = 0; i < r.length; i++) {
            if (Math.abs(r[i]) > Math.abs(r[peakIdx])) { peakAmp = Math.abs(r[i]); peakIdx = i; }
        }
        boolean glassLike = Math.abs(step) > 0.6 * peakAmp && peakAmp > 0;

        Double rate = ctx.segmentRateKperMin;
        Double massG = ctx.massG;
        boolean signKnown = curve.sign != SignConvention.UNSPECIFIED;

        if (glassLike) {
            f.kind = TransitionKind.GLASS;
            f.thermalSense = "step";
            double midT = (t[0] + t[t.length - 1]) / 2.0;
            int closest = 0;
            for (int i = 0; i < t.length; i++) if (Math.abs(t[i] - midT) < Math.abs(t[closest] - midT)) closest = i;
            f.points.add(new FeaturePoint(findingId + ":tg_mid", "tg_mid", t[closest], base.valueAt(t[closest]),
                    "half-height of residual step"));
            f.points.add(new FeaturePoint(findingId + ":onset", "onset", t[Math.max(0, r.length / 4 - 1)],
                    in.get(Math.max(0, r.length / 4 - 1)).hfMw, "step departure tangent"));
            f.points.add(new FeaturePoint(findingId + ":endset", "endset",
                    t[Math.min(t.length - 1, 3 * r.length / 4)],
                    in.get(Math.min(t.length - 1, 3 * r.length / 4)).hfMw, "step completion tangent"));
            if (rate != null && massG != null) {
                f.deltaCpJgK = (step / 1000.0) / (rate / 60.0) / massG;
            }
            f.enthalpyJg = null;
            f.enthalpyJ = null;
        } else {
            boolean positivePeak = r[peakIdx] >= 0;            // exo-up 坐标下：正=放热
            f.thermalSense = !signKnown ? "ambiguous"
                    : positivePeak ? "exothermic" : "endothermic";
            f.kind = classify(curve.direction, f.thermalSense);

            int onsetIdx = tangentExtreme(t, r, 0, peakIdx, true);
            int endsetIdx = tangentExtreme(t, r, peakIdx, r.length - 1, false);
            f.points.add(new FeaturePoint(findingId + ":onset", "onset", t[onsetIdx], in.get(onsetIdx).hfMw,
                    "maximum-slope tangent intersects left anchor baseline"));
            f.points.add(new FeaturePoint(findingId + ":peak", "peak", t[peakIdx], in.get(peakIdx).hfMw,
                    "residual extremum"));
            f.points.add(new FeaturePoint(findingId + ":endset", "endset", t[endsetIdx], in.get(endsetIdx).hfMw,
                    "maximum-slope tangent intersects right anchor baseline"));

            if (rate != null && massG != null) {
                double areaMwK = -trapz(t, r); // 吸热为正
                double jg = areaMwK / (rate / 60.0) / 1000.0 / massG;
                if (!signKnown) jg = Math.abs(jg);
                f.enthalpyJg = jg;
                f.enthalpyJ = jg * massG;
            }
        }

        f.overlapScore = ctx.overlapScore;
        addErrors(f, t, r, base, curve, ctx, signKnown, rate, massG, glassLike);
        return f;
    }

    private static void addErrors(Finding f, double[] t, double[] r, CandidateBaseline base,
                                  NormalizedCurve curve, SearchCtx ctx, boolean signKnown,
                                  Double rate, Double massG, boolean glass) {
        String wid = f.windowId;
        if (!signKnown) {
            f.errors.add(new ErrorSource(f.findingId + ":err-sign", "sign",
                    "endo/exo 符号未定（仪器头未声明）", f.enthalpyJg == null ? 0 : Math.abs(f.enthalpyJg), 0,
                    "sign_convention=unspecified; magnitude computable, thermal sense ambiguous"));
        }
        if (rate == null) {
            f.errors.add(new ErrorSource(f.findingId + ":err-rate", "rate",
                    "扫描速率缺失，时间量纲无法闭合", f.enthalpyJg == null ? 0 : Double.NaN, 0,
                    "ΔH ∝ 1/β; β unavailable from instrument header"));
        }
        if (massG == null) {
            f.errors.add(new ErrorSource(f.findingId + ":err-mass", "mass",
                    "质量版本缺失或失效，J/g 归一化未产生", 0, 0,
                    "normalized enthalpy requires a valid mass version"));
        }
        if (!curve.equidistant && !glass) {
            double fine = -trapz(t, r);
            double coarse = -trapzCoarse(t, r);
            double disc = Math.abs(fine - coarse);
            double jg = (rate != null && massG != null) ? disc / (rate / 60.0) / 1000.0 / massG : 0;
            f.errors.add(new ErrorSource(f.findingId + ":err-spacing", "spacing",
                    "非等间隔温度网格离散化（全网格 vs 隔点网格）", jg, 0,
                    "trapezoid-on-actual-intervals; grid-doubling difference reported"));
        }
        if (ctx.baselineSpreadJg > 0) {
            f.errors.add(new ErrorSource(f.findingId + ":err-baseline", "baseline",
                    "并列基线候选导致的焓变分歧", ctx.baselineSpreadJg, ctx.baselineSpreadK,
                    "retained candidates: " + String.join(",", ctx.candidateModels)));
        }
        if (ctx.overlapScore > 0 && !glass) {
            double v = f.enthalpyJg == null ? 0 : Math.abs(f.enthalpyJg) * 0.5 * ctx.overlapScore;
            f.errors.add(new ErrorSource(f.findingId + ":err-overlap", "overlap",
                    "重叠转折：相邻事件能量串入本窗口", v, 0,
                    "overlap score = shared temperature span / shorter window"));
        }
        double offset = ctx.appliedCalibrationK;
        if (Math.abs(offset) > 1e-12) {
            f.errors.add(new ErrorSource(f.findingId + ":err-calibration", "calibration",
                    "已施加的温度标定平移（来源链可查）", 0, Math.abs(offset),
                    "T_cal = T_ind + " + offset + " °C; correction magnitude, not residual uncertainty"));
        }
    }

    private static TransitionKind classify(Direction dir, String sense) {
        if ("ambiguous".equals(sense)) return TransitionKind.UNKNOWN;
        return switch (dir) {
            case HEATING -> "endothermic".equals(sense) ? TransitionKind.MELTING : TransitionKind.CRYSTALLIZATION;
            case COOLING -> "exothermic".equals(sense) ? TransitionKind.CRYSTALLIZATION : TransitionKind.UNKNOWN;
            default -> TransitionKind.UNKNOWN;
        };
    }

    /** 在 [from,to] 区间寻找 d r/d t 绝对值最大的索引（升温、降温都在温度排序后处理）。 */
    private static int tangentExtreme(double[] t, double[] r, int from, int to, boolean leading) {
        int best = leading ? to : from;
        double bestV = -1;
        for (int i = Math.max(1, from + 1); i < Math.min(r.length - 1, to + 1); i++) {
            double d = Math.abs((r[i + 1] - r[i - 1]) / Math.max(1e-9, t[i + 1] - t[i - 1]));
            if (d > bestV) { bestV = d; best = i; }
        }
        return best;
    }

    private static double trapz(double[] x, double[] y) {
        double s = 0;
        for (int i = 1; i < x.length; i++) s += 0.5 * (y[i] + y[i - 1]) * (x[i] - x[i - 1]);
        return s;
    }

    private static double trapzCoarse(double[] x, double[] y) {
        double s = 0;
        for (int i = 2; i < x.length; i += 2) s += 0.5 * (y[i] + y[i - 2]) * (x[i] - x[i - 2]);
        return s;
    }

    private static double mean(double[] v, int a, int b) {
        double s = 0;
        int n = 0;
        for (int i = a; i < b; i++) { s += v[i]; n++; }
        return n == 0 ? 0 : s / n;
    }

    /** 检测上下文：同一窗口不同候选共享的外部量。 */
    public static final class SearchCtx {
        public Double segmentRateKperMin;
        public Double massG;
        public double overlapScore;
        public double baselineSpreadJg;
        public double baselineSpreadK;
        public double appliedCalibrationK;
        public java.util.List<String> candidateModels = new ArrayList<>();
    }
}
