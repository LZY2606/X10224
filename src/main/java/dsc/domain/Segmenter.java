package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a run into heating / cooling / isothermal segments from the measured
 * temperature slope. Segment kind is derived from data, never from the file
 * name, and heating and cooling segments are never mixed into one interval.
 */
public class Segmenter {
    public static class Segment {
        public int startIdx;
        public int endIdx;
        public String kind; // heating | cooling | isothermal
        public boolean uniformSpacing;
    }

    private static final double SLOPE_EPS = 1e-4; // K/s

    public static List<Segment> detect(List<double[]> points) {
        List<Segment> out = new ArrayList<>();
        if (points.size() < 2) return out;
        int n = points.size();
        int segStart = 0;
        String curKind = kindOf(points, 0);
        for (int i = 1; i < n - 1; i++) {
            String k = kindOf(points, i);
            if (!k.equals(curKind)) {
                out.add(build(points, segStart, i, curKind));
                segStart = i;
                curKind = k;
            }
        }
        out.add(build(points, segStart, n - 1, curKind));
        return out;
    }

    private static String kindOf(List<double[]> p, int i) {
        double dt = p.get(i + 1)[0] - p.get(i)[0];
        if (dt <= 0) return "isothermal";
        double slope = (p.get(i + 1)[1] - p.get(i)[1]) / dt;
        if (slope > SLOPE_EPS) return "heating";
        if (slope < -SLOPE_EPS) return "cooling";
        return "isothermal";
    }

    private static Segment build(List<double[]> p, int a, int b, String kind) {
        Segment s = new Segment();
        s.startIdx = a;
        s.endIdx = b;
        s.kind = kind;
        s.uniformSpacing = true;
        double dt0 = -1;
        for (int i = a; i < b; i++) {
            double dt = p.get(i + 1)[0] - p.get(i)[0];
            if (dt0 < 0) dt0 = dt;
            else if (Math.abs(dt - dt0) > 1e-9) s.uniformSpacing = false;
        }
        return s;
    }
}
