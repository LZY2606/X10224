package dsc.core;

import java.util.Locale;

/**
 * A draggable analysis interval on one segment, anchored in the temperature
 * coordinate frame of that segment (Kelvin canonical).
 */
public record Interval(String id, String segmentId, double leftK, double rightK) {

    public Interval {
        if (rightK < leftK) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT,
                            "interval %s inverted: %.3f > %.3f", id, leftK, rightK));
        }
    }

    public boolean empty() { return rightK == leftK; }
    public double widthK() { return rightK - leftK; }
    public boolean contains(double tK) { return tK >= leftK && tK <= rightK; }
    public Interval moved(double newLeft, double newRight) {
        return new Interval(id, segmentId, newLeft, newRight);
    }
}
