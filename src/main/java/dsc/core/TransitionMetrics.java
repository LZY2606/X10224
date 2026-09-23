package dsc.core;

/**
 * Onset / peak / endset for the event. Onset/endset are intersections of the
 * peak tangents with the baseline; peak is the residual extremum. Glass
 * transitions report step temperatures instead of onset/endset.
 */
public record TransitionMetrics(
        String id,
        String kind,
        double onsetK,
        double peakK,
        double endsetK,
        double tgK,
        double deltaCpJgK,
        boolean valid,
        String note
) {
}
