package dsc.calc;

import java.util.List;

/**
 * Scan-direction and cycle-segment analysis.
 *
 * <p>Semantics enforced here:
 * <ul>
 *   <li>Heating vs cooling is decided by the measured temperature trend, cross-checked
 *       against the protocol declaration. The FILE NAME is never authoritative — a name
 *       like "heat2_up.csv" with a falling trend is reported as a reversal conflict.</li>
 *   <li>Heat-flow sign (exo-up / exo-down) comes from the instrument-output convention
 *       field; a filename token ("heating"/"exo") can only raise a warning.</li>
 *   <li>Heating and cooling traces are never mixed into one baseline/integral.</li>
 *   <li>Cycle-segment switches are explicit boundaries with a cycle tag.</li>
 * </ul>
 */
public final class Segmentation {

    public static final String HEATING = "heating";
    public static final String COOLING = "cooling";
    public static final String MIXED = "mixed";

    private Segmentation() {}

    public static final class SegmentInfo {
        public final String direction;
        public final double trendDegCperMin;
        public final boolean declarationMismatch;
        public final String cycle;
        public final int indexFrom;
        public final int indexTo;

        public SegmentInfo(String direction, double trend, boolean mismatch,
                           String cycle, int from, int to) {
            this.direction = direction;
            this.trendDegCperMin = trend;
            this.declarationMismatch = mismatch;
            this.cycle = cycle;
            this.indexFrom = from;
            this.indexTo = to;
        }
    }

    /**
     * Determine direction of a dense trace using the median of local temperature slopes
     * weighted by the declared rate's sign. Robust to a few reversed points.
     */
    public static SegmentInfo classify(Series s, String declaredDirection, String cycle,
                                       double declaredRateKPerMin, List<String> diagnostics) {
        double medSlope = medianLocalSlope(s);
        double trend = medSlope * 60.0; // degC/min if rows were per second is unknown; use sign
        String measured = medSlope > 0 ? HEATING : medSlope < 0 ? COOLING : MIXED;
        boolean mismatch = declaredDirection != null && !declaredDirection.isBlank()
                && !measured.equals(MIXED)
                && !declaredDirection.trim().toLowerCase().equals(measured);
        if (mismatch) {
            diagnostics.add("REVERSAL CONFLICT: protocol declares '" + declaredDirection
                    + "' but measured temperature trend is " + measured
                    + " (median local slope " + fmt(medSlope) + " degC/row). "
                    + "File name ignored; analysis uses measured direction and flags the segment.");
        }
        if (declaredRateKPerMin > 0 && Math.abs(medSlope) > 0
                && Math.abs(Math.abs(medSlope) - declaredRateKPerMin) / Math.max(declaredRateKPerMin, 1e-9) > 0.5) {
            diagnostics.add("declared scan rate " + fmt(declaredRateKPerMin)
                    + " K/min differs from observed point-to-point rate (row basis); "
                    + "integration uses declared rate metadata for time normalization");
        }
        return new SegmentInfo(measured, trend, mismatch, cycle, 0, s.t.length - 1);
    }

    /** Check that an analysis interval stays inside one direction (no heating+cooling mix). */
    public static boolean isSingleDirection(Series s, int from, int to) {
        if (from < 0 || to >= s.t.length || from >= to) return false;
        int pos = 0, neg = 0;
        for (int i = from + 1; i <= to; i++) {
            double d = s.t[i] - s.t[i - 1];
            if (d > 0) pos++;
            else if (d < 0) neg++;
        }
        return pos == 0 || neg == 0;
    }

    static double medianLocalSlope(Series s) {
        if (s.t.length < 2) return 0;
        double[] d = new double[s.t.length - 1];
        for (int i = 1; i < s.t.length; i++) d[i - 1] = s.t[i] - s.t[i - 1];
        java.util.Arrays.sort(d);
        return d[d.length / 2];
    }

    /** Filename heuristic returns only a warning token; never a direction decision. */
    public static String filenameWarning(String filename, String measuredDirection) {
        if (filename == null) return "";
        String lower = filename.toLowerCase();
        boolean saysHeat = lower.contains("heat") || lower.contains("warm") || lower.contains("up");
        boolean saysCool = lower.contains("cool") || lower.contains("cold") || lower.contains("down");
        if (saysHeat && measuredDirection.equals(COOLING)) {
            return "filename suggests heating but trace is cooling — filename not used for sign";
        }
        if (saysCool && measuredDirection.equals(HEATING)) {
            return "filename suggests cooling but trace is heating — filename not used for sign";
        }
        return "";
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.4g", v);
    }
}
