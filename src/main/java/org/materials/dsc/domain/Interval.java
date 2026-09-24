package org.materials.dsc.domain;

/**
 * An analysis window on a segment, in normalized °C.  Draggable in the UI;
 * every drag creates a new tangent/interval version, never an overwrite.
 */
public final class Interval {
    public final double startC;
    public final double endC;

    public Interval(double startC, double endC) {
        this.startC = startC;
        this.endC = endC;
    }

    public boolean contains(double t) {
        return t >= startC && t <= endC;
    }

    public double width() {
        return Math.abs(endC - startC);
    }

    public boolean isEmpty() {
        return width() == 0.0;
    }

    public boolean overlaps(Interval other) {
        return startC < other.endC && other.startC < endC;
    }

    public List<double[]> overlapRange(Interval other) {
        double lo = Math.max(startC, other.startC);
        double hi = Math.min(endC, other.endC);
        List<double[]> r = new java.util.ArrayList<>();
        if (lo < hi) {
            r.add(new double[] { lo, hi });
        }
        return r;
    }
}
