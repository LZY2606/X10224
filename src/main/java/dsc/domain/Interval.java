package dsc.domain;

import java.util.Objects;

/** Temperature interval in degrees Celsius with a stable coordinate basis. */
public final class Interval {
    public final double startC;
    public final double endC;

    public Interval(double startC, double endC) {
        this.startC = startC;
        this.endC = endC;
    }

    public boolean empty() { return !(endC > startC); }
    public boolean contains(double t) { return t >= Math.min(startC, endC) && t <= Math.max(startC, endC); }
    public double width() { return Math.abs(endC - startC); }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Interval i)) return false;
        return Double.compare(i.startC, startC) == 0 && Double.compare(i.endC, endC) == 0;
    }
    @Override public int hashCode() { return Objects.hash(startC, endC); }
    @Override public String toString() { return "[" + startC + "," + endC + "] C"; }
}
