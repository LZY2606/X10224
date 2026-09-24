package dsc.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * 基线引擎：在用户拖动区间上并列产生全部合理候选。
 *  - TANGENT_LINE：区间两侧外缘切线（玻璃化也适用）；
 *  - SIGMOID_STEP：玻璃化阶梯基线；
 *  - PIECEWISE_OVERLAP：区间内存在第二个局部转折时的分段基线；
 *  - 用户切线（USER_TANGENT 来源）作为额外候选并标注 origin=USER_TANGENT。
 * 空区间不抛异常，返回 INVALID 诊断。
 */
public final class BaselineEngine {

    public record UserTangent(double t0K, double y0, double t1K, double y1,
                              String sourceId, int sourceVersion) {
    }

    public BaselineResult build(String intervalId, NormalizedCurve curve,
                                double t0K, double t1K, RuleSet rules,
                                List<UserTangent> userTangents) {
        List<String> diags = new ArrayList<>();
        List<NormPoint> pts = curve.points();

        int n = CurveMath.countPoints(pts, t0K, t1K);
        double span = CurveMath.span(t0K, t1K);
        if (n == 0 || span <= 0) {
            diags.add("EMPTY_INTERVAL: 区间 [" + String.format("%.2f", t0K) + ","
                    + String.format("%.2f", t1K) + "]K 内无有效采样点（有效点数=0 或宽度=0），不产生基线");
            return new BaselineResult(intervalId, curve.segmentId(), t0K, t1K,
                    List.of(), null, diags, rules.version());
        }
        if (n < 3) {
            diags.add("SPARSE_INTERVAL: 区间内仅有 " + n + " 个有效点，基线不确定，候选仅供参考");
        }

        List<BaselineCandidate> cands = new ArrayList<>();
        double win = rules.onsetTangentWindow();

        double[] left = linearFit(pts, t0K - win, t0K);
        double[] right = linearFit(pts, t1K, t1K + win);
        if (left != null && right != null) {
            double a = (right[1] - left[1]) / (right[0] - left[0]);
            double b = left[1] - a * left[0];
            List<Double> line = List.of(a, b);
            double rms = rmsResidual(pts, t0K, t1K, line, List.of(t0K, t1K));
            cands.add(new BaselineCandidate(
                    intervalId + "#tangent", "TANGENT_LINE", "AUTO",
                    line, List.of(t0K, t1K), rms,
                    "以区间两侧 " + win + "K 外缘点做切线连接"));
        }

        double mid = 0.5 * (t0K + t1K);
        double[] pre = linearFit(pts, t0K - win, mid - rules.sigmoidStepWindow());
        double[] post = linearFit(pts, mid + rules.sigmoidStepWindow(), t1K + win);
        if (pre != null && post != null) {
            List<Double> line = List.of(pre[2], pre[3], post[2], post[3]);
            List<Double> knots = List.of(t0K, mid, t1K);
            double rms = rmsResidual(pts, t0K, t1K, line, knots);
            double stepJump = post[1] - pre[1];
            cands.add(new BaselineCandidate(
                    intervalId + "#sigmoid", "SIGMOID_STEP", "AUTO",
                    line, knots, rms,
                    "玻璃化阶梯基线，平台阶跃=" + String.format("%.4f", stepJump) + " W/g"));
        }

        double split = findInnerTurn(pts, t0K, t1K);
        if (!Double.isNaN(split)) {
            double w = rules.overlapSeparationK();
            double[] l1 = linearFit(pts, t0K - win, t0K);
            double[] m = linearFit(pts, split - w / 2, split + w / 2);
            double[] r1 = linearFit(pts, t1K, t1K + win);
            if (l1 != null && m != null && r1 != null) {
                List<Double> line = List.of(l1[2], l1[3], m[2], m[3], r1[2], r1[3]);
                List<Double> knots = List.of(t0K, split, t1K);
                double rms = rmsResidual(pts, t0K, t1K, line, knots);
                cands.add(new BaselineCandidate(
                        intervalId + "#piecewise", "PIECEWISE_OVERLAP", "AUTO",
                        line, knots, rms,
                        "检测到区间内部转折 @" + String.format("%.2f", split)
                                + "K，重叠分离阈值 " + rules.overlapSeparationK() + "K（规则 " + rules.version() + "）"));
            }
        }

        for (UserTangent ut : userTangents) {
            if (ut.t1K() <= ut.t0K()) {
                continue;
            }
            double a = (ut.y1() - ut.y0()) / (ut.t1K() - ut.t0K());
            double b = ut.y0() - a * ut.t0K();
            List<Double> line = List.of(a, b);
            double rms = rmsResidual(pts, t0K, t1K, line, List.of(t0K, t1K));
            cands.add(new BaselineCandidate(
                    intervalId + "#user-" + ut.sourceId(), "TANGENT_LINE", "USER_TANGENT",
                    line, List.of(t0K, t1K), rms,
                    "审阅者选定切线，来源 " + ut.sourceId() + " v" + ut.sourceVersion()));
        }

        if (cands.size() > 1) {
            diags.add("MULTIPLE_BASELINES: 存在 " + cands.size()
                    + " 个合理基线候选，全部保留差异，等待人工选择；系统不擅自合并为单一答案");
        }
        if (cands.isEmpty()) {
            diags.add("NO_BASELINE: 外缘数据不足以拟合任何基线，需扩大区间或提供用户切线");
        }
        return new BaselineResult(intervalId, curve.segmentId(), t0K, t1K,
                cands, null, diags, rules.version());
    }

    /** 最小二乘 [xbar,ybar,a,b]（y=a*x+b）；点不足返回 null。 */
    private static double[] linearFit(List<NormPoint> pts, double lo, double hi) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = 0;
        for (NormPoint p : pts) {
            if (p.excluded() || p.tK() < lo || p.tK() > hi) {
                continue;
            }
            sx += p.tK();
            sy += p.hfPerMass();
            sxx += p.tK() * p.tK();
            sxy += p.tK() * p.hfPerMass();
            n++;
        }
        if (n < 2) {
            return null;
        }
        double den = n * sxx - sx * sx;
        if (Math.abs(den) < 1e-12) {
            return null;
        }
        double a = (n * sxy - sx * sy) / den;
        double b = (sy - a * sx) / n;
        return new double[]{sx / n, sy / n, a, b};
    }

    /** 分段折线在 T 处的值，line 为每段 [a,b] 平铺，knots 含起止。 */
    static double baselineAt(List<Double> line, List<Double> knots, double t) {
        int seg = 0;
        for (int i = 1; i < knots.size() - 1; i++) {
            if (t >= knots.get(i)) {
                seg = i;
            }
        }
        double a = line.get(2 * seg);
        double b = line.get(2 * seg + 1);
        return a * t + b;
    }

    private static double rmsResidual(List<NormPoint> pts, double t0, double t1,
                                      List<Double> line, List<Double> knots) {
        double acc = 0;
        int n = 0;
        for (NormPoint p : pts) {
            if (p.excluded() || p.tK() < t0 || p.tK() > t1) {
                continue;
            }
            double r = p.hfPerMass() - baselineAt(line, knots, p.tK());
            acc += r * r;
            n++;
        }
        return n == 0 ? Double.NaN : Math.sqrt(acc / n);
    }

    /**
     * 找区间内部的额外转折：一阶差分符号变化最强的内点（距边界超过分离阈值的一半）。
     * 无则 NaN。
     */
    private static double findInnerTurn(List<NormPoint> pts, double t0, double t1) {
        double bestT = Double.NaN;
        double bestScore = 0;
        for (int i = 2; i < pts.size() - 2; i++) {
            NormPoint p = pts.get(i);
            if (p.tK() <= t0 || p.tK() >= t1) {
                continue;
            }
            double d1 = pts.get(i).hfPerMass() - pts.get(i - 2).hfPerMass();
            double d2 = pts.get(i + 2).hfPerMass() - pts.get(i).hfPerMass();
            double score = Math.abs(d2 - d1);
            if (score > bestScore && p.tK() - t0 > 3 && t1 - p.tK() > 3) {
                bestScore = score;
                bestT = p.tK();
            }
        }
        return bestT;
    }
}
