package dsc.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a raw scan into heating/cooling segments. A sign change of dT/dt
 * starts a new segment; each full heat/cool pair increments the cycle counter
 * (cycle segment switching).
 */
public final class Segmentation {

    private Segmentation() {}

    public static List<Segment> segment(List<DscPoint> points) {
        List<Segment> out = new ArrayList<>();
        if (points.size() < 2) return out;
        List<DscPoint> current = new ArrayList<>();
        current.add(points.get(0));
        Segment.Kind kind = null;
        int cycle = 0;
        for (int i = 1; i < points.size(); i++) {
            double dT = points.get(i).tempC() - points.get(i - 1).tempC();
            Segment.Kind stepKind = dT >= 0 ? Segment.Kind.HEATING : Segment.Kind.COOLING;
            if (kind == null) {
                kind = stepKind;
                cycle = 1;
            } else if (stepKind != kind) {
                out.add(new Segment(out.size(), kind, cycle, List.copyOf(current)));
                current = new ArrayList<>();
                if (stepKind == Segment.Kind.HEATING) cycle++;
                kind = stepKind;
            }
            current.add(points.get(i));
        }
        if (!current.isEmpty()) {
            out.add(new Segment(out.size(), kind, cycle, List.copyOf(current)));
        }
        return out;
    }
}
