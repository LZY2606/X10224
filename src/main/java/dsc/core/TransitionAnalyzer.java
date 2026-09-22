package dsc.core;

import dsc.domain.BaselineResult;
import dsc.domain.DirectionConvention;
import dsc.domain.Segment;
import dsc.domain.TransitionResult;
import dsc.domain.TransitionType;

/**
 * 由基线候选生成转折特征。峰温在 [onset,endset] 内取相对基线偏差最大的点。
 * 方向未决（仪器头缺失）时 signResolved=false；类型已知时可给出方向提示，
 * 但没有方向来源就不落定符号。
 */
public final class TransitionAnalyzer {

    private TransitionAnalyzer() {
    }

    public static TransitionResult analyze(String transitionId, Segment seg, BaselineResult base,
                                           TransitionType suggestedType) {
        if (!base.usable()) {
            return new TransitionResult(transitionId, seg.segmentId, base.candidateId, suggestedType,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, false,
                    "ERROR", "baseline unusable: " + base.diagnostic);
        }
        int peakIdx = -1;
        double bestAbs = -1;
        double signedAtPeak = 0;
        for (int i = 0; i < seg.size(); i++) {
            if (!seg.good[i] || seg.xK[i] < base.onsetK || seg.xK[i] > base.endsetK) {
                continue;
            }
            double dev = seg.yWg[i] - base.baselineAt(seg.xK[i]);
            if (Math.abs(dev) > bestAbs) {
                bestAbs = Math.abs(dev);
                signedAtPeak = dev;
                peakIdx = i;
            }
        }
        if (peakIdx < 0) {
            return new TransitionResult(transitionId, seg.segmentId, base.candidateId, suggestedType,
                    base.onsetK, Double.NaN, base.endsetK, Double.NaN, 0, false,
                    "EMPTY_INTERVAL", "no samples between onset and endset");
        }

        TransitionType type = suggestedType;
        boolean resolved = seg.instrumentSign != null
                && !DirectionConvention.UNRESOLVED.code.equals(seg.instrumentSign);
        int sign = 0;
        if (resolved) {
            if (type == TransitionType.MELTING) {
                sign = +1;
            } else if (type == TransitionType.CRYSTALLIZATION) {
                sign = -1;
            } else {
                sign = signedAtPeak >= 0 ? +1 : -1;
            }
        }
        return new TransitionResult(transitionId, seg.segmentId, base.candidateId, type,
                base.onsetK, seg.xK[peakIdx], base.endsetK, Math.abs(signedAtPeak),
                sign, resolved, "OK", resolved ? null
                : "DIRECTION_UNRESOLVED: peak temperature located, but heat direction cannot be signed");
    }
}
