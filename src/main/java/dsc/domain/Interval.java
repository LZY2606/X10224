package dsc.domain;

/**
 * A draggable temperature window on exactly one segment. Heating and cooling
 * segments are never mixed inside one interval; a window spanning segments is
 * rejected with a diagnostic.
 */
public record Interval(String id, String segmentId, double startK, double endK) {

    public Interval {
        if (!(endK > startK)) {
            throw new IllegalArgumentException("interval must have endK > startK");
        }
    }

    public boolean containsK(double tK) {
        return tK >= startK && tK <= endK;
    }
}
