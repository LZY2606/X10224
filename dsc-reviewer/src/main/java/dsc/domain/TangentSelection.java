package dsc.domain;

/**
 * The analyst-selected tangents, archived separately as their own source
 * record. For {@link BaselineModel#LINEAR_TANGENT} the two windows flank the
 * event; for {@link BaselineModel#STEP_TANGENT} they are the pre-Tg and
 * post-Tg glassy / rubbery tangent windows.
 */
public record TangentSelection(
        String id,
        String segmentId,
        BaselineModel model,
        Interval leftWindow,
        Interval rightWindow
) {
}
