package dsc.model;

import java.util.List;

/**
 * An onset/peak/endset + enthalpy integration for one interval against one
 * baseline candidate. Raw enthalpy (mJ) does not depend on mass; normalized
 * enthalpy (J/g) is a mass-dependent derivation and goes STALE when the mass
 * version changes.
 */
public record TransitionResult(
        String transitionId,
        String candidateId,
        String intervalId,
        String sequenceId,
        TransitionNature nature,
        TransitionKind kind,
        Double onsetC,
        Double peakC,
        Double endsetC,
        Double enthalpyMJ,
        Double enthalpyJPerG,
        double signedAreaMWK,
        int pointCount,
        String massId,
        String rateId,
        String calibrationId,
        CandidateStatus status,
        int version,
        long basedOnEventSeq,
        List<ErrorContribution> errorBudget,
        List<Diagnostic> diagnostics
) {
    public TransitionResult withStatus(CandidateStatus s) {
        return new TransitionResult(transitionId, candidateId, intervalId, sequenceId, nature, kind,
                onsetC, peakC, endsetC, enthalpyMJ, enthalpyJPerG, signedAreaMWK, pointCount,
                massId, rateId, calibrationId, s, version, basedOnEventSeq, errorBudget, diagnostics);
    }

    public TransitionResult withKind(TransitionKind k) {
        return new TransitionResult(transitionId, candidateId, intervalId, sequenceId, nature, k,
                onsetC, peakC, endsetC, enthalpyMJ, enthalpyJPerG, signedAreaMWK, pointCount,
                massId, rateId, calibrationId, status, version, basedOnEventSeq, errorBudget, diagnostics);
    }
}
