package dsc.model;

import java.util.List;

/**
 * One scan segment (one ramp of one cycle). Heating and cooling segments
 * are never mixed: baselines and integrals are computed per segment.
 */
public record Segment(String id, SegmentKind kind, int cycle,
                      HeatConvention convention, List<Point> points) {
    public double tMin() {
        double m = Double.POSITIVE_INFINITY;
        for (Point p : points) m = Math.min(m, p.temperatureC());
        return m;
    }
    public double tMax() {
        double m = Double.NEGATIVE_INFINITY;
        for (Point p : points) m = Math.max(m, p.temperatureC());
        return m;
    }
}
