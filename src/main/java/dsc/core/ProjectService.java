package dsc.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import dsc.domain.AtmosphereRecord;
import dsc.domain.BaselineResult;
import dsc.domain.CalibrationRecord;
import dsc.domain.DirectionConvention;
import dsc.domain.EnthalpyResult;
import dsc.domain.InstrumentOutput;
import dsc.domain.MassVersion;
import dsc.domain.Segment;
import dsc.domain.TangentChoice;
import dsc.domain.TransitionResult;
import dsc.domain.TransitionType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 样品履历的中央服务：六类来源分别存档、事件只增、依赖图版本化、
 * 并列基线候选、人工裁定、时间旅行（回到任意旧事件恢复当时结论）。
 *
 * 重放纪律：所有状态变更都来自 ProjectEvent；store 层持久化事件流，
 * 重启（以及导入/删除数据库后）从事件 0 重建，当前指针是派生缓存而非事实来源。
 */
public final class ProjectService {

    public static final ObjectMapper JSON = new ObjectMapper();

    private DependencyGraph graph = new DependencyGraph();
    private final List<ProjectEvent> events = new ArrayList<>();

    // 来源事实
    public final Map<String, InstrumentOutput> outputs = new LinkedHashMap<>();
    public final Map<String, Segment> segments = new LinkedHashMap<>();
    public final Map<String, MassVersion> masses = new LinkedHashMap<>();
    public final Map<String, AtmosphereRecord> atmospheres = new LinkedHashMap<>();
    public final Map<String, CalibrationRecord> calibrations = new LinkedHashMap<>();
    public final Map<String, TangentChoice> tangents = new LinkedHashMap<>();
    public final Map<String, String> scanRates = new LinkedHashMap<>();
    public final Map<String, CsvImport.ImportReport> reports = new LinkedHashMap<>();

    // 计算结果（segmentId -> 候选列表，并列保留）
    public final Map<String, List<BaselineResult>> baselineCandidates = new LinkedHashMap<>();
    public final Map<String, List<TransitionResult>> transitionResults = new LinkedHashMap<>();
    public final Map<String, List<EnthalpyResult>> enthalpyResults = new LinkedHashMap<>();
    // 裁定：segmentId -> accepted candidateId（历史裁定随事件重放恢复）
    public final Map<String, String> acceptedCandidate = new LinkedHashMap<>();
    public final Map<String, String> rejectedCandidates = new LinkedHashMap<>();

    // ------------------------------------------------------------------
    // 事件与重放
    // ------------------------------------------------------------------

    public List<ProjectEvent> eventLog() {
        return List.copyOf(events);
    }

    private ProjectEvent emit(String type, String actor, String basis, Map<String, Object> payload) {
        long seq = events.size() + 1L;
        ProjectEvent ev = new ProjectEvent(seq, type, actor, basis, payload, Instant.now().toString());
        events.add(ev);
        return ev;
    }

    /** 从持久化事件流重建全部状态（store 层在加载/导入后调用）。 */
    public void replayFrom(List<Map<String, Object>> rawEvents) {
        graph = new DependencyGraph();
        events.clear();
        outputs.clear();
        segments.clear();
        masses.clear();
        atmospheres.clear();
        calibrations.clear();
        tangents.clear();
        scanRates.clear();
        reports.clear();
        baselineCandidates.clear();
        transitionResults.clear();
        enthalpyResults.clear();
        acceptedCandidate.clear();
        rejectedCandidates.clear();
        for (Map<String, Object> raw : rawEvents) {
            applyEvent(raw, true);
        }
    }

    /** 时间旅行：重放前 untilSeq（含）个事件，返回当时可见结论。不修改已持久化事件流。 */
    public ProjectService viewAt(long untilSeq) {
        ProjectService view = new ProjectService();
        List<Map<String, Object>> slice = new ArrayList<>();
        for (ProjectEvent ev : events) {
            if (ev.seq <= untilSeq) {
                slice.add(ev.toMap());
            }
        }
        view.replayFrom(slice);
        return view;
    }

    // ------------------------------------------------------------------
    // 来源归档（六类独立存档）
    // ------------------------------------------------------------------

    public MassVersion archiveMass(String massId, double milligrams, String rawText, String note,
                                   String actor) {
        int version = masses.containsKey(massId) ? masses.get(massId).massVersion + 1 : 1;
        String supersedes = masses.containsKey(massId) ? masses.get(massId).massId : null;
        MassVersion mv = new MassVersion(massId, version, milligrams, rawText,
                Instant.now().toString(), note, supersedes);
        masses.put(massId, mv);
        List<NodeVersion.DepRef> deps = List.of();
        String payload = write(Map.of("milligrams", milligrams, "rawText", rawText, "version", version));
        graph.addVersion(node(GraphNodeKind.SRC_MASS, massId), GraphNodeKind.SRC_MASS, deps,
                payload, "OK", null, DependencyGraph.RULE_VERSION);
        emit(version == 1 ? ProjectEvent.SOURCE_ARCHIVED : ProjectEvent.MASS_REVISED, actor,
                "样品质量独立存档；修订产生新版本，所有质量归一化派生必须过期重算，不覆盖旧值",
                Map.of("nodeId", node(GraphNodeKind.SRC_MASS, massId), "massId", massId,
                        "version", version, "milligrams", milligrams, "rawText", rawText));
        if (version > 1) {
            propagateStale(GraphNodeKind.SRC_MASS, massId, actor,
                    "MASS_REVISED");
        }
        return mv;
    }

    public void archiveAtmosphere(AtmosphereRecord rec, String actor) {
        atmospheres.put(rec.atmosphereId, rec);
        graph.addVersion(node(GraphNodeKind.SRC_ATMOSPHERE, rec.atmosphereId), GraphNodeKind.SRC_ATMOSPHERE,
                List.of(), write(Map.of("gas", rec.gas, "flowMlPerMin", rec.flowMlPerMin,
                        "rawFlowText", rec.rawFlowText)), "OK", null, DependencyGraph.RULE_VERSION);
        emit(ProjectEvent.SOURCE_ARCHIVED, actor, "气氛独立存档；只影响引用它的段",
                Map.of("nodeId", node(GraphNodeKind.SRC_ATMOSPHERE, rec.atmosphereId),
                        "atmosphereId", rec.atmosphereId, "gas", rec.gas,
                        "rawFlow", rec.rawFlowText));
    }

    public void archiveCalibration(CalibrationRecord rec, String actor) {
        calibrations.put(rec.calibrationId, rec);
        boolean exists = graph.currentVersion(node(GraphNodeKind.SRC_CALIBRATION, rec.calibrationId)) != null;
        graph.addVersion(node(GraphNodeKind.SRC_CALIBRATION, rec.calibrationId),
                GraphNodeKind.SRC_CALIBRATION, List.of(),
                write(Map.of("standard", rec.standard, "offsetK", rec.offsetK, "rawText", rec.rawText)),
                "OK", null, DependencyGraph.RULE_VERSION);
        emit(exists ? ProjectEvent.MASS_REVISED : ProjectEvent.SOURCE_ARCHIVED, actor,
                "温度标定独立存档；修订只影响引用段的温度坐标派生",
                Map.of("nodeId", node(GraphNodeKind.SRC_CALIBRATION, rec.calibrationId),
                        "calibrationId", rec.calibrationId, "offsetK", rec.offsetK));
        if (exists) {
            propagateStale(GraphNodeKind.SRC_CALIBRATION, rec.calibrationId, actor,
                    "CALIBRATION_REVISED");
        }
    }

    public void archiveRate(String rateId, double kPerMin, String rawText, String actor) {
        scanRates.put(rateId, rawText + "|" + kPerMin);
        graph.addVersion(node(GraphNodeKind.SRC_RATE, rateId), GraphNodeKind.SRC_RATE, List.of(),
                write(Map.of("kPerMin", kPerMin, "rawText", rawText)), "OK", null,
                DependencyGraph.RULE_VERSION);
        emit(ProjectEvent.SOURCE_ARCHIVED, actor, "扫描速率独立存档；加热/冷却段各自携带，不混用",
                Map.of("nodeId", node(GraphNodeKind.SRC_RATE, rateId), "rateId", rateId,
                        "kPerMin", kPerMin, "rawText", rawText));
    }

    public void archiveInstrument(InstrumentOutput out, String actor) {
        outputs.put(out.outputId, out);
        DirectionConvention dc = DirectionConvention.fromInstrumentHeader(out.directionHeader,
                out.instrumentModel);
        graph.addVersion(node(GraphNodeKind.SRC_INSTRUMENT, out.outputId), GraphNodeKind.SRC_INSTRUMENT,
                List.of(), write(Map.of("fileName", out.fileName, "directionHeader",
                        out.directionHeader == null ? "" : out.directionHeader,
                        "resolvedDirection", dc.code, "tempUnit", out.tempUnit,
                        "heatFlowUnit", out.heatFlowUnit)),
                dc == DirectionConvention.UNRESOLVED ? "AMBIGUOUS" : "OK",
                dc == DirectionConvention.UNRESOLVED
                        ? "DIRECTION_UNRESOLVED: 吸放热方向不得由文件名猜测" : null,
                DependencyGraph.RULE_VERSION);
        emit(ProjectEvent.SOURCE_ARCHIVED, actor,
                "仪器输出逐字存档；吸放热方向唯一合法来源是仪器头标记，禁止按文件名猜测",
                Map.of("nodeId", node(GraphNodeKind.SRC_INSTRUMENT, out.outputId),
                        "outputId", out.outputId, "fileName", out.fileName,
                        "directionHeader", out.directionHeader == null ? "" : out.directionHeader,
                        "resolvedDirection", dc.code));
    }

    /** 从已归档仪器输出生成扫描段（曲线派生）。 */
    public CsvImport.ImportReport buildSegment(String segmentId, String outputId, int cycleIndex,
                                               String mode, String massId, String calibrationId,
                                               String rateId, String atmosphereId, String actor) {
        InstrumentOutput out = outputs.get(outputId);
        MassVersion mass = masses.get(massId);
        CalibrationRecord cal = calibrations.get(calibrationId);
        double rate = rateId == null ? Double.NaN
                : Double.parseDouble(scanRates.get(rateId).substring(scanRates.get(rateId).lastIndexOf('|') + 1));
        CsvImport.ImportReport report = CsvImport.importOutput(out, segmentId, cycleIndex, mode, mass, cal,
                rate, atmosphereId);
        segments.put(segmentId, report.segment);
        reports.put(segmentId, report);

        List<NodeVersion.DepRef> deps = new ArrayList<>();
        deps.add(new NodeVersion.DepRef(GraphNodeKind.SRC_INSTRUMENT, outputId,
                graph.currentVersion(node(GraphNodeKind.SRC_INSTRUMENT, outputId)).version));
        deps.add(new NodeVersion.DepRef(GraphNodeKind.SRC_MASS, massId,
                graph.currentVersion(node(GraphNodeKind.SRC_MASS, massId)).version));
        deps.add(new NodeVersion.DepRef(GraphNodeKind.SRC_RATE, rateId,
                graph.currentVersion(node(GraphNodeKind.SRC_RATE, rateId)).version));
        deps.add(new NodeVersion.DepRef(GraphNodeKind.SRC_ATMOSPHERE, atmosphereId,
                graph.currentVersion(node(GraphNodeKind.SRC_ATMOSPHERE, atmosphereId)).version));
        deps.add(new NodeVersion.DepRef(GraphNodeKind.SRC_CALIBRATION, calibrationId,
                graph.currentVersion(node(GraphNodeKind.SRC_CALIBRATION, calibrationId)).version));

        String status = report.diagnostics.stream().anyMatch(d -> d.contains("NON_MONOTONIC"))
                ? "ERROR" : (report.segment.badPoints.isEmpty() ? "OK" : "AMBIGUOUS");
        graph.addVersion(node(GraphNodeKind.DERIVED_SEGMENT_CURVE, segmentId),
                GraphNodeKind.DERIVED_SEGMENT_CURVE, deps,
                write(Map.of("points", report.segment.size(), "badPoints",
                        report.segment.badPoints.size(), "mode", mode)),
                status, String.join(" | ", report.diagnostics), DependencyGraph.RULE_VERSION);
        Map<String, Object> curvePayload = new LinkedHashMap<>();
        curvePayload.put("nodeId", node(GraphNodeKind.DERIVED_SEGMENT_CURVE, segmentId));
        curvePayload.put("segmentId", segmentId);
        curvePayload.put("outputId", outputId);
        curvePayload.put("massId", massId);
        curvePayload.put("calibrationId", calibrationId);
        curvePayload.put("rateId", rateId);
        curvePayload.put("atmosphereId", atmosphereId);
        curvePayload.put("mode", mode);
        curvePayload.put("cycleIndex", cycleIndex);
        curvePayload.put("diagnostics", report.diagnostics);
        curvePayload.put("reversedHeatFlow", report.reversedHeatFlow);
        curvePayload.put("direction", report.direction.code);
        emit(ProjectEvent.DERIVATION_COMPLETED, actor,
                "曲线为派生层：原始表示保留，规范化只在此发生；加热/冷却各自成段",
                curvePayload);
        return report;
    }

    public void archiveTangent(TangentChoice tc, String actor) {
        tangents.put(tc.tangentId, tc);
        graph.addVersion(node(GraphNodeKind.SRC_TANGENT, tc.tangentId), GraphNodeKind.SRC_TANGENT,
                List.of(), write(Map.of("segmentId", tc.segmentId, "preStartK", tc.preStartK,
                        "preEndK", tc.preEndK, "postStartK", tc.postStartK, "postEndK", tc.postEndK)),
                "OK", null, DependencyGraph.RULE_VERSION);
        emit(ProjectEvent.SOURCE_ARCHIVED, actor,
                "用户选定切线独立存档；不同切线产生并列基线候选，差异保留",
                Map.of("nodeId", node(GraphNodeKind.SRC_TANGENT, tc.tangentId),
                        "tangentId", tc.tangentId, "segmentId", tc.segmentId,
                        "preStartK", tc.preStartK, "preEndK", tc.preEndK,
                        "postStartK", tc.postStartK, "postEndK", tc.postEndK,
                        "basisNote", tc.basis == null ? "" : tc.basis));
    }

    // ------------------------------------------------------------------
    // 传播失效
    // ------------------------------------------------------------------

    private void propagateStale(GraphNodeKind kind, String nodeId, String actor, String reason) {
        List<String> affected = graph.affectedBy(kind, nodeId);
        for (String nid : affected) {
            graph.markStale(nid);
        }
        emit(ProjectEvent.STALE_PROPAGATED, actor,
                "依赖图传播：仅标记受影响派生为过期；其余节点当前版本仍有效；"
                        + "质量修订时全部归一化派生过期",
                Map.of("changed", DependencyGraph.key(kind, nodeId), "reason", reason,
                        "affectedNodes", affected));
    }

    public DependencyGraph graph() {
        return graph;
    }

    public static String node(GraphNodeKind kind, String id) {
        return DependencyGraph.key(kind, id);
    }

    public static String write(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

// ------------------------------------------------------------------
    // 基线并列候选 + 转折 + 焓变（每次推导独立版本，失败保留诊断候选）
    // ------------------------------------------------------------------

    /**
     * 用指定切线提出一个基线候选；同一段上多切线/多类型得到多候选并存。
     * 区间为空、越界等产生 ERROR 候选（保留，不吞掉），并触发 BOUNDARY_TRIGGERED 记录。
     */
    public BaselineResult proposeBaseline(String segmentId, String tangentId, TransitionType type,
                                          String actor) {
        ensureSegmentFresh(segmentId);
        Segment seg = segments.get(segmentId);
        TangentChoice tc = tangents.get(tangentId);
        String candidateId = "bl_" + segmentId + "_" + type.code + "_"
                + (baselineCandidates.getOrDefault(segmentId, List.of()).size() + 1);
        BaselineResult base = BaselineEngine.compute(candidateId, seg, tc, type);
        baselineCandidates.computeIfAbsent(segmentId, k -> new ArrayList<>()).add(base);

        List<NodeVersion.DepRef> deps = new ArrayList<>();
        deps.add(new NodeVersion.DepRef(GraphNodeKind.DERIVED_SEGMENT_CURVE, segmentId,
                graph.currentVersion(node(GraphNodeKind.DERIVED_SEGMENT_CURVE, segmentId)).version));
        deps.add(new NodeVersion.DepRef(GraphNodeKind.SRC_TANGENT, tangentId,
                graph.currentVersion(node(GraphNodeKind.SRC_TANGENT, tangentId)).version));
        graph.addVersion(node(GraphNodeKind.DERIVED_BASELINE, candidateId),
                GraphNodeKind.DERIVED_BASELINE, deps, write(baselineMap(base)),
                base.status, base.diagnostic, DependencyGraph.RULE_VERSION);

        emit(ProjectEvent.BASELINE_PROPOSED, actor,
                "多个合理基线保留为并列候选；采纳与否由独立裁定事件记录，计算结果不可变",
                Map.of("segmentId", segmentId, "candidateId", candidateId, "tangentId", tangentId,
                        "type", type.code, "status", base.status,
                        "diagnostic", base.diagnostic == null ? "" : base.diagnostic));

        if ("ERROR".equals(base.status) || "AMBIGUOUS".equals(base.status)) {
            String code = base.diagnostic != null && base.diagnostic.contains("EMPTY")
                    ? "EMPTY_INTERVAL" : "AMBIGUOUS_BASELINE";
            emit(ProjectEvent.BOUNDARY_TRIGGERED, actor,
                    "边界组合：空区间/外推起点必须留下输入片段、判定状态与受影响对象，不得静默丢弃",
                    Map.of("scenario", code, "segmentId", segmentId, "candidateId", candidateId,
                            "status", base.status, "affectedObjects",
                            List.of(node(GraphNodeKind.DERIVED_BASELINE, candidateId)),
                            "inputFragment", Map.of("preStartK", tc.preStartK, "preEndK", tc.preEndK,
                                    "postStartK", tc.postStartK, "postEndK", tc.postEndK)));
        }
        return base;
    }

    /** 对基线候选计算转折特征与焓变；方向未决时符号保留为 UNRESOLVED。 */
    public EnthalpyResult analyzeCandidate(String segmentId, BaselineResult base, double rateErrFrac,
                                           double massErrFrac, String actor) {
        ensureSegmentFresh(segmentId);
        Segment seg = segments.get(segmentId);
        TransitionType type = base.type;
        String transitionId = "tr_" + base.candidateId;
        TransitionResult tr = TransitionAnalyzer.analyze(transitionId, seg, base, type);
        transitionResults.computeIfAbsent(segmentId, k -> new ArrayList<>()).add(tr);
        graph.addVersion(node(GraphNodeKind.DERIVED_TRANSITION, transitionId),
                GraphNodeKind.DERIVED_TRANSITION,
                List.of(new NodeVersion.DepRef(GraphNodeKind.DERIVED_BASELINE, base.candidateId,
                        graph.currentVersion(node(GraphNodeKind.DERIVED_BASELINE, base.candidateId)).version),
                        new NodeVersion.DepRef(GraphNodeKind.DERIVED_SEGMENT_CURVE, segmentId,
                                graph.currentVersion(node(GraphNodeKind.DERIVED_SEGMENT_CURVE, segmentId)).version)),
                write(transitionMap(tr)), tr.status, tr.diagnostic, DependencyGraph.RULE_VERSION);

        String integrationId = "en_" + base.candidateId;
        EnthalpyResult en = EnthalpyCalculator.integrate(integrationId, seg, base, tr,
                rateErrFrac, massErrFrac);
        enthalpyResults.computeIfAbsent(segmentId, k -> new ArrayList<>()).add(en);
        String rId = rateIdOf(segmentId);
        String mId = massIdOf(segmentId);
        graph.addVersion(node(GraphNodeKind.DERIVED_ENTHALPY, integrationId),
                GraphNodeKind.DERIVED_ENTHALPY,
                List.of(new NodeVersion.DepRef(GraphNodeKind.DERIVED_TRANSITION, transitionId,
                                graph.currentVersion(node(GraphNodeKind.DERIVED_TRANSITION, transitionId)).version),
                        new NodeVersion.DepRef(GraphNodeKind.SRC_RATE, rId,
                                graph.currentVersion(node(GraphNodeKind.SRC_RATE, rId)).version),
                        new NodeVersion.DepRef(GraphNodeKind.SRC_MASS, mId,
                                graph.currentVersion(node(GraphNodeKind.SRC_MASS, mId)).version)),
                write(enthalpyMap(en)), en.status, en.diagnostic, DependencyGraph.RULE_VERSION);

        emit(ProjectEvent.DERIVATION_COMPLETED, actor,
                "焓变为质量归一化派生：质量修订后过期；方向未决时只保留绝对量，不臆造符号",
                Map.of("segmentId", segmentId, "candidateId", base.candidateId,
                        "transitionId", transitionId, "integrationId", integrationId,
                        "status", en.status, "heatDirection", en.heatDirection,
                        "diagnostic", en.diagnostic == null ? "" : en.diagnostic));
        return en;
    }

    public void acceptCandidate(String segmentId, String candidateId, String actor, String basis) {
        String previous = acceptedCandidate.get(segmentId);
        acceptedCandidate.put(segmentId, candidateId);
        rejectedCandidates.remove(segmentId + ":" + candidateId);
        emit(ProjectEvent.CANDIDATE_ACCEPTED, actor, basis,
                Map.of("segmentId", segmentId, "candidateId", candidateId,
                        "previousAccepted", previous == null ? "" : previous,
                        "unselectedCandidatesRetained", true));
    }

    /** 否定候选：候选保留，只记录裁定；与已接受候选冲突时记 CONFLICTING_DECISION。 */
    public void rejectCandidate(String segmentId, String candidateId, String actor, String basis) {
        rejectedCandidates.put(segmentId + ":" + candidateId, basis);
        String type = ProjectEvent.CANDIDATE_REJECTED;
        String b = basis;
        if (candidateId.equals(acceptedCandidate.get(segmentId))) {
            type = ProjectEvent.CONFLICTING_DECISION;
            b = basis + "；与既有接受裁定冲突：两条互相冲突的人工决定都保留，由后续比较分支解决";
        }
        emit(type, actor, b, Map.of("segmentId", segmentId, "candidateId", candidateId));
    }

    public void recordSegmentSwitch(String fromSegmentId, String toSegmentId, String actor) {
        emit(ProjectEvent.SEGMENT_SWITCH, actor,
                "循环段切换：加热与冷却段不混用；跨段积分返回 CROSSES_SEGMENTS",
                Map.of("fromSegmentId", fromSegmentId, "toSegmentId", toSegmentId));
    }

    public void recordBoundary(String scenario, String segmentId, String actor,
                               Map<String, Object> fragment, String status, List<String> affected) {
        Map<String, Object> p = new LinkedHashMap<>(fragment);
        p.put("scenario", scenario);
        p.put("segmentId", segmentId);
        p.put("status", status);
        p.put("affectedObjects", affected);
        emit(ProjectEvent.BOUNDARY_TRIGGERED, actor,
                "边界情形必须留下输入片段、判定状态与受影响对象", p);
    }

    // ------------------------------------------------------------------
    // 访问器
    // ------------------------------------------------------------------

    public List<BaselineResult> baselines(String segmentId) {
        return baselineCandidates.getOrDefault(segmentId, List.of());
    }

    public List<TransitionResult> transitions(String segmentId) {
        return transitionResults.getOrDefault(segmentId, List.of());
    }

    public List<EnthalpyResult> enthalpies(String segmentId) {
        return enthalpyResults.getOrDefault(segmentId, List.of());
    }

    public String accepted(String segmentId) {
        return acceptedCandidate.get(segmentId);
    }

    public boolean isRejected(String segmentId, String candidateId) {
        return rejectedCandidates.containsKey(segmentId + ":" + candidateId);
    }

    String rateIdOf(String segmentId) {
        for (ProjectEvent ev : events) {
            if ("DERIVATION_COMPLETED".equals(ev.type) && segmentId.equals(ev.payload.get("segmentId"))
                    && ev.payload.containsKey("rateId")) {
                return (String) ev.payload.get("rateId");
            }
        }
        return "rate_" + segmentId;
    }

    String massIdOf(String segmentId) {
        for (ProjectEvent ev : events) {
            if ("DERIVATION_COMPLETED".equals(ev.type) && segmentId.equals(ev.payload.get("segmentId"))
                    && ev.payload.containsKey("massId")) {
                return (String) ev.payload.get("massId");
            }
        }
        return "mass_sample";
    }

    int massVersionOf(String segmentId) {
        String mid = massIdOf(segmentId);
        NodeVersion nv = graph.currentVersion(node(GraphNodeKind.SRC_MASS, mid));
        return nv == null ? 1 : nv.version;
    }

    // ------------------------------------------------------------------
    // 结果映射（稳定身份字段）
    // ------------------------------------------------------------------

    public Map<String, Object> baselineMap(BaselineResult b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.candidateId);
        m.put("segmentId", b.segmentId);
        m.put("tangentId", b.tangentId);
        m.put("type", b.type.code);
        m.put("onsetK", round(b.onsetK));
        m.put("endsetK", round(b.endsetK));
        m.put("midpointK", round(b.midpointK));
        m.put("stepHeightWg", round(b.stepHeightWg));
        m.put("status", b.status);
        m.put("diagnostic", b.diagnostic == null ? "" : b.diagnostic);
        return m;
    }

    public Map<String, Object> transitionMap(TransitionResult t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.transitionId);
        m.put("candidateId", t.candidateId);
        m.put("segmentId", t.segmentId);
        m.put("type", t.type.code);
        m.put("onsetK", round(t.onsetK));
        m.put("peakK", round(t.peakK));
        m.put("endsetK", round(t.endsetK));
        m.put("peakAmplitudeWg", round(t.peakAmplitudeWg));
        m.put("heatSign", t.heatSign);
        m.put("signResolved", t.signResolved);
        m.put("status", t.status);
        m.put("diagnostic", t.diagnostic == null ? "" : t.diagnostic);
        return m;
    }

    public Map<String, Object> enthalpyMap(EnthalpyResult e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.integrationId);
        m.put("transitionId", e.transitionId);
        m.put("candidateId", e.candidateId);
        m.put("segmentId", e.segmentId);
        m.put("onsetK", round(e.onsetK));
        m.put("endsetK", round(e.endsetK));
        m.put("enthalpyJg", Double.isNaN(e.enthalpyJg) ? null : round(e.enthalpyJg));
        m.put("magnitudeJg", round(e.magnitudeJg));
        m.put("signResolved", e.signResolved);
        m.put("heatDirection", e.heatDirection);
        List<Map<String, Object>> errs = new ArrayList<>();
        for (EnthalpyResult.ErrorContribution c : e.errorSources) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("code", c.code);
            cm.put("label", c.label);
            cm.put("contributionJg", round(c.contributionJg));
            cm.put("basis", c.basis);
            errs.add(cm);
        }
        m.put("errorSources", errs);
        m.put("status", e.status);
        m.put("diagnostic", e.diagnostic == null ? "" : e.diagnostic);
        return m;
    }

    static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : Double.parseDouble(o.toString());
    }

    static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    static Object round(double v) {
        if (!Double.isFinite(v)) {
            return null;
        }
        return Math.round(v * 1e6) / 1e6;
    }

    // ------------------------------------------------------------------
    // 事件应用（前向执行与持久化后重放走同一套确定性重建）
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void applyEvent(Map<String, Object> raw, boolean replaying) {
        long seq = ((Number) raw.get("seq")).longValue();
        String type = (String) raw.get("type");
        String actor = (String) raw.getOrDefault("actor", "system");
        String basis = (String) raw.getOrDefault("basis", "");
        Map<String, Object> p = (Map<String, Object>) raw.getOrDefault("payload", Map.of());
        switch (type) {
            case ProjectEvent.SOURCE_ARCHIVED -> applySourceArchived(p);
            case ProjectEvent.MASS_REVISED -> applyMassRevised(p);
            case ProjectEvent.DERIVATION_COMPLETED -> applyDerivationCompleted(p);
            case ProjectEvent.BASELINE_PROPOSED -> applyBaselineProposed(p);
            case ProjectEvent.CANDIDATE_ACCEPTED ->
                    acceptedCandidate.put((String) p.get("segmentId"), (String) p.get("candidateId"));
            case ProjectEvent.CANDIDATE_REJECTED, ProjectEvent.CONFLICTING_DECISION ->
                    rejectedCandidates.put(p.get("segmentId") + ":" + p.get("candidateId"), basis);
            case ProjectEvent.STALE_PROPAGATED -> applyStalePropagated(p);
            case ProjectEvent.SEGMENT_SWITCH, ProjectEvent.INTERVAL_ADJUSTED,
                 ProjectEvent.BOUNDARY_TRIGGERED, ProjectEvent.DERIVATION_STARTED,
                 ProjectEvent.DERIVATION_FAILED -> {
                // 纯记录事件：结论保存在其它事件的载荷里，这里无需状态动作
            }
            default -> throw new IllegalStateException("unknown event type: " + type);
        }
        events.add(new ProjectEvent(seq, type, actor, basis, p, (String) raw.get("timestamp")));
    }

    /**
     * 按需重算：若某段曲线当前指针因来源修订被移除，用该段最近一次曲线派生事件的输入
     * （已经指向最新来源版本）重新计算并产生新版本。重算结果进入图版本账本但不产生
     * 重复事件——持久化事件流重放时同样走这里，因此结论可重复。
     */
    @SuppressWarnings("unchecked")
    private void ensureSegmentFresh(String segmentId) {
        if (graph.currentVersion(node(GraphNodeKind.DERIVED_SEGMENT_CURVE, segmentId)) != null) {
            return;
        }
        Map<String, Object> latest = null;
        for (ProjectEvent ev : events) {
            if (ProjectEvent.DERIVATION_COMPLETED.equals(ev.type)
                    && segmentId.equals(ev.payload.get("segmentId")) && ev.payload.containsKey("mode")) {
                latest = ev.payload;
            }
        }
        if (latest == null) {
            throw new IllegalStateException("no curve derivation event for stale segment " + segmentId);
        }
        applyDerivationCompleted(latest);
    }

    private void addSourceNode(GraphNodeKind kind, String id, Map<String, Object> payload,
                               String status, String diag) {
        graph.addVersion(node(kind, id), kind, List.of(), write(payload), status, diag,
                DependencyGraph.RULE_VERSION);
    }

    @SuppressWarnings("unchecked")
    private void applySourceArchived(Map<String, Object> p) {
        String nid = (String) p.get("nodeId");
        if (nid == null) {
            return;
        }
        if (nid.startsWith(GraphNodeKind.SRC_INSTRUMENT.code + ":")) {
            String outputId = (String) p.get("outputId");
            if (!outputs.containsKey(outputId)) {
                String fileName = (String) p.get("fileName");
                if (fileName != null && getClass().getResource("/casepack/" + fileName) != null) {
                    outputs.put(outputId, dsc.casepack.CasePack.instrument(outputId, fileName));
                }
            }
            addSourceNode(GraphNodeKind.SRC_INSTRUMENT, outputId,
                    Map.of("fileName", p.get("fileName"), "resolvedDirection",
                            p.get("resolvedDirection")),
                    "UNRESOLVED".equals(p.get("resolvedDirection")) ? "AMBIGUOUS" : "OK",
                    "UNRESOLVED".equals(p.get("resolvedDirection"))
                            ? "DIRECTION_UNRESOLVED" : null);
        } else if (nid.startsWith(GraphNodeKind.SRC_MASS.code + ":")) {
            String massId = (String) p.get("massId");
            int mv = ((Number) p.getOrDefault("version", 1)).intValue();
            masses.put(massId, new MassVersion(massId, mv, num(p.get("milligrams")),
                    str(p.getOrDefault("rawText", "")), "replayed",
                    str(p.getOrDefault("note", "")), mv > 1 ? massId : null));
            addSourceNode(GraphNodeKind.SRC_MASS, massId,
                    Map.of("milligrams", p.get("milligrams"), "version", mv), "OK", null);
        } else if (nid.startsWith(GraphNodeKind.SRC_ATMOSPHERE.code + ":")) {
            atmospheres.put((String) p.get("atmosphereId"), new AtmosphereRecord(
                    (String) p.get("atmosphereId"), (String) p.get("gas"), 50.0,
                    str(p.getOrDefault("rawFlow", "50 mL/min")), "replayed"));
            addSourceNode(GraphNodeKind.SRC_ATMOSPHERE, (String) p.get("atmosphereId"),
                    Map.of("gas", p.get("gas")), "OK", null);
        } else if (nid.startsWith(GraphNodeKind.SRC_CALIBRATION.code + ":")) {
            int cv = calibrations.containsKey(p.get("calibrationId"))
                    ? calibrations.get(p.get("calibrationId")).offsetK == num(p.get("offsetK")) ? 1 : 2 : 1;
            calibrations.put((String) p.get("calibrationId"), new CalibrationRecord(
                    (String) p.get("calibrationId"), "replayed", num(p.get("offsetK")),
                    "replayed", "replayed"));
            addSourceNode(GraphNodeKind.SRC_CALIBRATION, (String) p.get("calibrationId"),
                    Map.of("offsetK", p.get("offsetK")), "OK", null);
        } else if (nid.startsWith(GraphNodeKind.SRC_RATE.code + ":")) {
            scanRates.put((String) p.get("rateId"), str(p.get("rawText")) + "|" + num(p.get("kPerMin")));
            addSourceNode(GraphNodeKind.SRC_RATE, (String) p.get("rateId"),
                    Map.of("kPerMin", p.get("kPerMin")), "OK", null);
        } else if (nid.startsWith(GraphNodeKind.SRC_TANGENT.code + ":")) {
            tangents.put((String) p.get("tangentId"), new TangentChoice((String) p.get("tangentId"),
                    (String) p.get("segmentId"), num(p.get("preStartK")), num(p.get("preEndK")),
                    num(p.get("postStartK")), num(p.get("postEndK")),
                    str(p.getOrDefault("basisNote", "")), "replayed"));
            addSourceNode(GraphNodeKind.SRC_TANGENT, (String) p.get("tangentId"),
                    Map.of("segmentId", p.get("segmentId")), "OK", null);
        }
    }

    private void applyMassRevised(Map<String, Object> p) {
        String massId = (String) p.get("massId");
        int version = ((Number) p.get("version")).intValue();
        masses.put(massId, new MassVersion(massId, version, num(p.get("milligrams")),
                str(p.getOrDefault("rawText", "")), "replayed",
                str(p.getOrDefault("note", "")), massId));
        addSourceNode(GraphNodeKind.SRC_MASS, massId,
                Map.of("milligrams", p.get("milligrams"), "version", version), "OK", null);
    }

    @SuppressWarnings("unchecked")
    private void applyStalePropagated(Map<String, Object> p) {
        for (Object o : (List<Object>) p.getOrDefault("affectedNodes", List.of())) {
            graph.markStale((String) o);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyDerivationCompleted(Map<String, Object> p) {
        if (!p.containsKey("segmentId") || !p.containsKey("mode")) {
            // 焓变类 DERIVATION_COMPLETED：结果在 payload 中，重建结果对象与图版本
            if (p.containsKey("integrationId")) {
                rebuildEnthalpy(p);
            }
            return;
        }
        String segmentId = (String) p.get("segmentId");
        String outputId = (String) p.get("outputId");
        String massId = (String) p.get("massId");
        String calibrationId = (String) p.get("calibrationId");
        String rateId = (String) p.get("rateId");
        String atmosphereId = (String) p.get("atmosphereId");
        int cycle = ((Number) p.get("cycleIndex")).intValue();
        String mode = (String) p.get("mode");
        InstrumentOutput out = outputs.get(outputId);
        MassVersion mass = masses.get(massId);
        CalibrationRecord cal = calibrations.get(calibrationId);
        double rate = rateId == null ? Double.NaN
                : Double.parseDouble(scanRates.get(rateId).substring(scanRates.get(rateId).lastIndexOf('|') + 1));
        CsvImport.ImportReport report = CsvImport.importOutput(out, segmentId, cycle, mode, mass, cal,
                rate, atmosphereId);
        segments.put(segmentId, report.segment);
        reports.put(segmentId, report);
        List<NodeVersion.DepRef> deps = new ArrayList<>();
        deps.add(new NodeVersion.DepRef(GraphNodeKind.SRC_INSTRUMENT, outputId,
                graph.currentVersion(node(GraphNodeKind.SRC_INSTRUMENT, outputId)).version));
        deps.add(new NodeVersion.DepRef(GraphNodeKind.SRC_MASS, massId,
                graph.currentVersion(node(GraphNodeKind.SRC_MASS, massId)).version));
        deps.add(new NodeVersion.DepRef(GraphNodeKind.SRC_RATE, rateId,
                graph.currentVersion(node(GraphNodeKind.SRC_RATE, rateId)).version));
        deps.add(new NodeVersion.DepRef(GraphNodeKind.SRC_ATMOSPHERE, atmosphereId,
                graph.currentVersion(node(GraphNodeKind.SRC_ATMOSPHERE, atmosphereId)).version));
        deps.add(new NodeVersion.DepRef(GraphNodeKind.SRC_CALIBRATION, calibrationId,
                graph.currentVersion(node(GraphNodeKind.SRC_CALIBRATION, calibrationId)).version));
        List<String> diag = (List<String>) p.getOrDefault("diagnostics", List.of());
        String status = diag.stream().anyMatch(d -> d.contains("NON_MONOTONIC")) ? "ERROR"
                : (report.segment.badPoints.isEmpty() ? "OK" : "AMBIGUOUS");
        graph.addVersion(node(GraphNodeKind.DERIVED_SEGMENT_CURVE, segmentId),
                GraphNodeKind.DERIVED_SEGMENT_CURVE, deps,
                write(Map.of("points", report.segment.size(), "badPoints",
                        report.segment.badPoints.size(), "mode", mode)),
                status, String.join(" | ", diag), DependencyGraph.RULE_VERSION);
    }

    @SuppressWarnings("unchecked")
    private void applyBaselineProposed(Map<String, Object> p) {
        String segmentId = (String) p.get("segmentId");
        String candidateId = (String) p.get("candidateId");
        String tangentId = (String) p.get("tangentId");
        TransitionType type = TransitionType.valueOf(((String) p.get("type")).toUpperCase());
        // 用当前（重放时点的）来源重新计算；若曲线因来源修订过期，先用新来源重算（依赖图只传播子树）
        ensureSegmentFresh(segmentId);
        BaselineResult base = BaselineEngine.compute(candidateId, segments.get(segmentId),
                tangents.get(tangentId), type);
        baselineCandidates.computeIfAbsent(segmentId, k -> new ArrayList<>()).add(base);
        List<NodeVersion.DepRef> deps = List.of(
                new NodeVersion.DepRef(GraphNodeKind.DERIVED_SEGMENT_CURVE, segmentId,
                        graph.currentVersion(node(GraphNodeKind.DERIVED_SEGMENT_CURVE, segmentId)).version),
                new NodeVersion.DepRef(GraphNodeKind.SRC_TANGENT, tangentId,
                        graph.currentVersion(node(GraphNodeKind.SRC_TANGENT, tangentId)).version));
        graph.addVersion(node(GraphNodeKind.DERIVED_BASELINE, candidateId),
                GraphNodeKind.DERIVED_BASELINE, deps, write(baselineMap(base)),
                base.status, base.diagnostic, DependencyGraph.RULE_VERSION);
    }

    @SuppressWarnings("unchecked")
    private void rebuildEnthalpy(Map<String, Object> p) {
        String segmentId = (String) p.get("segmentId");
        String candidateId = (String) p.get("candidateId");
        BaselineResult base = baselineCandidates.getOrDefault(segmentId, List.of()).stream()
                .filter(b -> b.candidateId.equals(candidateId)).findFirst().orElseThrow();
        TransitionResult tr = TransitionAnalyzer.analyze((String) p.get("transitionId"),
                segments.get(segmentId), base, base.type);
        transitionResults.computeIfAbsent(segmentId, k -> new ArrayList<>()).add(tr);
        graph.addVersion(node(GraphNodeKind.DERIVED_TRANSITION, tr.transitionId),
                GraphNodeKind.DERIVED_TRANSITION,
                List.of(new NodeVersion.DepRef(GraphNodeKind.DERIVED_BASELINE, candidateId,
                        graph.currentVersion(node(GraphNodeKind.DERIVED_BASELINE, candidateId)).version)),
                write(transitionMap(tr)), tr.status, tr.diagnostic, DependencyGraph.RULE_VERSION);
        EnthalpyResult en = EnthalpyCalculator.integrate((String) p.get("integrationId"),
                segments.get(segmentId), base, tr, 0.01, 0.005);
        enthalpyResults.computeIfAbsent(segmentId, k -> new ArrayList<>()).add(en);
        graph.addVersion(node(GraphNodeKind.DERIVED_ENTHALPY, en.integrationId),
                GraphNodeKind.DERIVED_ENTHALPY,
                List.of(new NodeVersion.DepRef(GraphNodeKind.DERIVED_TRANSITION, tr.transitionId,
                        graph.currentVersion(node(GraphNodeKind.DERIVED_TRANSITION, tr.transitionId)).version)),
                write(enthalpyMap(en)), en.status, en.diagnostic, DependencyGraph.RULE_VERSION);
    }
}
