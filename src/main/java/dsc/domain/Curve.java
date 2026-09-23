package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw instrument output: ordered samples of (time, temperature, heat flow),
 * each with its raw textual representation kept. Direction of a segment is
 * derived from the temperature slope in the data itself, never from a file
 * name. The exo/endo sign convention comes only from instrument metadata.
 */
public final class Curve {
    public static final class Pt {
        public final double timeS;
        public final double tempC;
        public final double heatFlow; // raw instrument unit, see heatFlowUnit
        public final String rawLine;  // original row, preserved at import
        public Pt(double t, double temp, double hf, String rawLine) {
            this.timeS = t; this.tempC = temp; this.heatFlow = hf; this.rawLine = rawLine;
        }
    }

    public enum Direction { HEATING, COOLING, ISOTHERMAL, UNKNOWN }

    public final List<Pt> points;
    public final String heatFlowUnit;   // e.g. "mW"
    public final String tempUnit;       // e.g. "degC"
    public final String exoConvention;  // "EXO_UP" | "EXO_DOWN" | null (unknown -> must not guess)

    public Curve(List<Pt> points, String heatFlowUnit, String tempUnit, String exoConvention) {
        this.points = points;
        this.heatFlowUnit = heatFlowUnit;
        this.tempUnit = tempUnit;
        this.exoConvention = exoConvention;
    }

    /** Split a run into segments wherever the temperature slope changes sign (cycle segment switching). */
    public List<Segment> splitSegments() {
        List<Segment> out = new ArrayList<>();
        if (points.size() < 2) return out;
        List<Pt> cur = new ArrayList<>();
        Direction curDir = Direction.UNKNOWN;
        for (int i = 0; i < points.size(); i++) {
            Pt p = points.get(i);
            Direction d = curDir;
            if (i > 0) {
                double dT = p.tempC - points.get(i - 1).tempC;
                double dt = p.timeS - points.get(i - 1).timeS;
                if (dt > 0) {
                    double slope = dT / dt;
                    if (slope > 1e-6) d = Direction.HEATING;
                    else if (slope < -1e-6) d = Direction.COOLING;
                    else d = Direction.ISOTHERMAL;
                }
            }
            if (curDir == Direction.UNKNOWN && d != Direction.UNKNOWN) curDir = d;
            if (cur.isEmpty() || d == curDir || d == Direction.UNKNOWN) {
                cur.add(p);
            } else {
                out.add(new Segment(curDir, List.copyOf(cur)));
                cur = new ArrayList<>();
                cur.add(points.get(i - 1)); // keep continuity at the switch point
                cur.add(p);
                curDir = d;
            }
        }
        if (!cur.isEmpty()) out.add(new Segment(curDir, List.copyOf(cur)));
        return out;
    }

    /** Detect heat-flow sign reversal inside a segment (reverse heat flow boundary case). */
    public static boolean hasReverseHeatFlow(Segment s) {
        int sign = 0;
        for (Pt p : s.points) {
            int sg = (int) Math.signum(p.heatFlow);
            if (sg == 0) continue;
            if (sign == 0) sign = sg;
            else if (sg != sign) return true;
        }
        return false;
    }

    public static final class Segment {
        public final Direction direction;
        public final List<Pt> points;
        public Segment(Direction d, List<Pt> pts) { this.direction = d; this.points = pts; }
        public double tMin() { return points.stream().mapToDouble(p -> p.tempC).min().orElse(0); }
        public double tMax() { return points.stream().mapToDouble(p -> p.tempC).max().orElse(0); }
        public boolean isUniformSpacing() {
            if (points.size() < 3) return true;
            double d0 = points.get(1).tempC - points.get(0).tempC;
            for (int i = 2; i < points.size(); i++) {
                double d = points.get(i).tempC - points.get(i - 1).tempC;
                if (Math.abs(d - d0) > 1e-9) return false;
            }
            return true;
        }
    }
}
