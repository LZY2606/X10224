package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * A monotonic temperature run inside a curve. Heating and cooling segments are
 * never mixed: baselines and integrations are confined to exactly one segment.
 * Cycle switching (heat -> cool -> heat ...) produces one segment per run.
 */
public final class Segment {
    public enum Direction { HEATING, COOLING, ISOTHERMAL }

    public final String id;          // stable: "<curveObjectId>/seg<index>"
    public final int index;          // 0-based order inside the curve (cycle order)
    public final Direction direction;
    public final int fromPoint;      // inclusive
    public final int toPoint;        // exclusive
    public final double tStartC;
    public final double tEndC;

    public Segment(String id, int index, Direction direction, int fromPoint, int toPoint,
                   double tStartC, double tEndC) {
        this.id = id;
        this.index = index;
        this.direction = direction;
        this.fromPoint = fromPoint;
        this.toPoint = toPoint;
        this.tStartC = tStartC;
        this.tEndC = tEndC;
    }

    public int size() { return toPoint - fromPoint; }

    /** Split a curve into monotonic runs; sign changes of dT/dt start a new segment. */
    public static List<Segment> split(Curve curve) {
        List<Segment> out = new ArrayList<>();
        List<Curve.Point> pts = curve.points;
        if (pts.size() < 2) return out;
        int start = 0;
        Direction current = directionOf(pts.get(0).tempC, pts.get(1).tempC);
        int idx = 0;
        for (int i = 1; i < pts.size(); i++) {
            Direction d = directionOf(pts.get(i - 1).tempC, pts.get(i).tempC);
            if (d != Direction.ISOTHERMAL && current == Direction.ISOTHERMAL) {
                current = d; // leave initial isothermal plateau
            }
            if (d != Direction.ISOTHERMAL && d != current) {
                out.add(make(curve.objectId, idx++, current, start, i, pts));
                start = i;
                current = d;
            }
        }
        out.add(make(curve.objectId, idx, current, start, pts.size(), pts));
        return out;
    }

    private static Segment make(String curveId, int idx, Direction dir, int from, int to,
                                List<Curve.Point> pts) {
        return new Segment(curveId + "/seg" + idx, idx, dir, from, to,
                pts.get(from).tempC, pts.get(to - 1).tempC);
    }

    private static Direction directionOf(double t0, double t1) {
        double d = t1 - t0;
        if (d > 1e-9) return Direction.HEATING;
        if (d < -1e-9) return Direction.COOLING;
        return Direction.ISOTHERMAL;
    }
}
