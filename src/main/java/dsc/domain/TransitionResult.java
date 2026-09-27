package dsc.domain;

import java.util.List;

/**
 * Computed characterization of one transition against one adopted baseline.
 * Every displayed number is reported in canonical units (K, W, J/g) and keeps
 * a stable identity plus references to the exact versions it derives from.
 */
public record TransitionResult(
        String id,
        String intervalId,
        String segmentId,
        String baselineCandidateId,
        TransitionType type,
        Double onsetK,
        Double peakK,
        Double endsetK,
        Double peakFlowW,
        Double stepHeightWK,
        Double midpointK,
        Double specificEnthalpyJG,
        ThermalSign sign,
        EnthalpyErrorSources errorSources,
        List<String> diagnostics,
        int massVersion,
        int rateVersion,
        int calibrationVersion,
        int rawBatchVersion,
        int tangentVersionSet,
        String dataHash) {

    /** Whether the value is complete and usable (no blocking diagnostics). */
    public boolean valid() {
        return diagnostics == null || diagnostics.stream().noneMatch(d -> d.startsWith("BLOCKER:"));
    }
}
