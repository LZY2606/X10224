package dsc.domain;

import java.util.List;

/**
 * Result of analysing one interval within one segment against one baseline
 * candidate. Identity ({@code id}) is stable across re-imports because it is a
 * content hash of (segmentId, interval, tangentVersion, baselineCoefficients,
 * ruleVersion) — so identical analysis always has the same id, and changing
 * any dependency changes the id.
 */
public record TransitionResult(
        String id,
        String segmentId,
        Interval interval,
        TransitionType type,
        BaselineFit baseline,
        Double onsetK,
        Double peakK,
        Double endsetK,
        Double peakHeatFlowMW,
        Double areaMilliJ,
        Double enthalpyJPerG,
        List<UncertaintySource> uncertainty,
        List<Diagnostic> diagnostics,
        String ruleVersion,
        String massVersion
) {
    public boolean usable() {
        return diagnostics.stream().noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }
}
