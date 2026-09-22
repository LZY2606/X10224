package dsc.core;

import dsc.domain.BaselineResult;
import dsc.domain.Segment;
import dsc.domain.TangentChoice;
import dsc.domain.TransitionType;

/**
 * 基线引擎（可重算、纯函数）。
 *
 * 峰（熔融/结晶）：前后切线分别对窗口最小二乘；取信号偏离切线最远的过渡点，
 * 在该点做最大斜率线，与前切线交于 onset、与后切线交于 endset。
 * 台阶（玻璃化）：onset/endset 为台阶前后两条切线与中点水平线的交点，
 * midpoint 为半步温度，stepHeight 为两切线在中点处的差。
 *
 * 空区间 / 窗口点数不足 / 窗口越过扫描方向范围时返回 ERROR 基线并带诊断，
 * 调用方保留候选而不是静默丢弃。
 */
public final class BaselineEngine {

    private BaselineEngine() {
    }

    public static BaselineResult compute(String candidateId, Segment seg, TangentChoice tangents,
                                         TransitionType type) {
        WindowIndex pre = WindowIndex.of(seg, tangents.preStartK, tangents.preEndK);
        WindowIndex post = WindowIndex.of(seg, tangents.postStartK, tangents.postEndK);

        if (pre.lo < 0 || post.lo < 0) {
            return error(candidateId, seg, tangents, type,
                    "EMPTY_INTERVAL: tangent window matched no usable points "
                            + describe(tangents, seg));
        }
        LinearFit preFit = LinearFit.fit(seg.xK, seg.yWg, seg.good, pre.lo, pre.hi);
        LinearFit postFit = LinearFit.fit(seg.xK, seg.yWg, seg.good, post.lo, post.hi);
        if (!Double.isFinite(preFit.slope) || !Double.isFinite(postFit.slope)) {
            return error(candidateId, seg, tangents, type,
                    "DEGENERATE_TANGENT: fewer than 2 points in a tangent window");
        }

        int regionLo = pre.hi + 1;
        int regionHi = post.lo - 1;
        if (regionLo > regionHi) {
            // 窗口紧邻：取两个窗口边界之间的全部过渡点（允许少量重叠窗口时仍可判定）
            regionLo = Math.min(pre.hi, post.lo);
            regionHi = Math.max(pre.hi, post.lo);
        }

        if (type.isStep()) {
            return stepBaseline(candidateId, seg, tangents, type, preFit, postFit);
        }
        return peakBaseline(candidateId, seg, tangents, type, preFit, postFit, regionLo, regionHi);
    }

    private static BaselineResult peakBaseline(String candidateId, Segment seg, TangentChoice tangents,
                                               TransitionType type, LinearFit preFit, LinearFit postFit,
                                               int regionLo, int regionHi) {
        // 峰顶 = 对两切线偏离最大的点（内部约定下熔融正、结晶负）
        int peak = -1;
        double bestDev = -1;
        for (int i = regionLo; i <= regionHi && i < seg.size(); i++) {
            if (!seg.good[i]) {
                continue;
            }
            double base = i < (regionLo + regionHi) / 2 ? preFit.at(seg.xK[i]) : postFit.at(seg.xK[i]);
            double dev = Math.abs(seg.yWg[i] - base);
            if (dev > bestDev) {
                bestDev = dev;
                peak = i;
            }
        }
        if (peak < 0) {
            return error(candidateId, seg, tangents, type,
                    "EMPTY_INTERVAL: no transition points between tangent windows");
        }

        // onset：前切线与“前沿拐点最大斜率线”的交点；endset 对后沿对称处理。
        // 加热与冷却方向都适用：只取 |dY/dT| 最大，不假设升温。
        Double[] onsetLine = maxSlopeLine(seg, regionLo, peak);
        Double[] endsetLine = endSlopeLine(seg, peak, regionHi);
        if (onsetLine == null || endsetLine == null) {
            return error(candidateId, seg, tangents, type,
                    "EMPTY_INTERVAL: insufficient points for inflection tangents");
        }
        Double onsetK = lineIntersection(preFit.slope, preFit.intercept, onsetLine[0], onsetLine[1]);
        Double endsetK = lineIntersection(postFit.slope, postFit.intercept, endsetLine[0], endsetLine[1]);
        if (onsetK == null || endsetK == null) {
            return error(candidateId, seg, tangents, type,
                    "PARALLEL_TANGENT: inflection tangent parallel to a baseline tangent, "
                            + "onset/endset undefined");
        }
        if (onsetK > endsetK) {
            double t = onsetK;
            onsetK = endsetK;
            endsetK = t;
        }
        double onsetY = preFit.at(onsetK);
        double endsetY = postFit.at(endsetK);
        String status = "OK";
        String diag = null;
        double segMin = Math.min(seg.xK[seg.firstGood()], seg.xK[seg.lastGood()]);
        double segMax = Math.max(seg.xK[seg.firstGood()], seg.xK[seg.lastGood()]);
        if (onsetK < segMin || endsetK > segMax) {
            status = "AMBIGUOUS";
            diag = "EXTRAPOLATED_ONSET: tangent intersection lies outside measured range; candidate retained";
        }
        double mid = (onsetK + endsetK) / 2.0;
        double intStart = tangents.preEndK;
        double intEnd = tangents.postStartK;
        return new BaselineResult(candidateId, seg.segmentId, tangents.tangentId, type,
                preFit.slope, preFit.intercept, postFit.slope, postFit.intercept,
                onsetK, endsetK, onsetY, endsetY, mid, 0.0, intStart, intEnd, status, diag);
    }

    /** 区间内 |dY/dT| 最大的点处的切线 [slope, intercept]。 */
    private static Double[] maxSlopeLine(Segment seg, int from, int to) {
        int idx = -1;
        double best = -1;
        for (int i = Math.max(1, from); i <= to && i < seg.size() - 1; i++) {
            if (!seg.good[i - 1] || !seg.good[i] || !seg.good[i + 1]) {
                continue;
            }
            double slope = (seg.yWg[i + 1] - seg.yWg[i - 1])
                    / (seg.xK[i + 1] - seg.xK[i - 1]);
            if (Math.abs(slope) > best) {
                best = Math.abs(slope);
                idx = i;
            }
        }
        if (idx < 0) {
            return null;
        }
        double slope = (seg.yWg[idx + 1] - seg.yWg[idx - 1])
                / (seg.xK[idx + 1] - seg.xK[idx - 1]);
        return new Double[]{slope, seg.yWg[idx] - slope * seg.xK[idx]};
    }

    private static Double[] endSlopeLine(Segment seg, int from, int to) {
        return maxSlopeLine(seg, from, Math.min(to, seg.size() - 2));
    }

    private static BaselineResult stepBaseline(String candidateId, Segment seg, TangentChoice tangents,
                                               TransitionType type, LinearFit preFit, LinearFit postFit) {
        // 玻璃化台阶中点取两条切线在窗口相邻边界处的平均；onset/endset 为半步水平线交点。
        double preX = (tangents.preStartK + tangents.preEndK) / 2.0;
        double postX = (tangents.postStartK + tangents.postEndK) / 2.0;
        double preY = preFit.at(preX);
        double postY = postFit.at(postX);
        double half = (postY - preY) / 2.0;
        double midY = preY + half;
        double midpointK = (preX + postX) / 2.0;
        Double onsetK = horizontalIntercept(preFit.slope, preFit.intercept, preY + half / 2.0);
        Double endsetK = horizontalIntercept(postFit.slope, postFit.intercept, postY - half / 2.0);
        if (onsetK == null || endsetK == null) {
            return error(candidateId, seg, tangents, type, "FLAT_TANGENT: glass step onset/endset undefined");
        }
        if (onsetK > endsetK) {
            double t = onsetK;
            onsetK = endsetK;
            endsetK = t;
        }
        return new BaselineResult(candidateId, seg.segmentId, tangents.tangentId, type,
                preFit.slope, preFit.intercept, postFit.slope, postFit.intercept,
                onsetK, endsetK, preFit.at(onsetK), postFit.at(endsetK), midpointK, postY - preY,
                tangents.preEndK, tangents.postStartK, "OK", null);
    }

    private static Double lineIntersection(double m1, double b1, double m2, double b2) {
        if (Math.abs(m1 - m2) < 1e-12) {
            return null;
        }
        return (b2 - b1) / (m1 - m2);
    }

    private static Double horizontalIntercept(double slope, double intercept, double y) {
        if (Math.abs(slope) < 1e-15) {
            return null;
        }
        return (y - intercept) / slope;
    }

    private static String describe(TangentChoice t, Segment seg) {
        return "(pre " + String.format("%.1f-%.1f", t.preStartK, t.preEndK)
                + " K, post " + String.format("%.1f-%.1f", t.postStartK, t.postEndK)
                + " K, segment range " + String.format("%.1f-%.1f",
                seg.xK[seg.firstGood()], seg.xK[seg.lastGood()]) + " K)";
    }

    private static BaselineResult error(String candidateId, Segment seg, TangentChoice tangents,
                                        TransitionType type, String diagnostic) {
        return new BaselineResult(candidateId, seg.segmentId, tangents.tangentId, type,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, "ERROR", diagnostic);
    }
}
