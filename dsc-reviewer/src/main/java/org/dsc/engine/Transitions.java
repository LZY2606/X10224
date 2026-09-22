package org.dsc.engine;

import org.dsc.domain.Models.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 相变/玻璃化转折分析。
 *
 * 方向语义（禁止按文件名猜测）：
 *  - 仪器 convention 决定屏幕向上方向对应的吸/放热；
 *  - segment.direction 决定该段是加热还是冷却；
 *  - 二者齐备才允许分类。加热段可出现熔融（吸热）/结晶（放热）；
 *    冷却段可出现结晶（放热）；跨段窗口一律拒绝（CROSS_SEGMENT_REJECTED）。
 *
 * 积分：ΔH = ∫(HF_measured − HF_baseline) dt，使用相邻采样点梯形积分，
 * 不假设等间隔温度（nonUniformSpacing 时仍按真实 t 间隔积分）。
 * 符号按 convention 折算为“吸热为正 J/g”。
 */
public final class Transitions {
    private Transitions() {}

    public static final class Analysis {
        public TransitionKind kind = TransitionKind.UNCLASSIFIED;
        public double onsetK = Double.NaN;
        public double peakK = Double.NaN;
        public double endsetK = Double.NaN;
        public double enthalpyJg = Double.NaN;
        public double uncertaintyJg = 0;
        public final List<String> errorSources = new ArrayList<>();
        public final List<String> diagnostics = new ArrayList<>();
        public boolean rejected = false;
    }

    /** 窗口必须完全落在单一 segment 的温度覆盖范围内。 */
    public static boolean windowInsideSegment(Segment seg, TempWindow w) {
        if (seg.points().isEmpty()) return false;
        double lo = seg.points().get(0).temperature();
        double hi = lo;
        for (Point p : seg.points()) {
            lo = Math.min(lo, p.temperature());
            hi = Math.max(hi, p.temperature());
        }
        return w.tStartK() >= lo - 1e-9 && w.tEndK() <= hi + 1e-9;
    }

    public static boolean crossesSegmentBoundary(List<Segment> segs, TempWindow w) {
        int covering = 0;
        for (Segment s : segs) if (windowInsideSegment(s, w)) covering++;
        return covering != 1;
    }

    public static Analysis analyze(Segment seg, TempWindow window, Baselines.Fit baseline) {
        Analysis a = new Analysis();

        if (seg.direction() == ScanDirection.UNKNOWN) {
            a.rejected = true;
            a.diagnostics.add("scan direction unknown; cannot classify endo/exo from filename");
            return a;
        }
        if (seg.convention() == HeatFlowConvention.UNKNOWN) {
            a.rejected = true;
            a.diagnostics.add("heat-flow convention unknown; endo/exo classification blocked");
            return a;
        }
        List<Point> pts = Baselines.pointsInWindow(seg, window);
        if (pts.isEmpty()) {
            a.diagnostics.add("empty window: no samples inside [" + window.tStartK()
                    + "," + window.tEndK() + "] K; nothing to integrate");
            a.errorSources.add("EMPTY_WINDOW");
            return a;
        }
        if (pts.size() < 3) {
            a.diagnostics.add("window contains only " + pts.size() + " points; onset/endset unreliable");
            a.errorSources.add("TOO_FEW_POINTS");
        }

        // 相对基线的偏离信号；向上为正
        double[] dev = new double[pts.size()];
        double peakDev = Double.NEGATIVE_INFINITY;
        int peakIdx = 0;
        for (int i = 0; i < pts.size(); i++) {
            Point p = pts.get(i);
            dev[i] = p.heatFlow() - baseline.at(p.temperature());
            if (Math.abs(dev[i]) > Math.abs(peakDev)) {
                peakDev = dev[i];
                peakIdx = i;
            }
        }
        boolean peakUp = peakDev >= 0;
        boolean endothermic = peakUp == (seg.convention() == HeatFlowConvention.ENDO_UP);

        a.peakK = pts.get(peakIdx).temperature();
        a.kind = classify(seg.direction(), endothermic, a.errorSources);
        a.onsetK = onsetByTangent(pts, dev, peakIdx, true, a.errorSources);
        a.endsetK = onsetByTangent(pts, dev, peakIdx, false, a.errorSources);

        double enthalpy = trapIntegrate(pts, baseline, seg.convention());
        a.enthalpyJg = enthalpy;
        a.uncertaintyJg = uncertainty(seg, window, baseline, pts, a.errorSources);
        if (seg.nonUniformSpacing())
            a.errorSources.add("NON_UNIFORM_SPACING: trapezoid over real time, scan-rate term bounded");
        return a;
    }

    private static TransitionKind classify(ScanDirection dir, boolean endothermic, List<String> errs) {
        if (dir == ScanDirection.HEATING) {
            // 加热段的台阶状、焓变近零事件按玻璃化处理由调用方按 shape 覆盖，这里给峰事件分类
            return endothermic ? TransitionKind.MELTING : TransitionKind.CRYSTALLIZATION;
        } else {
            if (endothermic) {
                errs.add("UNEXPECTED_ENDOTHERM_ON_COOLING: keep UNCLASSIFIED rather than guessing");
                return TransitionKind.UNCLASSIFIED;
            }
            return TransitionKind.CRYSTALLIZATION;
        }
    }

    /**
     * onset/endset：在峰侧取“信号最陡点”的切线与基线（dev=0）交点温度。
     * 这正是 DSC 常用的切线交点法；找不到可靠切线时退化为半高位置并登记误差来源。
     */
    private static double onsetByTangent(List<Point> pts, double[] dev, int peakIdx,
                                         boolean leftSide, List<String> errs) {
        int from = leftSide ? 0 : peakIdx;
        int to = leftSide ? peakIdx : pts.size() - 1;
        double maxSlope = 0;
        int steep = -1;
        for (int i = from + 1; i <= to; i++) {
            double dt = pts.get(i).temperature() - pts.get(i - 1).temperature();
            if (dt == 0) continue;
            double slope = Math.abs((dev[i] - dev[i - 1]) / dt);
            if (slope > maxSlope) { maxSlope = slope; steep = i; }
        }
        if (steep <= 0) {
            errs.add((leftSide ? "ONSET" : "ENDSET") + "_UNRELIABLE: no steep tangent found");
            return pts.get(clamp(peakIdx / 2, 0, pts.size() - 1)).temperature();
        }
        double t0 = pts.get(steep - 1).temperature();
        double d0 = dev[steep - 1];
        double t1 = pts.get(steep).temperature();
        double d1 = dev[steep];
        double slope = (d1 - d0) / (t1 - t0);
        if (slope == 0) return t0;
        double tCross = t0 - d0 / slope;
        double lo = pts.get(0).temperature();
        double hi = pts.get(pts.size() - 1).temperature();
        if (tCross < Math.min(lo, hi) || tCross > Math.max(lo, hi)) {
            errs.add((leftSide ? "ONSET" : "ENDSET") + "_OUT_OF_WINDOW: tangent crosses baseline outside");
            return t0;
        }
        return tCross;
    }

    /** 梯形积分（按时间秒），输出吸热为正 J/g。 */
    static double trapIntegrate(List<Point> pts, Baselines.Fit base, HeatFlowConvention conv) {
        double sum = 0;
        for (int i = 1; i < pts.size(); i++) {
            Point a = pts.get(i - 1), b = pts.get(i);
            double da = a.heatFlow() - base.at(a.temperature());
            double db = b.heatFlow() - base.at(b.temperature());
            double dt = b.timeS() - a.timeS();
            // 冷却段时间仍向前，信号方向由 convention 折算
            double sign = conv == HeatFlowConvention.EXO_UP ? -1 : 1;
            sum += sign * 0.5 * (da + db) * dt;
        }
        return sum;
    }

    private static double uncertainty(Segment seg, TempWindow w, Baselines.Fit base,
                                      List<Point> pts, List<String> errs) {
        double durS = pts.get(pts.size() - 1).timeS() - pts.get(0).timeS();
        double u = base.rmse * durS; // 基线残差主导（W/g * s = J/g）
        if (Double.isNaN(seg.scanRate().rateKPerMin())) {
            u += Math.abs(0.02 * (w.widthK()));
            errs.add("SCAN_RATE_UNKNOWN: temperature-axis cross check unavailable");
        }
        if (pts.size() < 3) u *= 2;
        return u;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    /** 台阶检测：玻璃化转变按热容台阶（中点温度近似 Tg），焓变不作为主量。 */
    public static Double glassStepMidpoint(Segment seg, TempWindow w, Baselines.Fit base) {
        List<Point> pts = Baselines.pointsInWindow(seg, w);
        if (pts.size() < 4) return null;
        double before = 0, after = 0;
        int q = pts.size() / 4;
        for (int i = 0; i < q; i++) before += pts.get(i).heatFlow() - base.at(pts.get(i).temperature());
        for (int i = pts.size() - q; i < pts.size(); i++)
            after += pts.get(i).heatFlow() - base.at(pts.get(i).temperature());
        double step = (after / q) - (before / q);
        if (Math.abs(step) < 1e-9) return null;
        int mid = pts.size() / 2;
        return pts.get(mid).temperature();
    }
}
