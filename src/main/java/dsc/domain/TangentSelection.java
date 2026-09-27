package dsc.domain;

/**
 * User-selected tangent anchor on a segment: an index range whose fitted
 * slope/offset defines a tangent line. Stored as its own source so tangent
 * revisions invalidate only dependent baselines.
 */
public record TangentSelection(String id, String segmentId, String side, int startIndex, int endIndex) {

    public TangentSelection {
        if (endIndex < startIndex) {
            throw new IllegalArgumentException("tangent range inverted: " + startIndex + ".." + endIndex);
        }
    }
}
