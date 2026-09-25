package dsc.core;

import dsc.model.Model.Direction;
import dsc.model.Model.RawPoint;
import dsc.model.Model.RawSeries;
import dsc.model.Model.Segment;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a raw run into monotonic scan segments by the sign of dT/dt.
 * Heating and cooling points are never mixed into one segment, so cyclic
 * runs (heat/cool/heat...) yield one segment per leg.
 */
public final class Segments {
    private Segments() {}

    public static List<Segment> split(RawSeries series) {
        List<RawPoint> pts = series.points();
        List<Segment> out = new ArrayList<>();
        if (pts.size() < 2) return out;
        int start = 0;
        Direction current = directionOf(pts, 0);
        int cycle = current == Direction.HEATING ? 1 : 0;
        for (int i = 1; i < pts.size(); i++) {
            Direction d = directionOf(pts, i);
            if (d != current && d != Direction.ISOTHERMAL) {
                if (i - start >= 2) {
                    out.add(new Segment(segmentId(series.id(), out.size()), series.id(),
                            current, cycle, start, i, rateOf(series, start, i)));
                }
                start = i;
                current = d;
                if (d == Direction.HEATING) cycle++;
            }
        }
        if (pts.size() - start >= 2) {
            out.add(new Segment(segmentId(series.id(), out.size()), series.id(),
                    current, cycle, start, pts.size() - 1, rateOf(series, start, pts.size() - 1)));
        }
        return out;
    }

    private static String segmentId(String seriesId, int n) {
        return "seg:" + seriesId.substring(seriesId.indexOf(':') + 1) + "#" + n;
    }

    private static Direction directionOf(List<RawPoint> pts, int i) {
        double dT = pts.get(Math.min(i + 1, pts.size() - 1)).temperature()
                - pts.get(Math.max(i - 1, 0)).temperature();
        double dt = pts.get(Math.min(i + 1, pts.size() - 1)).time()
                - pts.get(Math.max(i - 1, 0)).time();
        if (dt == 0) return Direction.ISOTHERMAL;
        double rate = dT / dt;
        if (rate > 1e-6) return Direction.HEATING;
        if (rate < -1e-6) return Direction.COOLING;
        return Direction.ISOTHERMAL;
    }

    private static double rateOf(RawSeries series, int a, int b) {
        RawPoint p0 = series.points().get(a);
        RawPoint p1 = series.points().get(b);
        double dtMin = (p1.time() - p0.time()) / 60.0;
        if (dtMin == 0) return 0;
        return (p1.temperature() - p0.temperature()) / dtMin;
    }
}
