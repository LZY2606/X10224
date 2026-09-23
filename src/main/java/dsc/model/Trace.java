package dsc.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed instrument curve for one segment. Parsing is representation preserving:
 * original token strings and units are kept; diagnostics record anything that could not
 * be converted rather than dropping the point.
 */
public final class Trace {
    public static final class Point {
        public final int index;
        public final String tempRaw;
        public final String hfRaw;
        /** Temperature in K, or NaN if the point could not be converted. */
        public final double tempK;
        /** Heat flow in W, or NaN if the point could not be converted. */
        public final double heatFlowW;

        public Point(int index, String tempRaw, String hfRaw, double tempK, double heatFlowW) {
            this.index = index;
            this.tempRaw = tempRaw;
            this.hfRaw = hfRaw;
            this.tempK = tempK;
            this.heatFlowW = heatFlowW;
        }
    }

    public final String segmentId;
    public final Types.HeatFlowConvention convention;
    public final Types.ScanDirection direction;
    public final double calibrationOffsetK;
    public final List<Point> points;
    public final List<String> diagnostics;
    public final boolean nonUniformSpacing;

    private Trace(String segmentId, Types.HeatFlowConvention convention, Types.ScanDirection direction,
                  double calibrationOffsetK, List<Point> points, List<String> diagnostics,
                  boolean nonUniformSpacing) {
        this.segmentId = segmentId;
        this.convention = convention;
        this.direction = direction;
        this.calibrationOffsetK = calibrationOffsetK;
        this.points = points;
        this.diagnostics = diagnostics;
        this.nonUniformSpacing = nonUniformSpacing;
    }

    public int size() {
        return points.size();
    }

    /** Calibrated temperature in K (raw converted value + temperature calibration offset). */
    public double calibratedTempK(int i) {
        double t = points.get(i).tempK;
        return Double.isNaN(t) ? Double.NaN : t + calibrationOffsetK;
    }

    /** Heat flow normalised so positive always = endothermic (into sample), in W. */
    public double endoUpHeatFlowW(int i) {
        double h = points.get(i).heatFlowW;
        if (Double.isNaN(h)) return Double.NaN;
        return convention == Types.HeatFlowConvention.ENDO_UP ? h : -h;
    }

    /**
     * Parses raw CSV ("temperature,heatFlow" rows plus an optional header). {@code tempUnit}
     * and {@code hfUnit} declare the units of the numeric columns. Points that fail to parse
     * are retained with NaN canonical values and a diagnostic; the original text survives.
     */
    public static Trace parse(String segmentId, String rawText, String tempUnit, String hfUnit,
                              Types.HeatFlowConvention convention, Types.ScanDirection direction,
                              double calibrationOffsetK) {
        List<Point> points = new ArrayList<>();
        List<String> diag = new ArrayList<>();
        String[] lines = rawText.split("\\r?\\n");
        int idx = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split("[,;\\t]+");
            if (parts.length < 2) {
                diag.add("line " + (idx + 1) + ": expected 2 columns, kept raw: " + trimmed);
                continue;
            }
            String a = parts[0].trim();
            String b = parts[1].trim();
            if (idx == 0 && !(a.chars().anyMatch(Character::isDigit))) {
                continue; // header row
            }
            Physical tp = new Physical(a, tempUnit, "ITS-90");
            Physical hp = new Physical(b, hfUnit, "instrument");
            double t = tp.toCanonical().orElse(Double.NaN);
            double h = hp.toCanonical().orElse(Double.NaN);
            if (Double.isNaN(t)) diag.add("point " + idx + ": temperature '" + a + " "
                    + tempUnit + "' not convertible; kept in place");
            if (Double.isNaN(h)) diag.add("point " + idx + ": heat flow '" + b + " "
                    + hfUnit + "' not convertible; kept in place");
            points.add(new Point(idx, a, b, t, h));
            idx++;
        }
        boolean nonUniform = detectNonUniform(points);
        if (nonUniform) diag.add("temperature sampling is non-uniform; trapezoid integration uses actual dT");
        return new Trace(segmentId, convention, direction, calibrationOffsetK, points, diag, nonUniform);
    }

    private static boolean detectNonUniform(List<Point> points) {
        double prev = Double.NaN;
        double prevStep = Double.NaN;
        for (Point p : points) {
            if (Double.isNaN(p.tempK)) continue;
            if (!Double.isNaN(prev)) {
                double step = Math.abs(p.tempK - prev);
                if (step <= 1e-12) return true; // repeated/zero spacing
                if (!Double.isNaN(prevStep) && Math.abs(step - prevStep) / Math.max(step, prevStep) > 0.25) {
                    return true;
                }
                prevStep = step;
            }
            prev = p.tempK;
        }
        return false;
    }
}
