package dsc.rules;

import dsc.domain.Models.EventKind;

/** 用户/脚本在曲线上选择的可拖动区间（K 坐标）。 */
public record IntervalSpec(
        String id,
        String runId,
        String label,
        double startK,
        double endK,
        EventKind expectedKind,
        double leftWindowK,
        double rightWindowK,
        boolean userTangentsPreferred) {

    public IntervalSpec withBounds(double newStartK, double newEndK) {
        return new IntervalSpec(id, runId, label, newStartK, newEndK, expectedKind,
                leftWindowK, rightWindowK, userTangentsPreferred);
    }
}
