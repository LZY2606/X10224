package dsc.core;

/**
 * One legitimate baseline candidate for an interval. Multiple candidates may
 * remain valid simultaneously; the application keeps their differences and
 * never collapses ambiguity into a single answer.
 */
public record BaselineCandidate(
        String id,
        String intervalId,
        String method,
        double slopeWpK,
        double interceptW,
        double rmseW,
        boolean feasible,
        String rationale) {

    public double at(double tK) { return slopeWpK * tK + interceptW; }
}
