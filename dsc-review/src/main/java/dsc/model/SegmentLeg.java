package dsc.model;

/**
 * A monotone leg of a (possibly cyclic) instrument sequence. A trace that
 * turns around mid-file contains several legs; heating and cooling legs must
 * never be mixed inside one integration.
 */
public record SegmentLeg(String legId, String sequenceId, SegmentDirection direction,
                         int fromIndex, int toIndex, double startC, double endC) {}
