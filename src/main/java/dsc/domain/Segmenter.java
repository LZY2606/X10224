package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/** 按 dT/dt 符号切段：加热段与冷却段绝不混用；循环段切换产生新段。 */
public final class Segmenter {

    public enum Type { HEATING, COOLING, ISOTHERMAL }

    public record Segment(int index, Type type, int fromPoint, int toPoint) {
        public int size() { return toPoint - fromPoint + 1; }
    }

    private static final double SLOPE_EPS = 1e-6;

    private Segmenter() {}

    public static List<Segment> split(Trace trace) {
        List<Segment> out = new ArrayList<>();
        int n = trace.points.size();
        if (n == 0) return out;
        int start = 0;
        Type current = typeOf(trace, 0);
        for (int i = 1; i < n; i++) {
            Type t = typeOf(trace, i);
            if (t != current && t != Type.ISOTHERMAL) {
                out.add(new Segment(out.size(), current, start, i));
                start = i;
                current = t;
            }
        }
        out.add(new Segment(out.size(), current, start, n - 1));
        return out;
    }

    private static Type typeOf(Trace trace, int i) {
        int prev = Math.max(0, i - 1);
        double dt = trace.points.get(i).tempC() - trace.points.get(prev).tempC();
        double ds = trace.points.get(i).timeS() - trace.points.get(prev).timeS();
        if (Math.abs(ds) < 1e-12) return Type.ISOTHERMAL;
        double slope = dt / ds;
        if (slope > SLOPE_EPS) return Type.HEATING;
        if (slope < -SLOPE_EPS) return Type.COOLING;
        return Type.ISOTHERMAL;
    }

    /**
     * 找到完整覆盖温度区间 [onsetT, endsetT] 的非等温段。
     * 返回段下标；没有段能完整覆盖（跨段或空区间）时返回 -1 —— 加热与冷却段不混用。
     */
    public static int segmentCovering(Trace trace, List<Segment> segments, double onsetT, double endsetT) {
        double lo = Math.min(onsetT, endsetT);
        double hi = Math.max(onsetT, endsetT);
        for (Segment s : segments) {
            if (s.type == Type.ISOTHERMAL) continue;
            double t0 = trace.points.get(s.fromPoint()).tempC();
            double t1 = trace.points.get(s.toPoint()).tempC();
            double slo = Math.min(t0, t1);
            double shi = Math.max(t0, t1);
            if (lo >= slo - 1e-9 && hi <= shi + 1e-9) return s.index();
        }
        return -1;
    }
}
