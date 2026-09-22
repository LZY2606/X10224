package dsc.engine;

import dsc.domain.BaselineCandidate;
import dsc.domain.EnthalpyMetric;
import dsc.domain.Feature;
import dsc.domain.Ids;

import java.util.ArrayList;
import java.util.List;

/**
 * 焓变积分：按时间轴梯形积分（对非等间隔温度采样同样成立），
 * 除以样品质量得到 J/g。符号方向由扫描方向 + 仪器极性共同决定，
 * 绝不依据文件名猜测。
 *
 * <p>误差是可解释的分项：基线选择、质量版本、速率标定、温度标定、采样噪声。
 */
public final class Integrator {

    private Integrator() {
    }

    public record IntegralInputs(
            double massKg,
            double massUncertaintyRel,
            double rateKPerSec,
            double rateUncertaintyRel,
            double tempCalibOffsetK,
            double tempCalibUncertaintyK,
            boolean polarityReversed,
            boolean polarityAmbiguous
    ) {
    }

    public static double baselineAt(BaselineCandidate candidate, double t) {
        if (candidate.linear() || candidate.params() == null) {
            return candidate.a() * t + candidate.b();
        }
        double[] p = candidate.params();
        double mid = (p[0] + p[1]) / 2;
        double half = Math.max(1e-9, (p[1] - p[0]) / 2);
        double u = (t - mid) / half;
        double f = 0.5 + 0.5 * u / Math.sqrt(1 + u * u);
        return (p[2] * t + p[3]) * (1 - f) + (p[4] * t + p[5]) * f + p[6];
    }

    /**
     * @return {面积 J（带方向符号）, 基线不确定度贡献 J}
     */
    public static double[] integrate(double[][] points, int hfCol, int onsetIdx, int endIdx,
                                     BaselineCandidate chosen,
                                     List<BaselineCandidate> alternatives) {
        double area = 0;
        double altSpread = 0;
        for (int i = onsetIdx; i < endIdx; i++) {
            double dt = points[i + 1][0] - points[i][0];
            double d1 = points[i][hfCol] - baselineAt(chosen, points[i][1]);
            double d2 = points[i + 1][hfCol] - baselineAt(chosen, points[i + 1][1]);
            area += 0.5 * (d1 + d2) * dt;
        }
        for (BaselineCandidate alt : alternatives) {
            if (alt.id().equals(chosen.id())) {
                continue;
            }
            double altArea = 0;
            for (int i = onsetIdx; i < endIdx; i++) {
                double dt = points[i + 1][0] - points[i][0];
                double d1 = points[i][hfCol] - baselineAt(alt, points[i][1]);
                double d2 = points[i + 1][hfCol] - baselineAt(alt, points[i + 1][1]);
                altArea += 0.5 * (d1 + d2) * dt;
            }
            altSpread = Math.max(altSpread, Math.abs(altArea - area));
        }
        return new double[]{area, altSpread};
    }

    public static EnthalpyMetric enthalpy(Feature feature, double[][] points, int hfCol,
                                          BaselineCandidate chosen,
                                          List<BaselineCandidate> all,
                                          IntegralInputs inputs) {
        int onsetIdx = nearest(points, feature.onsetT());
        int endIdx = nearest(points, feature.endsetT());
        if (endIdx <= onsetIdx) {
            return new EnthalpyMetric(Ids.metric(feature.id(), "enthalpy"),
                    feature.id(), 0, 0, List.of(),
                    "空区间：onset/endset 之间无数据点，无法积分");
        }
        double[] areaAndSpread = integrate(points, hfCol, onsetIdx, endIdx, chosen, all);
        double area = areaAndSpread[0];
        double baselineUncertaintyJ = areaAndSpread[1];

        // 方向：加热段吸热为正；冷却段凝固/结晶本身放热。
        // 极性声明反向则翻转；极性存疑时保留绝对值并标记，符号需人工裁决。
        double polarity = inputs.polarityReversed ? -1 : 1;
        double signedArea = area * polarity;

        double grams = inputs.massKg * 1000.0;
        double jPerG = grams == 0 ? 0 : signedArea / grams;

        double noise = Signal.noiseLevel(Signal.column(points, hfCol), onsetIdx, endIdx);
        double noiseArea = 0;
        for (int i = onsetIdx; i < endIdx; i++) {
            noiseArea += noise * (points[i + 1][0] - points[i][0]);
        }

        double calibArea = inputs.tempCalibUncertaintyK == 0 ? 0
                : area * inputs.tempCalibUncertaintyK
                / Math.max(1.0, feature.endsetT() - feature.onsetT());
        double rateAreaJ = Math.abs(area) * inputs.rateUncertaintyRel;
        double massAreaJ = Math.abs(area) * inputs.massUncertaintyRel;

        double totalJ = Math.sqrt(baselineUncertaintyJ * baselineUncertaintyJ
                + noiseArea * noiseArea
                + calibArea * calibArea
                + rateAreaJ * rateAreaJ
                + massAreaJ * massAreaJ);

        List<EnthalpyMetric.ErrorContribution> budget = new ArrayList<>();
        budget.add(new EnthalpyMetric.ErrorContribution("baseline_choice",
                "并列基线候选差异", toJPerG(baselineUncertaintyJ, grams),
                all.size() + " 条合法候选，取未采纳候选最大面积差"));
        budget.add(new EnthalpyMetric.ErrorContribution("sampling_noise",
                "采样噪声", toJPerG(noiseArea, grams),
                "由二阶差分估计噪声 RMS，按区间时长传播"));
        budget.add(new EnthalpyMetric.ErrorContribution("temp_calibration",
                "温度标定", toJPerG(calibArea, grams),
                "标定不确定度 " + inputs.tempCalibUncertaintyK + " K，按区间宽度缩放"));
        budget.add(new EnthalpyMetric.ErrorContribution("scan_rate",
                "扫描速率", toJPerG(rateAreaJ, grams),
                "速率相对不确定度 " + inputs.rateUncertaintyRel));
        budget.add(new EnthalpyMetric.ErrorContribution("sample_mass",
                "样品质量版本", toJPerG(massAreaJ, grams),
                "质量相对不确定度 " + inputs.massUncertaintyRel
                        + "；质量换版后本派生整体过期"));

        String signNote = inputs.polarityAmbiguous
                ? "极性存疑（声明与数据不一致），符号待人工裁决；加热/冷却段不混用"
                : (feature.heating() ? "加热段" : "冷却段")
                + (inputs.polarityReversed ? "，仪器极性声明为反向" : "")
                + "；方向来自实测斜率与极性档案，不来自文件名";

        return new EnthalpyMetric(Ids.metric(feature.id(), "enthalpy"),
                feature.id(), jPerG, toJPerG(totalJ, grams), budget, signNote);
    }

    private static double toJPerG(double joules, double grams) {
        return grams == 0 ? 0 : joules / grams;
    }

    private static int nearest(double[][] points, double t) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < points.length; i++) {
            double d = Math.abs(points[i][1] - t);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }
}
