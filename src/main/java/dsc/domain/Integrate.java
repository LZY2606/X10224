package dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数值计算：一律按实际温度坐标梯形求积（非等间隔安全）。
 * 所有量带单位；方向只依据曲线 directionMeta。
 */
public final class Integrate {
    private Integrate() {}

    /** 从用户切线（两侧各一条）构造基线：取左右切线交点之前的左切线、之后的右切线？
     *  简化为弦基线：连接区间两端点的直线。切线法用左右切线平均。 */
    public static double[] chordBaseline(State.Curve c, double t1, double t2) {
        double[] a = nearest(c, t1), b = nearest(c, t2);
        if (a == null || b == null || b[0] == a[0]) return null;
        double slope = (b[1] - a[1]) / (b[0] - a[0]);
        return new double[]{slope, a[1] - slope * a[0]};
    }

    public static double[] tangentBaseline(State st, State.ThermalEvent e) {
        State.Tangent left = null, right = null;
        for (State.Tangent t : st.tangents.values()) {
            if (!t.eventId.equals(e.id)) continue;
            if ("left".equals(t.side)) left = t;
            if ("right".equals(t.side)) right = t;
        }
        State.Curve c = st.curves.get(st.segments.get(e.segmentId).curveId);
        if (left == null || right == null) return chordBaseline(c, e.tStart, e.tEnd);
        double[] la = nearest(c, left.t1), lb = nearest(c, left.t2);
        double[] ra = nearest(c, right.t1), rb = nearest(c, right.t2);
        if (la == null || lb == null || ra == null || rb == null) return null;
        double ls = (lb[1] - la[1]) / (lb[0] - la[0]);
        double rs = (rb[1] - ra[1]) / (rb[0] - ra[0]);
        double li = la[1] - ls * la[0], ri = ra[1] - rs * ra[0];
        // 两条切线的平均作为折中基线候选
        return new double[]{(ls + rs) / 2, (li + ri) / 2};
    }

    public static double[] nearest(State.Curve c, double t) {
        double[] best = null;
        for (double[] p : c.points) {
            if (best == null || Math.abs(p[0] - t) < Math.abs(best[0] - t)) best = p;
        }
        return best;
    }

    public static int pointsInRange(State.Curve c, double t1, double t2) {
        int n = 0;
        for (double[] p : c.points) if (p[0] >= Math.min(t1, t2) && p[0] <= Math.max(t1, t2)) n++;
        return n;
    }

    /**
     * 计算 onset/peak/endset 与焓变。
     * @return null 表示空区间
     */
    public static Map<String, Object> analyze(State st, State.ThermalEvent e, State.Baseline bl) {
        State.Segment seg = st.segments.get(e.segmentId);
        State.Curve c = st.curves.get(seg.curveId);
        State.Calibration cal = st.calibrations.get(seg.calibrationId);
        double lo = Math.min(e.tStart, e.tEnd), hi = Math.max(e.tStart, e.tEnd);
        List<double[]> pts = new ArrayList<>();
        for (double[] p : c.points) if (p[0] >= lo && p[0] <= hi) pts.add(p);
        if (pts.isEmpty()) return null;

        double calOffset = cal == null ? 0 : cal.offsetK;
        // 峰：相对基线偏差最大点；符号按方向约定解释
        int peakIdx = 0;
        double peakDev = 0;
        for (int i = 0; i < pts.size(); i++) {
            double dev = pts.get(i)[1] - (bl.slope * pts.get(i)[0] + bl.intercept);
            if (Math.abs(dev) > Math.abs(peakDev)) { peakDev = dev; peakIdx = i; }
        }
        // onset/endset：偏差超过噪声阈值（峰偏差的 2%）的首尾点
        double thresh = Math.abs(peakDev) * 0.02;
        int onsetIdx = 0, endsetIdx = pts.size() - 1;
        for (int i = 0; i < pts.size(); i++) {
            double dev = Math.abs(pts.get(i)[1] - (bl.slope * pts.get(i)[0] + bl.intercept));
            if (dev > thresh) { onsetIdx = i; break; }
        }
        for (int i = pts.size() - 1; i >= 0; i--) {
            double dev = Math.abs(pts.get(i)[1] - (bl.slope * pts.get(i)[0] + bl.intercept));
            if (dev > thresh) { endsetIdx = i; break; }
        }
        // 焓：∫(hf - baseline) dT / rate * 60 / mass  → J/g；按实际温度梯形求积
        double areaMwK = 0;
        for (int i = 1; i < pts.size(); i++) {
            double t0 = pts.get(i - 1)[0], t1 = pts.get(i)[0];
            double d0 = pts.get(i - 1)[1] - (bl.slope * t0 + bl.intercept);
            double d1 = pts.get(i)[1] - (bl.slope * t1 + bl.intercept);
            areaMwK += 0.5 * (d0 + d1) * (t1 - t0);
        }
        double rateKPerS = seg.rateKPerMin / 60.0;
        double areaMj = areaMwK / rateKPerS; // mW·K / (K/s) = mJ
        State.MassVersion mass = st.currentMass();
        double jPerG = mass == null ? Double.NaN : areaMj / mass.mg; // mJ/mg = J/g

        // 方向解释：只依据 directionMeta
        String dir = c.directionMeta;
        String peakKind;
        if ("exo-up".equals(dir)) peakKind = peakDev > 0 ? "exothermic" : "endothermic";
        else if ("endo-up".equals(dir)) peakKind = peakDev > 0 ? "endothermic" : "exothermic";
        else peakKind = "undetermined";

        List<String> err = new ArrayList<>();
        err.add("baseline-choice:" + bl.method);
        err.add("mass:" + (mass == null ? "missing" : mass.id + "±0.02mg"));
        err.add("calibration:" + (cal == null ? "none" : cal.id + "±" + Math.abs(calOffset) + "K"));
        err.add("integration:trapezoid-actual-T");
        if ("unknown".equals(dir)) err.add("direction:undetermined");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("onset", Map.of("t", round(pts.get(onsetIdx)[0] + calOffset), "unit", "degC"));
        out.put("peak", Map.of("t", round(pts.get(peakIdx)[0] + calOffset), "unit", "degC",
                "deviation", round(peakDev), "deviationUnit", "mW", "kind", peakKind));
        out.put("endset", Map.of("t", round(pts.get(endsetIdx)[0] + calOffset), "unit", "degC"));
        out.put("areaMJ", round(areaMj));
        out.put("jPerG", Double.isNaN(jPerG) ? 0 : round(jPerG));
        out.put("jPerGUnit", "J/g");
        out.put("rateKPerMin", seg.rateKPerMin);
        out.put("atmosphere", seg.atmosphere);
        out.put("errorSources", err);
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
