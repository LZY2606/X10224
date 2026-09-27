package dsc.domain;

import java.util.List;

/**
 * Onset / peak / endset and integrated enthalpy for one transition under one
 * baseline candidate. All temperatures carry a coordinate basis
 * (raw vs calibration-corrected). Error sources are enumerated, not hidden.
 */
public record AnalysisResult(
        String id,
        String transitionId,
        String baselineCandidateId,
        String derivedVersionId,
        Qty onset,
        Qty peak,
        Qty endset,
        Qty enthalpy,
        Integer endothermicSign,
        List<ErrorContribution> errorBudget,
        String status,
        List<String> diagnostics) {

    public boolean complete() {
        return onset != null && peak != null && endset != null && enthalpy != null;
    }
}
