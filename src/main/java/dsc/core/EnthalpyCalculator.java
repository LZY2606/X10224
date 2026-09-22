package dsc.core;

import dsc.domain.BaselineResult;
import dsc.domain.EnthalpyResult;
import dsc.domain.Segment;
import dsc.domain.TransitionResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 焓变积分。
 *
 * 时间梯形积分（对非等间隔温度精确成立）：
 *   dt_min = |ΔT(K)| / |rate(K/min)|，dt_s = dt_min·60，
 *   ΔH(J/g) = Σ ½·((y−b)_i + (y−b)_{i+1})·dt_s。
 * 加热与冷却段不混用：积分区间必须落在单段内，越界返回 CROSSES_SEGMENTS。
 * 空区间（区间内无可用点）返回 EMPTY_INTERVAL。
 * 方向未决时保留绝对量 magnitude，enthalpy=NaN 且 heatDirection=UNRESOLVED。
 */
public final class EnthalpyCalculator {

    private EnthalpyCalculator() {
    }

    public static EnthalpyResult integrate(String integrationId, Segment seg, BaselineResult base,
                                           TransitionResult transition, double rateUncertaintyFraction,
                                           double massUncertaintyFraction) {
        List<EnthalpyResult.ErrorContribution> errors = new ArrayList<>();
        // 积分覆盖前切线窗口结束→后切线窗口开始之间的完整峰/台阶区域；
        // onset/endset（切线交点）用于特征温度显示，不截断峰尾。
        double lo = base.usable() ? base.integrationLowK() : Math.min(base.onsetK, base.endsetK);
        double hi = base.usable() ? base.integrationHighK() : Math.max(base.onsetK, base.endsetK);

        if (!Double.isFinite(lo) || !Double.isFinite(hi)) {
            return fail(integrationId, transition, base, seg, "EMPTY_INTERVAL",
                    "integration bounds undefined");
        }
        int first = seg.firstGood();
        int last = seg.lastGood();
        if (first < 0) {
            return fail(integrationId, transition, base, seg, "EMPTY_INTERVAL",
                    "segment contains no usable points");
        }
        double segMin = Math.min(seg.xK[first], seg.xK[last]);
        double segMax = Math.max(seg.xK[first], seg.xK[last]);
        if (lo < segMin - 1e-9 || hi > segMax + 1e-9) {
            return fail(integrationId, transition, base, seg, "CROSSES_SEGMENTS",
                    "integration interval reaches beyond segment " + seg.segmentId
                            + "; heating and cooling segments must never be merged");
        }

        double rate = Math.abs(seg.scanRateKPerMin);
        double integral = 0;
        int usedEdges = 0;
        int inside = 0;
        for (int i = first; i < last; i++) {
            if (!seg.good[i] || !seg.good[i + 1]) {
                continue;
            }
            double x0 = seg.xK[i];
            double x1 = seg.xK[i + 1];
            if (x1 <= lo || x0 >= hi) {
                continue;
            }
            double u0 = Math.max(x0, lo);
            double u1 = Math.min(x1, hi);
            if (u1 <= u0) {
                continue;
            }
            double span = x1 - x0;
            double w0 = (u0 - x0) / span;
            double w1 = (u1 - x0) / span;
            double f0 = seg.yWg[i] - base.baselineAt(u0);
            double f1 = seg.yWg[i + 1] - base.baselineAt(u1);
            // 线性裁剪区间内的端点函数值
            double g0 = seg.yWg[i] + w0 * (seg.yWg[i + 1] - seg.yWg[i]) - base.baselineAt(u0);
            double g1 = seg.yWg[i] + w1 * (seg.yWg[i + 1] - seg.yWg[i]) - base.baselineAt(u1);
            double dtSec = Math.abs(u1 - u0) / rate * 60.0;
            integral += 0.5 * (g0 + g1) * dtSec;
            usedEdges++;
            if (x0 >= lo && x0 <= hi) {
                inside++;
            }
            // 保留 f/f1 引用避免编译器告警（裁剪值 g 已包含基线扣除）
            if (Double.isNaN(f0 + f1)) {
                return fail(integrationId, transition, base, seg, "ERROR", "non-finite residual in interval");
            }
        }
        if (usedEdges == 0) {
            return fail(integrationId, transition, base, seg, "EMPTY_INTERVAL",
                    "no sampled edges intersect [" + String.format("%.2f", lo) + ", "
                            + String.format("%.2f", hi) + "] K");
        }

        double magnitude = Math.abs(integral);
        boolean resolved = transition.signResolved;
        double signed = resolved ? transition.heatSign * magnitude : Double.NaN;
        String direction = !resolved ? "UNRESOLVED" : transition.heatSign > 0
                ? "ENDOTHERMIC" : "EXOTHERMIC";

        // 误差来源逐项（线性独立不确定度近似，单位 J/g）
        errors.add(new EnthalpyResult.ErrorContribution("baseline_choice",
                "基线选择（前/后切线窗口）", magnitude * baselineWindowFraction(base),
                "candidate " + base.candidateId + " tangent windows vs alternative baselines"));
        errors.add(new EnthalpyResult.ErrorContribution("scan_rate",
                "扫描速率标定", magnitude * rateUncertaintyFraction,
                "rate " + String.format("%.3f", rate) + " K/min ±"
                        + (rateUncertaintyFraction * 100) + "%"));
        errors.add(new EnthalpyResult.ErrorContribution("mass_normalization",
                "样品质量（归一化）", magnitude * massUncertaintyFraction,
                "mass version used to build segment curve ±" + (massUncertaintyFraction * 100) + "%"));
        errors.add(new EnthalpyResult.ErrorContribution("temperature_calibration",
                "温度标定偏移", magnitude * Math.abs(base.endsetK - base.onsetK) == 0 ? 0
                : magnitude * 0.002,
                "calibration " + seg.calibrationId + "; interval "
                        + String.format("%.2f", Math.abs(hi - lo)) + " K, ±0.2% boundary shift"));
        errors.add(new EnthalpyResult.ErrorContribution("temperature_grid",
                "非等间隔/采样网格", magnitude * (usedEdges <= 2 ? 0.02 : 0.005),
                usedEdges + " integrated edges (time-based trapezoid, no resampling)"));
        if (!resolved) {
            errors.add(new EnthalpyResult.ErrorContribution("direction_unresolved",
                    "吸放热方向未决（仪器头缺失）", magnitude,
                    "sign not assigned; magnitude retained, direction=UNRESOLVED"));
        }

        return new EnthalpyResult(integrationId, transition.transitionId, base.candidateId, seg.segmentId,
                lo, hi, signed, magnitude, resolved, direction, errors, "OK", null);
    }

    private static double baselineWindowFraction(BaselineResult base) {
        if ("AMBIGUOUS".equals(base.status)) {
            return 0.05;
        }
        return 0.02;
    }

    private static EnthalpyResult fail(String integrationId, TransitionResult transition,
                                       BaselineResult base, Segment seg, String status, String diag) {
        return new EnthalpyResult(integrationId, transition.transitionId, base.candidateId, seg.segmentId,
                base.onsetK, base.endsetK, Double.NaN, Double.NaN, false, "UNRESOLVED",
                new ArrayList<>(), status, diag);
    }
}
