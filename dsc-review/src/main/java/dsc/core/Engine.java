package dsc.core;

import dsc.model.AtmosphereRecord;
import dsc.model.Calibration;
import dsc.model.CandidateBaseline;
import dsc.model.DeriveDiagnostic;
import dsc.model.Direction;
import dsc.model.Event;
import dsc.model.EventType;
import dsc.model.Finding;
import dsc.model.MassRecord;
import dsc.model.NormalizedCurve;
import dsc.model.Quantity;
import dsc.model.ResultVersion;
import dsc.model.SearchWindow;
import dsc.model.Segment;
import dsc.model.SignConvention;
import dsc.model.TangentSelection;
import dsc.model.TransitionKind;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 核心推导引擎：事件溯源 + 依赖图传播。
 * 源档案（仪器输出/质量/速率/气氛/标定/切线）分别存档；
 * 每次输入变化只重算受影响节点；推导结果以独立版本保存，指针原子切换。
 */
public final class Engine {
    public final String sampleId;
    public String sampleName;

    public final List<Event> events = new ArrayList<>();
    public final SourceBundle bundle = new SourceBundle();
    public final List<SearchWindow> windows = new ArrayList<>();
    public final List<ResultVersion> resultVersions = new ArrayList<>();
    public final DepGraph graph = new DepGraph();

    public ResultVersion current;
    public String conflictPending;            // 未解决的并发/人工冲突 id
    public String conflictReason;

    private final Map<String, Finding> acceptedByWindow = new LinkedHashMap<>();
    private final Map<String, Finding> rejectedByFinding = new LinkedHashMap<>();

    public Engine(String sampleId, String sampleName) {
        this.sampleId = sampleId;
        this.sampleName = sampleName;
        bundle.sampleId = sampleId;
        bundle.sampleName = sampleName;
    }

    public int eventSeq() { return events.size(); }

    // ------------------------------------------------------------------
    // 事件追加（乐观并发：baseRevision 必须等于当前源版本）
    // ------------------------------------------------------------------
    public static final class ConflictException extends RuntimeException {
        public final int expected, actual;
        public ConflictException(String msg, int expected, int actual) {
            super(msg);
            this.expected = expected;
            this.actual = actual;
        }
    }

    public Event append(EventType type, String actor, Integer baseRevision,
                        Map<String, Object> payload, Map<String, Object> basis, List<String> affects) {
        int rev = bundle.revision;
        if (baseRevision != null && baseRevision != rev) {
            throw new ConflictException("source revision conflict: expected " + rev
                    + " but edit was based on " + baseRevision, rev, baseRevision);
        }
        Event e = new Event();
        e.seq = events.size() + 1;
        e.sampleId = sampleId;
        e.type = type.code();
        e.actor = actor;
        e.createdAt = Instant.now().toString();
        e.baseRevision = baseRevision == null ? null : String.valueOf(baseRevision);
        if (payload != null) e.payload.putAll(payload);
        if (basis != null) e.basis.putAll(basis);
        if (affects != null) e.affects.addAll(affects);
        e.eventId = dsc.model.Ids.sid(sampleId + "|event|" + e.seq + "|" + type.code()
                + "|" + String.valueOf(e.payload.get("key")));
        boolean sourceMutating = appliesSource(type);
        applyToBundle(e);
        events.add(e);
        if (sourceMutating) bundle.revision++;
        recompute(e.seq, e);
        return e;
    }

    private static boolean appliesSource(EventType t) {
        return switch (t) {
            case BUNDLE_IMPORT, SEGMENT_DATA, MASS_SET, MASS_REVISED, RATE_SET,
                 ATMOSPHERE_SET, CALIBRATION_SET, CALIBRATION_REVISED,
                 TANGENT_PICKED, SOURCE_PATCHED, INTERVAL_DRAGGED -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------
    // 源档案重放（每个直接来源各走各的槽位）
    // ------------------------------------------------------------------
    private void applyToBundle(Event e) {
        EventType type = EventType.fromCode(e.type);
        Map<String, Object> p = e.payload;
        switch (type) {
            case BUNDLE_IMPORT -> {
                sampleName = str(p.get("sampleName"), sampleName);
            }
            case SEGMENT_DATA, SOURCE_PATCHED -> {
                Segment seg = (Segment) p.get("segment");
                if (seg != null) {
                    Segment existing = bundle.segment(seg.segmentId);
                    if (existing == null) bundle.segments.add(seg);
                    else replaceSegment(existing, seg);
                }
            }
            case MASS_SET, MASS_REVISED -> {
                bundle.masses.add(new MassRecord(
                        str(p.get("versionId"), "mass-v" + (bundle.masses.size() + 1)),
                        e.eventId, (Quantity) p.get("mass"), str(p.get("note"), "")));
            }
            case RATE_SET -> {
                Segment seg = bundle.segment(str(p.get("segmentId"), ""));
                if (seg != null) seg.rate = (Quantity) p.get("rate");
            }
            case ATMOSPHERE_SET -> {
                bundle.atmospheres.add(new AtmosphereRecord(
                        str(p.get("versionId"), "atm-v" + (bundle.atmospheres.size() + 1)),
                        str(p.get("gas"), ""), (Quantity) p.get("flow"), str(p.get("note"), "")));
            }
            case CALIBRATION_SET, CALIBRATION_REVISED -> {
                bundle.calibrations.add(new Calibration(
                        str(p.get("versionId"), "cal-v" + (bundle.calibrations.size() + 1)),
                        e.eventId, (Quantity) p.get("offsetC"),
                        str(p.get("standard"), ""), str(p.get("note"), "")));
            }
            case TANGENT_PICKED -> {
                TangentSelection t = new TangentSelection();
                t.versionId = str(p.get("versionId"), "tan-v" + (bundle.tangents.size() + 1));
                t.createdEvent = e.eventId;
                t.segmentId = str(p.get("segmentId"), "");
                t.windowId = str(p.get("windowId"), "");
                t.leftFromC = (Quantity) p.get("leftFromC");
                t.leftToC = (Quantity) p.get("leftToC");
                t.rightFromC = (Quantity) p.get("rightFromC");
                t.rightToC = (Quantity) p.get("rightToC");
                t.note = str(p.get("note"), "");
                bundle.tangents.add(t);
            }
            case INTERVAL_DRAGGED -> {
                String wid = str(p.get("windowId"), "");
                String segId = str(p.get("segmentId"), "");
                double from = num(p.get("fromC"));
                double to = num(p.get("toC"));
                SearchWindow w = window(wid);
                if (w == null) windows.add(new SearchWindow(wid, segId, from, to, e.seq));
                else { w.fromC = from; w.toC = to; }
            }
            default -> { /* 判定类事件不改变源档案 */ }
        }
    }

    private void replaceSegment(Segment old, Segment fresh) {
        int idx = bundle.segments.indexOf(old);
        fresh.segmentId = old.segmentId;
        bundle.segments.set(idx, fresh);
    }

    public SearchWindow window(String id) {
        for (SearchWindow w : windows) if (w.windowId.equals(id)) return w;
        return null;
    }

    private static String str(Object o, String dflt) { return o == null ? dflt : String.valueOf(o); }
    private static double num(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(o));
    }

    // ------------------------------------------------------------------
    // 重算：依赖图传播。曲线对段内容指纹，窗口对区间，候选对锚点参数。
    // ------------------------------------------------------------------
    public ResultVersion recompute(int basedOnEventSeq, Event trigger) {
        List<DeriveDiagnostic> allDiags = new ArrayList<>();
        List<NormalizedCurve> curves = new ArrayList<>();
        MassRecord mass = bundle.currentMass();
        Calibration cal = bundle.currentCalibration();

        java.util.Set<String> dirtySources = new java.util.HashSet<>();
        if (trigger != null) {
            for (String a : trigger.affects) dirtySources.add(a);
            if (trigger.payload.get("segment") instanceof Segment s) dirtySources.add("source:" + s.segmentId);
            if (EventType.fromCode(trigger.type) == EventType.MASS_REVISED
                    || EventType.fromCode(trigger.type) == EventType.MASS_SET)
                dirtySources.add("source:mass");
            if (trigger.type.startsWith("calibration")) dirtySources.add("source:calibration");
        }
        registerSourceNodes(dirtySources);

        for (Segment seg : bundle.segments) {
            Normalizer.Output no = Normalizer.normalize(IdsHash.curveNode(seg.segmentId), seg, mass, cal, sampleId);
            curves.add(no.curve());
            allDiags.addAll(no.diagnostics());
            DepGraph.Node cn = graph.node(no.curve().nodeId, "curve", no.curve().sourceHash);
            graph.edge("source:" + seg.segmentId, cn.id);
            graph.edge("source:mass", cn.id);
            graph.edge("source:calibration", cn.id);
            cn.status = "current";
            cn.reason = null;
        }

        // 重叠检测（仅同段窗口之间；加热/冷却段天然隔离）
        for (SearchWindow w : windows) {
            double score = 0;
            for (SearchWindow o : windows) {
                if (!o.windowId.equals(w.windowId) && w.overlaps(o)) {
                    double span = Math.min(w.hi() - w.lo(), o.hi() - o.lo());
                    double lo = Math.max(w.lo(), o.lo()), hi = Math.min(w.hi(), o.hi());
                    score = Math.max(score, Math.max(0, hi - lo) / Math.max(1e-9, span));
                }
            }
            if (score > 0) {
                allDiags.add(new DeriveDiagnostic(
                        "diag-overlap-" + w.windowId, "overlapping_transitions", "warning",
                        w.segmentId, w.windowId,
                        String.format(Locale.ROOT, "search windows overlap (score %.2f); both events retained, cross-contribution estimated",
                                score),
                        "windows are on the same segment and share a temperature span"));
            }
        }

        List<Finding> findings = new ArrayList<>();
        for (SearchWindow w : windows) {
            findings.addAll(evaluateWindow(w, curves, mass, cal, allDiags));
        }

        // 人工裁定在新版本上恢复状态；旧版本保持原样（历史不被覆盖）
        for (Finding f : findings) {
            Finding prior = rejectedByFinding.get(f.findingId);
            if (prior != null) {
                f.status = "rejected";
                f.decidedByEvent = prior.decidedByEvent;
                f.note = prior.note;
                continue;
            }
            Finding acc = acceptedByWindow.get(wKey(f.windowId, f.baselineModel.code()));
            if (acc != null && acc.candidateId.equals(f.candidateId)) {
                f.status = "accepted";
                f.decidedByEvent = acc.decidedByEvent;
            }
        }

        ResultVersion v = new ResultVersion();
        v.versionId = "result-" + dsc.model.Ids.sid(sampleId + "|result|" + basedOnEventSeq);
        v.basedOnEventSeq = basedOnEventSeq;
        v.sourceRevision = bundle.revision;
        v.createdAt = Instant.now().toString();
        v.curves = curves;
        v.findings = findings;
        v.diagnostics = allDiags;
        for (NormalizedCurve c : curves) {
            v.nodeHashes.put(c.nodeId, c.sourceHash);
            v.nodeStatus.put(c.nodeId, graph.nodes.get(c.nodeId).status);
        }
        for (Finding f : findings) {
            v.nodeHashes.put("finding:" + f.findingId, dsc.model.Ids.sha256(
                    f.candidateId + "|" + f.enthalpyJg + "|" + f.deltaCpJgK).substring(0, 16));
            v.nodeStatus.put("finding:" + f.findingId, f.status);
        }
        v.contentHash = dsc.model.Ids.sha256(v.nodeHashes.toString() + v.findings.size());
        resultVersions.add(v);

        // 图传播：本次脏源使哪些派生节点失效（用于解释“为何需重算”）
        graph.propagate(dirtySources);
        v.current = true;
        if (current != null) current.current = false;
        current = v;
        return v;
    }

    private void registerSourceNodes(java.util.Set<String> dirtySources) {
        for (Segment seg : bundle.segments) {
            DepGraph.Node n = graph.node("source:" + seg.segmentId, "source",
                    dsc.model.Ids.sha256(segSegmentHash(seg)).substring(0, 16));
            if (dirtySources.contains(n.id)) n.status = "stale";
            else if (n.status == null) n.status = "current";
        }
        for (String src : new String[]{"source:mass", "source:calibration", "source:atmosphere"}) {
            DepGraph.Node n = graph.node(src, "source", null);
            if (dirtySources.contains(src)) n.status = "stale";
            else if (n.status == null) n.status = "current";
        }
    }

    private String segSegmentHash(Segment seg) {
        StringBuilder sb = new StringBuilder();
        for (var p : seg.points) sb.append(p.tempRaw).append(',').append(p.hfRaw).append(';');
        return sb + "#" + seg.declaredDirection.code() + "#" + seg.declaredSign.code()
                + "#" + (seg.rate == null ? "" : seg.rate.raw);
    }

    private List<Finding> evaluateWindow(SearchWindow w, List<NormalizedCurve> curves,
                                         MassRecord mass, Calibration cal,
                                         List<DeriveDiagnostic> diags) {
        List<Finding> out = new ArrayList<>();
        NormalizedCurve curve = null;
        for (NormalizedCurve c : curves) if (c.segmentId.equals(w.segmentId)) curve = c;
        if (curve == null) {
            diags.add(new DeriveDiagnostic("diag-nocurve-" + w.windowId, "missing_segment",
                    "error", w.segmentId, w.windowId,
                    "window references unknown segment", "window.segmentId not present in sample history"));
            return out;
        }
        DepGraph.Node wn = graph.node(IdsHash.windowNode(w.windowId), "window",
                IdsHash.forWindow(w.segmentId, w.fromC, w.toC));
        graph.edge(curve.nodeId, wn.id);

        // 空区间检验：先判定是否有任何点落入
        int inside = 0;
        for (var p : curve.points) if (p.tempC >= w.lo() && p.tempC <= w.hi()) inside++;
        if (inside == 0) {
            diags.add(new DeriveDiagnostic("diag-empty-" + w.windowId, "empty_interval", "warning",
                    w.segmentId, w.windowId,
                    String.format(Locale.ROOT, "no samples within [%.2f, %.2f] °C on %s; no baseline proposed",
                            w.lo(), w.hi(), w.segmentId),
                    "empty-window check runs before baseline fitting; current results elsewhere unaffected"));
            wn.status = "blocked";
            wn.reason = "empty interval: zero sampled points";
            return out;
        }

        TangentSelection tangent = null;
        for (TangentSelection t : bundle.tangents)
            if (t.appliesTo(w.segmentId, w.windowId)) tangent = t;

        String prefix = "cand:" + sampleId + ":" + w.windowId;
        List<CandidateBaseline> cands = Baselines.propose(prefix, curve, w.lo(), w.hi(), tangent);
        if (cands.isEmpty()) {
            diags.add(new DeriveDiagnostic("diag-noanchors-" + w.windowId, "insufficient_anchors", "warning",
                    w.segmentId, w.windowId,
                    "not enough anchor points to propose any baseline",
                    "anchor windows contain fewer than 4 valid samples"));
            wn.status = "blocked";
            wn.reason = "insufficient anchors";
            return out;
        }

        double overlap = 0;
        for (SearchWindow o : windows) {
            if (!o.windowId.equals(w.windowId) && w.overlaps(o)) {
                double span = Math.min(w.hi() - w.lo(), o.hi() - o.lo());
                double lo = Math.max(w.lo(), o.lo()), hi = Math.min(w.hi(), o.hi());
                overlap = Math.max(overlap, Math.max(0, hi - lo) / Math.max(1e-9, span));
            }
        }

        // 先算各候选焓值，得到候选间分歧作为每条结果的误差来源
        List<Finding> tmp = new ArrayList<>();
        for (CandidateBaseline b : cands) {
            Transitions.SearchCtx ctx = new Transitions.SearchCtx();
            Segment seg = bundle.segment(curve.segmentId);
            ctx.segmentRateKperMin = seg.rate != null && seg.rate.valid() ? seg.rate.value : null;
            ctx.massG = mass != null && mass.mass.valid() ? mass.mass.value : null;
            ctx.overlapScore = overlap;
            ctx.appliedCalibrationK = cal != null && cal.offsetC.valid() ? cal.offsetC.value : 0;
            String fid = dsc.model.Ids.sid(sampleId + "|finding|" + w.windowId + "|" + b.model.code());
            Finding f = Transitions.analyze(fid, w.windowId, b, curve, ctx);
            if (f != null) tmp.add(f);
        }
        double spreadJg = 0, spreadK = 0;
        Double minH = null, maxH = null, minT = null, maxT = null;
        for (Finding f : tmp) {
            if (f.enthalpyJg != null) {
                minH = minH == null ? f.enthalpyJg : Math.min(minH, f.enthalpyJg);
                maxH = maxH == null ? f.enthalpyJg : Math.max(maxH, f.enthalpyJg);
            }
            var pk = f.point("peak");
            if (pk == null) pk = f.point("tg_mid");
            if (pk != null) {
                minT = minT == null ? pk.tempC : Math.min(minT, pk.tempC);
                maxT = maxT == null ? pk.tempC : Math.max(maxT, pk.tempC);
            }
        }
        if (minH != null) spreadJg = maxH - minH;
        if (minT != null) spreadK = maxT - minT;

        for (Finding f : tmp) {
            // 补“基线分歧”误差项
            if (tmp.size() > 1) {
                f.errors.add(new dsc.model.ErrorSource(f.findingId + ":err-baseline-spread", "baseline",
                        "并列基线候选导致的焓变分歧", Math.abs(spreadJg), Math.abs(spreadK),
                        "all legal baselines retained; range across " + tmp.size() + " candidates"));
            }
            out.add(f);
            graph.edge(wn.id, "finding:" + f.findingId);
            DepGraph.Node fn = graph.node("finding:" + f.findingId, "finding",
                    dsc.model.Ids.sha256(f.candidateId + f.enthalpyJg + f.deltaCpJgK).substring(0, 16));
            fn.status = "current";
        }
        wn.status = "current";
        return out;
    }

    private static String wKey(String windowId, String model) { return windowId + "|" + model; }
