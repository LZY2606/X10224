package dsc.rules;

import dsc.model.CurvePoint;
import dsc.model.ParsedCurve;
import dsc.model.Segment;
import dsc.model.StableIds;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits an instrument sequence into monotonic heating/cooling runs. Direction is
 * derived from the measured temperature trend; a declaration that disagrees with the
 * trend is reported, and an empty curve yields no segment.
 */
public final class Segmenter {

    public static final String RULE_VERSION = "segment/v2";

    private Segmenter() {
    }

    public record Result(List<Segment> segments, List<String> diagnostics) {
        public Result {
            segments = segments == null ? List.of() : List.copyOf(segments);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }
    }

    public static Result segment(ParsedCurve curve, String instrumentSourceId) {
        List<String> diagnostics = new ArrayList<>();
        List<Segment> segments = new ArrayList<>();
        List<CurvePoint> pts = curve.points();
        if (pts.isEmpty()) {
            return new Result(segments, List.of("NO_POINTS"));
        }
        int runStart = 0;
        int heatCount = 0;
        int coolCount = 0;
        int runIndex = 0;
        for (int i = 1; i <= pts.size(); i++) {
            boolean end = i == pts.size();
            double prev = pts.get(i - 1).temperature().canonical() == null ? Double.NaN
                    : pts.get(i - 1).temperature().canonical();
            double cur = end ? prev
                    : (pts.get(i).temperature().canonical() == null ? Double.NaN
                    : pts.get(i).temperature().canonical());
            int sign = (!Double.isFinite(prev) || !Double.isFinite(cur)) ? 0
                    : Double.compare(cur, prev);
            if (sign > 0) {
                heatCount++;
            } else if (sign < 0) {
                coolCount++;
            }
            if (end) {
                // close run
            } else if (i > runStart && flips(pts, runStart, i)) {
                Segment seg = build(instrumentSourceId, runIndex, pts, runStart, i, curve, diagnostics);
                if (seg != null) {
                    segments.add(seg);
                    runIndex++;
                }
                runStart = i;
            }
        }
        Segment last = build(instrumentSourceId, runIndex, pts, runStart, pts.size(), curve, diagnostics);
        if (last != null) {
            segments.add(last);
        }
        if (heatCount > 0 && coolCount > 0) {
            diagnostics.add("CYCLE_SEGMENTS_DETECTED:" + segments.size());
        }
        return new Result(segments, diagnostics);
    }

    private static boolean flips(List<CurvePoint> pts, int runStart, int at) {
        double first = value(pts, runStart);
        double prev = value(pts, at - 1);
        double cur = value(pts, at);
        if (!Double.isFinite(first) || !Double.isFinite(prev) || !Double.isFinite(cur)) {
            return false;
        }
        return (prev - first) * (cur - prev) < 0;
    }

    private static double value(List<CurvePoint> pts, int i) {
        Double v = pts.get(i).temperature().canonical();
        return v == null ? Double.NaN : v;
    }

    private static Segment build(String sourceId, int runIndex, List<CurvePoint> pts,
                                 int from, int to, ParsedCurve curve, List<String> diagnostics) {
        if (to - from < 2) {
            return null;
        }
        Double first = pts.get(from).temperature().canonical();
        Double last = pts.get(to - 1).temperature().canonical();
        if (first == null || last == null || Math.abs(last - first) < 1e-9) {
            return null;
        }
        String detected = last > first ? "HEATING" : "COOLING";
        String cycle = (detected.equals("HEATING") ? "H" : "C") + (runIndex + 1);
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double maxGap = 0;
        double minGap = Double.POSITIVE_INFINITY;
        for (int i = from; i < to; i++) {
            Double t = pts.get(i).temperature().canonical();
            if (t == null) {
                continue;
            }
            min = Math.min(min, t);
            max = Math.max(max, t);
            if (i > from) {
                Double p = pts.get(i - 1).temperature().canonical();
                if (p != null) {
                    double gap = Math.abs(t - p);
                    if (gap > 1e-9) {
                        maxGap = Math.max(maxGap, gap);
                        minGap = Math.min(minGap, gap);
                    }
                }
            }
        }
        boolean nonUniform = Double.isFinite(minGap) && maxGap / Math.max(minGap, 1e-9) > 2.0;
        if (nonUniform) {
            diagnostics.add("NONUNIFORM_TEMPERATURE_SPACING:" + cycle + ":maxMinGapRatio="
                    + String.format("%.2f", maxGap / minGap));
        }
        if (!curve.declaredScanDirection().equals("ABSENT")
                && !curve.declaredScanDirection().equals(detected)) {
            diagnostics.add("DECLARED_DIRECTION_CONFLICTS_TREND:" + cycle
                    + ":declared=" + curve.declaredScanDirection() + ",trend=" + detected);
        }
        String id = StableIds.of("seg", sourceId + "|" + from + "|" + to + "|" + RULE_VERSION);
        return new Segment(id, cycle, detected, curve.declaredScanDirection(), from, to,
                min, max, nonUniform);
    }
}
