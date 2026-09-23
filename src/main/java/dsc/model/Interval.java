package dsc.model;

/**
 * A reviewer-draggable temperature window on one segment. Intervals are first-class:
 * tangents attach to them and baselines are computed inside them.
 */
public final class Interval {
    public final String id;
    public final String segmentId;
    public final String label;
    /** Window bounds in calibrated K. */
    public final double startK;
    public final double endK;
    public final int version;

    public Interval(String id, String segmentId, String label, double startK, double endK, int version) {
        if (endK <= startK) {
            throw new IllegalArgumentException("interval " + id + ": end must be greater than start");
        }
        this.id = id;
        this.segmentId = segmentId;
        this.label = label;
        this.startK = startK;
        this.endK = endK;
        this.version = version;
    }

    public boolean contains(double tempK) {
        return tempK >= startK && tempK <= endK;
    }
}
