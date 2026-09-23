package dsc.core;

/**
 * Temperature window on a segment. Bounds are kept both as canonical Kelvin and
 * as the original textual representation with their original unit.
 */
public record Interval(Quantity start, Quantity end) {

    public double startK() { return start.toBase(); }

    public double endK() { return end.toBase(); }

    public boolean covers(double kelvin) {
        double lo = Math.min(startK(), endK());
        double hi = Math.max(startK(), endK());
        return kelvin >= lo && kelvin <= hi;
    }

    public boolean emptyAgainst(RawSeries series) {
        int n = 0;
        double[] t = series.tK();
        for (double v : t) {
            if (covers(v)) {
                n++;
            }
        }
        return n == 0;
    }
}
