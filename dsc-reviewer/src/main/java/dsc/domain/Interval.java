package dsc.domain;

/**
 * An analyst-selected temperature interval [startK, endK] within one segment.
 * Temperature is always stored in the canonical basis (K). Intervals are
 * "reversed-valid": {@link #normalized()} sorts the bounds so dragging a window
 * right-to-left cannot flip a sign silently.
 */
public record Interval(double startK, double endK) {

    public Interval {
        if (Double.isNaN(startK) || Double.isNaN(endK)) {
            throw new IllegalArgumentException("interval bounds must be numeric");
        }
    }

    public Interval normalized() {
        return startK <= endK ? this : new Interval(endK, startK);
    }

    public boolean contains(double temperatureK) {
        return temperatureK >= startK && temperatureK <= endK;
    }

    public double widthK() {
        return endK - startK;
    }
}
