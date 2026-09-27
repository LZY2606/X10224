package dsc.domain;

import java.util.List;

/**
 * One of potentially several defensible baselines for a transition.
 * Candidates are computed and retained even when not accepted, so a
 * confirmation never erases the alternatives (KEEP_ALL_CANDIDATES rule).
 */
public record BaselineCandidate(
        String id,
        String transitionId,
        String derivedVersionId,
        int rank,
        String method,
        boolean accepted,
        double[] xs,
        double[] ys,
        double fitRms,
        List<String> notes,
        String eventId) {

    public int pointCount() { return xs.length; }
}
