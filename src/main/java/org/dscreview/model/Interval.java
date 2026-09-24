package org.dscreview.model;

/** A draggable review interval on a segment, expressed in the temperature coordinate basis (degC). */
public record Interval(String intervalId, String segmentId, double fromDegC, double toDegC) {
    public boolean isEmpty() { return toDegC <= fromDegC; }
    public Interval moved(double from, double to) { return new Interval(intervalId, segmentId, from, to); }
}
