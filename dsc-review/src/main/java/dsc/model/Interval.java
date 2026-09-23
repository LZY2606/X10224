package dsc.model;

/**
 * A review interval (draggable on the curve page). Bounds are in degrees
 * Celsius relative to the calibrated trace, pinned to one monotone leg.
 * An empty interval (end <= start) is a legal input that yields a diagnostic
 * instead of an integration.
 */
public record Interval(String intervalId, String sequenceId, String legId,
                       double startC, double endC) {
    public boolean isEmpty() { return endC <= startC; }
    public Interval withBounds(double s, double e) { return new Interval(intervalId, sequenceId, legId, s, e); }
}
