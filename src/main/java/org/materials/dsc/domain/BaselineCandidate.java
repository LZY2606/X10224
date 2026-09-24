package org.materials.dsc.domain;

import java.util.List;

/**
 * 一条基线候选：在区间 [loIndex, hiIndex] 上由左右切线窗口拟合出的线性基线。
 * 多个合理基线同时保留差异，直到人工裁定；未被采纳的候选不删除。
 */
public record BaselineCandidate(String id, String transitionId, String traceId, String segmentId,
                                String origin, int loIndex, int hiIndex,
                                double leftX1, double leftX2, double rightX1, double rightX2,
                                double slope, double intercept,
                                double rmsResidual, double quality,
                                boolean adopted, String supersededBy,
                                String eventSeq, String ruleVersion,
                                List<String> diagnostics) {
    public double valueAtCelsius(double tCelsius) {
        return slope * tCelsius + intercept;
    }
}
