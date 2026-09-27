package dsc.domain;

import java.util.List;

/**
 * One legally admissible baseline for an interval. When multiple baselines are
 * reasonable (overlapped transitions), all of them are retained; adopting one
 * never deletes the others.
 */
public record BaselineCandidate(
        String id,
        String intervalId,
        BaselineKind kind,
        String label,
        String preTangentId,
        String postTangentId,
        double slopeWK,
        double offsetW,
        double preSlopeWK,
        double postSlopeWK,
        double preOffsetW,
        double postOffsetW,
        List<String> diagnostics) {

    /** Baseline heat flow (W) at temperature T (K). */
    public double baselineAtK(double tK) {
        if (kind == BaselineKind.STEP_TANGENT) {
            throw new IllegalStateException("step baseline needs a junction temperature");
        }
        return slopeWK * tK + offsetW;
    }

    /** Step (glass) baseline: pre tangent left of junction, post tangent right. */
    public double stepAtK(double tK, double junctionK) {
        if (kind != BaselineKind.STEP_TANGENT) {
            return baselineAtK(tK);
        }
        return tK < junctionK ? preSlopeWK * tK + preOffsetW : postSlopeWK * tK + postOffsetW;
    }
}
