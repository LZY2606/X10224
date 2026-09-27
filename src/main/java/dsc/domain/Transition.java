package dsc.domain;

/**
 * A user- or detector-marked thermal transition living on one segment.
 * Heating and cooling transitions are always kept on their own segment;
 * no baseline mixes the two (NO_HEAT_COOL_MIX rule).
 */
public record Transition(
        String id,
        String runId,
        String segmentId,
        int ord,
        String label,
        TransitionKind kindHint,
        int regionFromOrd,
        int regionToOrd,
        ScanDirection segmentDirection) {
}
