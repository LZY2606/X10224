package io.dsc.model;

import java.util.List;

/**
 * A legitimate baseline fit for an interval. Multiple reasonable candidates
 * coexist with their differences preserved; a candidate is adopted only by an
 * explicit decision, and rejected/unchosen candidates are never deleted.
 */
public record BaselineCandidate(
        StableId id,             // stable display identity, e.g. bl:cool-cryst:linear:v1
        String caseId,
        String slot,
        String method,           // linear / chord / glass-tangent
        int versionNo,
        double slopeWPerK,
        double interceptW,
        double secondSlopeWPerK, // glass-tangent second arm
        double secondInterceptW,
        double joinK,            // glass-tangent join coordinate
        double rmsResidual,
        Status status,
        boolean adopted,
        String rejectedBecause,
        List<String> diagnosticIds,
        Derivation derivation
) {
    public enum Status { CURRENT, SUPERSEDED, EXPIRED, REJECTED, AMBIGUOUS }

    public double valueAt(double tK) {
        if ("glass-tangent".equals(method) && tK >= joinK) {
            return secondSlopeWPerK * tK + secondInterceptW;
        }
        return slopeWPerK * tK + interceptW;
    }

    public BaselineCandidate markAdopted() {
        return new BaselineCandidate(id, caseId, slot, method, versionNo, slopeWPerK, interceptW,
                secondSlopeWPerK, secondInterceptW, joinK, rmsResidual, status, true,
                rejectedBecause, diagnosticIds, derivation);
    }

    public BaselineCandidate markRejected(String reason) {
        return new BaselineCandidate(id, caseId, slot, method, versionNo, slopeWPerK, interceptW,
                secondSlopeWPerK, secondInterceptW, joinK, rmsResidual, Status.REJECTED, false,
                reason, diagnosticIds, derivation);
    }
}
