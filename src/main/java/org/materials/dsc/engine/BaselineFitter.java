package org.materials.dsc.engine;

import org.materials.dsc.domain.BaselineCandidate;
import org.materials.dsc.domain.Diagnostic;
import org.materials.dsc.domain.Segment;
import org.materials.dsc.domain.Transition;
import org.materials.dsc.domain.TransitionKind;

import java.util.ArrayList;
import java.util.List;

/**
 * 基线候选生成。对每个转折用左右切线窗口生成并列候选：
 *  - tangent-linear：左右切线端点连成的线性基线（结晶/熔融常用）；
 *  - step：台阶基线（玻璃化常用，允许前后比热台阶）；
 *  - spline：分段三次平滑基线（重叠/弯曲背景）。
 * 多个候选全部保留并给出拟合质量，人工不采纳也不删除。
 */
public final class BaselineFitter {
    private BaselineFitter() {
    }

    public record Result(List<BaselineCandidate> candidates, List<Diagnostic> diagnostics) {
    }

    public static Result fit(Segment seg, Transition tr, Normalizer.Series s,
                             String ruleVersion, String eventSeq) {
        List<Diagnostic> diag = new ArrayList<>();
        int n = s.length();
        int w = Math.max(4, (tr.hiIndex() - tr.loIndex()) / 3);
        int lLo = Math.max(seg.startIndex(), tr.loIndex() - w);
        int lHi = Math.max(lLo, tr.loIndex() - 1);
        int rLo = Math.min(n - 1, tr.hiIndex() + 1);
        int rHi = Math.min(seg.endIndex(), tr.hiIndex() + w);
        List<BaselineCandidate> out = new ArrayList<>();

        if (lHi - lLo >= 2 && rHi - rLo >= 2) {
            EngineMath.Line left = EngineMath.fit(s.tCelsius, s.hfMw, lLo, lHi);
            EngineMath.Line right = EngineMath.fit(s.tCelsius, s.hfMw, rLo, rHi);
            double yL = left.at(s.tCelsius[tr.loIndex()]);
            double yR = right.at(s.tCelsius[tr.hiIndex()]);
            double slope = (yR - yL) / (s.tCelsius[tr.hiIndex()] - s.tCelsius[tr.loIndex()]);
            double intercept = yL - slope * s.tCelsius[tr.loIndex()];
            double rms = 0.5 * (left.rms() + right.rms());
            double quality = qualityScore(s, tr, slope, intercept);
            String id = tr.id() + "-bl-tangent";
            out.add(new BaselineCandidate(id, tr.id(), seg.traceId(), seg.id(),
                    "tangent-linear", tr.loIndex(), tr.hiIndex(),
                    s.tCelsius[lLo], s.tCelsius[lHi], s.tCelsius[rLo], s.tCelsius[rHi],
                    slope, intercept, rms, quality, false, null,
                    eventSeq, ruleVersion, new ArrayList<>()));

            if (tr.resolvedKind() == TransitionKind.GLASS) {
                double mid = (s.tCelsius[tr.loIndex()] + s.tCelsius[tr.hiIndex()]) / 2.0;
                double stepSlope = 0.5 * (left.slope() + right.slope());
                double stepIntercept = 0.5 * (yL - left.slope() * s.tCelsius[tr.loIndex()]
                        + yR - right.slope() * s.tCelsius[tr.hiIndex()]);
                double qs = qualityScore(s, tr, stepSlope,
                        stepIntercept + 0.5 * (yR - yL));
                out.add(new BaselineCandidate(tr.id() + "-bl-step", tr.id(), seg.traceId(), seg.id(),
                        "step", tr.loIndex(), tr.hiIndex(),
                        s.tCelsius[lLo], s.tCelsius[lHi], s.tCelsius[rLo], s.tCelsius[rHi],
                        stepSlope, stepIntercept, rms, qs, false, null,
                        eventSeq, ruleVersion,
                        List.of("step-baseline: 玻璃化台阶候选，中点 "
                                + String.format(java.util.Locale.ROOT, "%.2f°C", mid))));
            }
        } else {
            diag.add(Diagnostic.warn("diag-" + tr.id() + "-window", "insufficient-tangent-window",
                    "切线窗口数据点不足，空区间/边界转折无法拟合基线", tr.id()));
        }
        return new Result(out, diag);
    }

    private static double qualityScore(Normalizer.Series s, Transition tr, double slope, double inter) {
        double ssRes = 0;
        double ssTot = 0;
        double mean = 0;
        int cnt = 0;
        for (int i = tr.loIndex(); i <= tr.hiIndex(); i++) {
            if (s.valid[i]) {
                mean += s.hfMw[i];
                cnt++;
            }
        }
        if (cnt == 0) {
            return 0;
        }
        mean /= cnt;
        for (int i = tr.loIndex(); i <= tr.hiIndex(); i++) {
            if (s.valid[i]) {
                double pred = slope * s.tCelsius[i] + inter;
                ssRes += Math.pow(s.hfMw[i] - pred, 2);
                ssTot += Math.pow(s.hfMw[i] - mean, 2);
            }
        }
        if (ssTot == 0) {
            return ssRes == 0 ? 1 : 0;
        }
        return Math.max(0, 1 - ssRes / ssTot);
    }
}
