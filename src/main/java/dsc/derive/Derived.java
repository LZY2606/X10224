package dsc.derive;

import dsc.domain.Entities.*;
import dsc.domain.History.Snapshot;
import dsc.domain.Values;
import dsc.domain.Values.Point;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从某个历史快照重算派生层。
 * 关键语义：
 *  - 加热与冷却段绝不混用（积分只在绑定的仪器输出/段内进行）；
 *  - 吸放热方向来自显式 exoSign，不从文件名猜；
 *  - 多个合理基线（linear/step 及不同切线）全部保留为候选；
 *  - 质量修订后，所有依赖质量的归一化派生标记 stale 并重算，旧版本不被覆盖；
 *  - 无法唯一决定时保留全部合法候选。
 */
public final class Derived {

    public static final class BaselineCandidate {
        public final String id;
        public final String segmentId;
        public final String tangentId;
        public final String method;
        public final Baseline baseline;
        public final double fromK;
        public final double toK;
        public final Transition.Kind expectedKind;
        public final Transition result;

        BaselineCandidate(String id, String segmentId, String tangentId, String method,
                          Baseline b, double fromK, double toK, Transition.Kind kind, Transition r) {
            this.id = id;
            this.segmentId = segmentId;
            this.tangentId = tangentId;
            this.method = method;
            this.baseline = b;
            this.fromK = fromK;
            this.toK = toK;
            this.expectedKind = kind;
            this.result = r;
        }
    }

    public static final class SegmentView {
        public final Segment segment;
        public final InstrumentOutput instrument;
        public final ScanRate rate;
        public final Atmosphere atmosphere;
        public final TempCalibration calibration;
        public final MassVersion mass;
        public final List<Point> calibratedPoints;
        public final List<String> diagnostics;
        public final List<BaselineCandidate> candidates = new ArrayList<>();

        SegmentView(Segment s, InstrumentOutput io, ScanRate r, Atmosphere a,
                    TempCalibration c, MassVersion m, List<Point> pts, List<String> diag) {
            this.segment = s;
            this.instrument = io;
            this.rate = r;
            this.atmosphere = a;
            this.calibration = c;
            this.mass = m;
            this.calibratedPoints = pts;
            this.diagnostics = List.copyOf(diag);
        }
    }

    public static final class Bundle {
        public final Snapshot snapshot;
        public final List<SegmentView> views = new ArrayList<>();
        public final DepGraph graph = new DepGraph();
        public final List<String> globalDiagnostics = new ArrayList<>();

        Bundle(Snapshot s) {
            this.snapshot = s;
        }

        public SegmentView viewOfSegment(String segmentId) {
            for (SegmentView v : views) {
                if (v.segment.id.equals(segmentId)) {
                    return v;
                }
            }
            return null;
        }

        public List<BaselineCandidate> allCandidates() {
            List<BaselineCandidate> out = new ArrayList<>();
            for (SegmentView v : views) {
                out.addAll(v.candidates);
            }
            return out;
        }

        public BaselineCandidate candidate(String id) {
            for (BaselineCandidate c : allCandidates()) {
                if (c.id.equals(id)) {
                    return c;
                }
            }
            return null;
        }

        /** 当前被采纳的候选（按快照裁定）。 */
        public List<BaselineCandidate> accepted() {
            List<BaselineCandidate> out = new ArrayList<>();
            for (BaselineCandidate c : allCandidates()) {
                if (snapshot.verdicts.getOrDefault(c.id, Verdict.PROPOSED) == Verdict.ACCEPTED) {
                    out.add(c);
                }
            }
            return out;
        }
    }

    /** 对快照全量重算。 */
    public static Bundle recompute(Snapshot snap) {
        Bundle b = new Bundle(snap);

        // 当前生效质量：取每个 sampleKey 历史中的最后一版
        MassVersion currentMass = null;
        String currentMassId = null;
        for (Map.Entry<String, List<String>> e : snap.massHistory.entrySet()) {
            List<String> hist = e.getValue();
            if (!hist.isEmpty()) {
                currentMassId = hist.get(hist.size() - 1);
                currentMass = snap.masses.get(currentMassId);
            }
        }

        // 直接来源节点进图
        snap.instruments.forEach((id, io) ->
                b.graph.put(id, "instrument", Hashes.instrument(io), List.of()));
        snap.rates.forEach((id, r) -> b.graph.put(id, "rate", Hashes.rate(r), List.of()));
        snap.atmospheres.forEach((id, a) -> b.graph.put(id, "atmosphere", Hashes.atmosphere(a), List.of()));
        snap.calibrations.forEach((id, c) -> b.graph.put(id, "calibration", Hashes.calibration(c), List.of()));
        if (currentMass != null) {
            b.graph.put(currentMass.id, "mass", Hashes.mass(currentMass), List.of());
        }

        for (Segment seg : snap.segments.values()) {
            buildSegment(b, seg, currentMass, currentMassId);
        }
        return b;
    }

    private static void buildSegment(Bundle b, Segment seg, MassVersion mass, String massId) {
        Snapshot s = b.snapshot;
        InstrumentOutput io = s.instruments.get(seg.instrumentOutputId);
        ScanRate rate = s.rates.get(seg.scanRateId);
        Atmosphere atm = s.atmospheres.get(seg.atmosphereId);
        TempCalibration cal = s.calibrations.get(seg.tempCalibrationId);
        List<String> diag = new ArrayList<>();

        if (io == null) {
            diag.add("段 " + seg.id + " 引用的仪器输出缺失: " + seg.instrumentOutputId);
        }
        if (rate == null) {
            diag.add("段 " + seg.id + " 缺少扫描速率");
        }
        if (mass == null) {
            diag.add("缺少样品质量，归一化焓不可用");
        }

        List<Point> raw = io == null ? List.of() : io.points;
        List<Point> ordered = Values.usableInTempOrder(raw);
        List<Point> calibrated = new ArrayList<>();
        for (Point p : ordered) {
            double t = cal == null ? p.tempK : cal.apply(p.tempK);
            calibrated.add(new Point(t, p.heatFlowW, p.timeS, p.rawTemp, p.rawHeat, p.rawTime,
                    p.diagnostics));
        }
        // 收集点级诊断
        for (Point p : raw) {
            diag.addAll(p.diagnostics);
        }
        if (io != null && io.exoSign == Entities.ExoSign.UNKNOWN) {
            diag.add("仪器输出 " + io.id + " 未声明吸放热方向约定");
        }

        SegmentView view = new SegmentView(seg, io, rate, atm, cal, mass, calibrated, diag);
        b.views.add(view);

        List<String> segDeps = new ArrayList<>();
        segDeps.add(seg.instrumentOutputId);
        if (rate != null) {
            segDeps.add(rate.id);
        }
        if (atm != null) {
            segDeps.add(atm.id);
        }
        if (cal != null) {
            segDeps.add(cal.id);
        }
        if (massId != null) {
            segDeps.add(massId);
        }
        String segNode = "segment:" + seg.id;
        b.graph.put(segNode, "segment_view",
                Hashes.segment(seg, io, rate, atm, cal, mass), segDeps);

        // 为该段的每条切线生成候选；一条切线同时产生 linear / step 两个合理解释
        for (Tangent t : snap.tangents.values()) {
            if (!t.segmentId.equals(seg.id)) {
                continue;
            }
            addCandidates(b, view, t, mass, massId, segNode);
        }
    }

    private static void addCandidates(Bundle b, SegmentView view, Tangent tan,
                                      MassVersion mass, String massId, String segNode) {
        List<Point> pts = view.calibratedPoints;
        double lv = Baseline.sampleAt(pts, tan.leftAnchorK);
        double rv = Baseline.sampleAt(pts, tan.rightAnchorK);
        Entities.ExoSign sign = view.instrument == null ? Entities.ExoSign.UNKNOWN : view.instrument.exoSign;
        double rate = view.rate == null ? Double.NaN : view.rate.kPerMin;
        double massKg = mass == null ? Double.NaN : mass.kg;

        Transition.Kind expected = kindOf(tan.kind);
        String[] methods = expected == Transition.Kind.GLASS
                ? new String[]{"step", "linear"}
                : new String[]{"linear", "step"};

        int ordinal = 0;
        for (String method : methods) {
            Baseline bl = method.equals("step")
                    ? Baseline.step(tan.leftAnchorK, tan.rightAnchorK, lv, rv,
                            0.5 * (tan.fromK + tan.toK))
                    : Baseline.linear(tan.leftAnchorK, tan.rightAnchorK, lv, rv);

            String candidateId = "bl:" + tan.id + ":" + method;
            Map<String, String> src = new LinkedHashMap<>();
            src.put("segmentId", view.segment.id);
            src.put("instrumentOutputId", view.segment.instrumentOutputId);
            src.put("tangentId", tan.id);
            src.put("method", method);
            if (view.rate != null) {
                src.put("scanRateId", view.rate.id);
            }
            if (view.calibration != null) {
                src.put("calibrationId", view.calibration.id);
            }
            if (massId != null) {
                src.put("massId", massId);
            }
            String resultId = "tr:" + Hashes.shortHash(candidateId
                    + "|" + bl.leftValueW + "|" + bl.rightValueW
                    + "|" + (mass == null ? "none" : mass.id)
                    + "|" + (view.rate == null ? "none" : view.rate.id)
                    + "|" + (view.calibration == null ? "none" : view.calibration.id));

            Transition r = Transition.compute(resultId, view.segment.id, candidateId,
                    pts, sign, view.segment.direction, bl, tan.fromK, tan.toK,
                    massKg, rate, expected, false, src);

            BaselineCandidate cand = new BaselineCandidate(candidateId, view.segment.id,
                    tan.id, method, bl, tan.fromK, tan.toK, expected, r);
            view.candidates.add(cand);

            b.graph.put(candidateId, "baseline_candidate",
                    Hashes.candidate(cand), List.of(segNode, tan.id));
            b.graph.put(resultId, "transition",
                    Hashes.transition(r), List.of(candidateId, segNode,
                            massId == null ? "__no_mass__" : massId));
            ordinal++;
        }
    }

    private static Transition.Kind kindOf(String kind) {
        if (kind == null) {
            return Transition.Kind.UNKNOWN;
        }
        return switch (kind.toLowerCase()) {
            case "glass", "tg", "glass_transition" -> Transition.Kind.GLASS;
            case "crystallization", "crystal", "cold_crystallization" -> Transition.Kind.CRYSTALLIZATION;
            case "melting", "melt", "fusion" -> Transition.Kind.MELTING;
            default -> Transition.Kind.UNKNOWN;
        };
    }
}
