package dsc.model;

import java.util.List;

/**
 * One independently versioned baseline candidate. Multiple legal candidates
 * coexist; only one is ADOPTED at a time and rejected/adoption never deletes
 * the others. Coefficients are in the calibrated coordinate basis.
 */
public record BaselineCandidate(
        String candidateId,
        String intervalId,
        String sequenceId,
        String tangentId,
        BaselineType type,
        List<Double> coeffs,            // valueC = c0 + c1*x (+ c2*x^2 ...) for LINEAR/POLY
        double fitRmseMW,
        double tgMidC, double tgWidthC, // SIGMOID_STEP parameters, degC
        CandidateStatus status,
        int version,
        long basedOnEventSeq,
        List<Diagnostic> diagnostics
) {
    public BaselineCandidate withStatus(CandidateStatus s) {
        return new BaselineCandidate(candidateId, intervalId, sequenceId, tangentId, type, coeffs,
                fitRmseMW, tgMidC, tgWidthC, s, version, basedOnEventSeq, diagnostics);
    }
}
