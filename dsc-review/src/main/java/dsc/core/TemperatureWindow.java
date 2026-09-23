package dsc.core;

import java.util.Locale;

/**
 * A reviewer-selected temperature window in degrees Celsius (canonical basis).
 * Stored separately from instrument output; dragging the handles on the curve
 * page creates a new parameter revision rather than mutating an old one.
 */
public final class TemperatureWindow {

    private final double startC;
    private final double endC;

    public TemperatureWindow(double startC, double endC) {
        this.startC = startC;
        this.endC = endC;
    }

    public double startC() { return startC; }
    public double endC() { return endC; }
    public boolean empty() {
        double tol = 1e-9;
        return Math.abs(endC - startC) <= tol;
    }

    public boolean containsC(double t) {
        double lo = Math.min(startC, endC);
        double hi = Math.max(startC, endC);
        return t >= lo && t <= hi;
    }

    @Override public String toString() {
        return String.format(Locale.ROOT, "[%.3f, %.3f] C", startC, endC);
    }
}
