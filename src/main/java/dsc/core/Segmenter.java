package dsc.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 循环段切分：按 segment 列或温度斜率符号把扫描拆成加热/冷却段。
 * 加热与冷却段绝不混用到同一分析段。
 */
public final class Segmenter {

    public enum Kind { HEATING, COOLING, UNKNOWN }

    public record Segment(int index, Kind kind, List<DscPoint> points) {
        public double tMin() { return points.stream().mapToDouble(DscPoint::tempC).min().orElse(0); }
        public double tMax() { return points.stream().mapToDouble(DscPoint::tempC).max().orElse(0); }
    }

    private Segmenter() {}

    /** 依据 segment 列拆分（循环段切换场景），并判定每段方向。 */
    public static List<Segment> split(InstrumentFile file) {
        List<DscPoint> norm = normalize(file);
        List<List<DscPoint>> groups = new ArrayList<>();
        List<DscPoint> cur = new ArrayList<>();
        int curSeg = file.rows.isEmpty() ? 1 : file.rows.get(0).segment();
        for (int i = 0; i < norm.size(); i++) {
            int seg = file.rows.get(i).segment();
            if (seg != curSeg && !cur.isEmpty()) {
                groups.add(cur);
                cur = new ArrayList<>();
                curSeg = seg;
            }
            cur.add(norm.get(i));
        }
        if (!cur.isEmpty()) groups.add(cur);

        List<Segment> out = new ArrayList<>();
        int idx = 0;
        for (List<DscPoint> g : groups) {
            // 段内若发生持续斜率反转（无显式段号的循环切换），继续细分
            for (List<DscPoint> sub : splitOnDirectionFlips(g)) {
                out.add(new Segment(idx++, classify(sub), sub));
            }
        }
        return out;
    }

    private static List<DscPoint> normalize(InstrumentFile file) {
        List<DscPoint> pts = new ArrayList<>();
        for (InstrumentFile.RawRow r : file.rows) {
            var tn = Quantity.normalize(r.temp(), file.tempUnit, "temperature");
            var qn = Quantity.normalize(r.heatFlow(), file.heatFlowUnit, "heatflow");
            if (!tn.ok() || !qn.ok()) {
                // 无法转换的值留在原位置（跳过该行），诊断由调用方记录
                file.diagnostics.add(tn.ok() ? qn.diagnostic() : tn.diagnostic());
                continue;
            }
            pts.add(new DscPoint(r.time(), tn.canonicalValue(), qn.canonicalValue()));
        }
        return pts;
    }

    /** 检测持续反向（>=6 个连续点）作为循环段切换点。 */
    static List<List<DscPoint>> splitOnDirectionFlips(List<DscPoint> pts) {
        List<List<DscPoint>> out = new ArrayList<>();
        if (pts.size() < 12) { out.add(pts); return out; }
        int start = 0;
        int sign = Integer.signum(Double.compare(pts.get(1).tempC(), pts.get(0).tempC()));
        int run = 0;
        for (int i = 1; i < pts.size(); i++) {
            int s = Integer.signum(Double.compare(pts.get(i).tempC(), pts.get(i - 1).tempC()));
            if (s == 0) continue;
            if (sign == 0) { sign = s; continue; }
            if (s == sign) { run = 0; continue; }
            run++;
            if (run >= 6) {
                out.add(new ArrayList<>(pts.subList(start, i - run + 1)));
                start = i - run + 1;
                sign = s;
                run = 0;
            }
        }
        out.add(new ArrayList<>(pts.subList(start, pts.size())));
        return out;
    }

    static Kind classify(List<DscPoint> pts) {
        if (pts.size() < 2) return Kind.UNKNOWN;
        double d = pts.get(pts.size() - 1).tempC() - pts.get(0).tempC();
        if (d > 1.0) return Kind.HEATING;
        if (d < -1.0) return Kind.COOLING;
        return Kind.UNKNOWN;
    }
}
