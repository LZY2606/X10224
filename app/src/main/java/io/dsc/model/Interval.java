package io.dsc.model;

/**
 * Reviewer-draggable interval. Each interval lives on exactly one segment:
 * an interval spanning a heating/cooling boundary (cycle switch) is stored but
 * invalid, never silently split or merged.
 */
public record Interval(
        StableId id,
        String caseId,
        String slot,            // stable slot name, e.g. slot:cool-cryst
        String segmentId,
        double fromK,           // current (possibly calibrated) coordinates
        double toK,
        double rawFromK,        // coordinates as dragged, pre calibration
        double rawToK,
        Status status,
        String diagnosticId     // null when VALID
) {
    public enum Status { VALID, EMPTY, CROSSES_SEGMENT, EMPTY_OVERLAP, DRAFT }

    public Interval withCoordinates(double newFromK, double newToK, Status status, String diagnosticId) {
        return new Interval(id, caseId, slot, segmentId, newFromK, newToK, rawFromK, rawToK, status, diagnosticId);
    }

    public boolean crossesSegment(String otherSegmentId) {
        return !this.segmentId.equals(otherSegmentId);
    }
}
