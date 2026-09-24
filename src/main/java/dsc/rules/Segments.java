package dsc.rules;

import dsc.model.Model.Direction;
import dsc.model.Model.Point;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits an instrument run into monotonic temperature segments (cycle switching).
 * Heating and cooling segments are never mixed inside one analysis interval.
 */
public final class Segments {
    private static final double SLOPE_EPS = 1e-9;

    private Segments() {}

    public record RawSegment(Direction direction, List<Point> points) {}

    public static List<RawSegment> split(List<Point> points) {
        List<RawSegment> out = new ArrayList<>();
        if (points.size() < 2) {
            if (!points.isEmpty()) out.add(new RawSegment(Direction.HEATING, List.copyOf(points)));
            return out;
        }
        List<Point> current = new ArrayList<>();
        current.add(points.get(0));
        Direction dir = null;
        for (int i = 1; i < points.size(); i++) {
            Point prev = points.get(i - 1);
            Point cur = points.get(i);
            double dT = cur.tempRaw() - prev.tempRaw();
            Direction stepDir = dT >= 0 ? Direction.HEATING : Direction.COOLING;
            if (Math.abs(dT) < SLOPE_EPS) stepDir = dir != null ? dir : Direction.HEATING;
            if (dir == null) {
                dir = stepDir;
                current.add(cur);
            } else if (stepDir == dir) {
                current.add(cur);
            } else {
                out.add(new RawSegment(dir, List.copyOf(current)));
                current = new ArrayList<>();
                current.add(prev); // boundary point shared by both segments
                current.add(cur);
                dir = stepDir;
            }
        }
        if (!current.isEmpty()) out.add(new RawSegment(dir, List.copyOf(current)));
        return out;
    }

    /** An interval is valid only if it lies fully inside one segment: heating/cooling never mix. */
    public static boolean intervalInsideSegment(RawSegment seg, double tStart, double tEnd) {
        double lo = Math.min(seg.points().get(0).tempRaw(),
                seg.points().get(seg.points().size() - 1).tempRaw());
        double hi = Math.max(seg.points().get(0).tempRaw(),
                seg.points().get(seg.points().size() - 1).tempRaw());
        return tStart >= lo - 1e-9 && tEnd <= hi + 1e-9 && tStart < tEnd;
    }

    /** Points of the segment whose (raw) temperature falls inside [tStart, tEnd]. */
    public static List<Point> window(RawSegment seg, double tStart, double tEnd) {
        List<Point> out = new ArrayList<>();
        for (Point p : seg.points()) {
            if (p.tempRaw() >= tStart - 1e-9 && p.tempRaw() <= tEnd + 1e-9) out.add(p);
        }
        return out;
    }
}
