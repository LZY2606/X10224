package dsc.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.casepack.CasePack;
import dsc.casepack.CasePackLoader;
import dsc.domain.Artifact;
import dsc.domain.ArtifactKind;
import dsc.domain.Decision;
import dsc.domain.DomainEvent;
import dsc.domain.RawPoint;
import dsc.domain.SourceKind;
import dsc.domain.SourceRecord;
import dsc.domain.Units;
import dsc.rules.BaselineCandidate;
import dsc.rules.BaselineEngine;
import dsc.rules.BaselineResult;
import dsc.rules.IntegrationEngine;
import dsc.rules.IntegrationResult;
import dsc.rules.NormalizedCurve;
import dsc.rules.NormalizationEngine;
import dsc.rules.RuleSet;
import dsc.store.ConflictException;
import dsc.store.Json;
import dsc.store.Repository;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 应用编排核心：来源存档、依赖图传播重算、版本指针切换、事件留痕。
 *
 * 关键不变量：
 *  1. 一次提交 = 一个事务：全部新版本写成功后才移动 current_pointers；
 *  2. 重算只重建受影响目标，并对未受影响目标发 DERIVED_STILL_VALID 说明；
 *  3. 历史事件保存依赖版本快照，回放到旧事件时恢复当时的指针视图；
 *  4. 并列候选与未被采纳的决定永不删除。
 */
public class AppEngine {
    private final Repository repo;
    private final Map<String, SegmentMeta> segments = new LinkedHashMap<>();
    private final List<IntervalDef> intervals = new ArrayList<>();
    private RuleSet ruleSet = RuleSet.v1();
    private final IntegrationEngine.Calibration cal = new IntegrationEngine.Calibration();
    private final BaselineEngine baselineEngine = new BaselineEngine();
    private final IntegrationEngine integrationEngine = new IntegrationEngine();

    public AppEngine(Repository repo) {
        this.repo = repo;
    }

    // =====================================================================
    // 案例包导入
    // =====================================================================

    public void importCasePack(CasePack pack, Path resourceDir) throws Exception {
        repo.beginTx();
        try {
            repo.setCaseMeta("case_id", pack.caseId());
            repo.setCaseMeta("case_title", pack.title());
            repo.setCaseMeta("case_schema", String.valueOf(pack.schemaVersion()));
            repo.setCaseMeta("rule_version", RuleSet.v1().version());

            long seq = 1;
            for (CasePack.SegmentDef sd : pack.segments()) {
                String rel = CasePackLoader.RESOURCE_ROOT + "/" + sd.file();
                CasePackLoader.CsvData csv;
                Path p = resourceDir == null ? null : resourceDir.resolve(sd.file());
                if (p != null && java.nio.file.Files.exists(p)) {
                    csv = CasePackLoader.parseSegmentCsv(p);
                } else {
                    csv = CasePackLoader.parseSegmentResource(rel);
                }
                SegmentMeta meta = SegmentMeta.from(sd.segmentId(), sd.cycle(),
                        sd.programDirection(), sd.declaredSign(), sd.file(), csv,
                        sd.rateValue(), sd.rateUnit(), sd.gas(), sd.flowRaw(), sd.flowUnit());
                segments.put(sd.segmentId(), meta);
                seq = storeInstrumentSource(sd, csv.header(), csv.points(), seq);
                storeScalarSource(SourceKind.SCAN_RATE, sd.segmentId(),
                        "rate-seg-" + sd.segmentId(), sd.rateValue(), sd.rateUnit(),
                        "扫描速率（扫描程序）: " + sd.rateValue() + " " + sd.rateUnit(), seq++);
                ObjectNode atm = Json.MAPPER.createObjectNode();
                atm.put("gas", sd.gas());
                atm.put("flowRaw", sd.flowRaw());
                atm.put("flowUnit", sd.flowUnit());
                insertSource("atm-seg-" + sd.segmentId(), SourceKind.ATMOSPHERE, sd.segmentId(), 1,
                        "气氛记录: " + sd.gas() + " " + sd.flowRaw() + " " + sd.flowUnit(),
                        atm.toString(), seq++);
            }

            for (CasePack.SourceDef d : pack.sources()) {
                insertSource(d.id(), SourceKind.valueOf(d.kind()), d.refId(), d.version(),
                        d.originalRep(), d.node().toString(), seq++);
            }

            intervals.clear();
            for (CasePack.IntervalDef iv : pack.intervals()) {
                IntervalDef def = new IntervalDef(iv.intervalId(), iv.segmentId(),
                        iv.transitionId(), iv.t0C() + 273.15, iv.t1C() + 273.15, iv.hint());
                intervals.add(def);
                repo.upsertInterval(def.intervalId(), def.segmentId(), def.transitionId(),
                        def.t0K(), def.t1K(), seq++);
            }

            long rev = repo.currentRevision();
            recomputeAll(seq);
            emit(EngineEvent.CASE_IMPORTED,
                    "案例包已导入：仪器输出/质量/速率/气氛/温度标定/用户切线六类来源分别存档",
                    importPayload(pack));
            for (String segId : segments.keySet()) {
                emitSignAndSegmentEvents(segId);
            }
            repo.bumpRevision(rev);
            repo.commitTx();
        } catch (Exception e) {
            repo.rollbackTx();
            throw e;
        }
    }

    private long storeInstrumentSource(CasePack.SegmentDef sd, String header,
                                       List<RawPoint> points, long seqStart) throws Exception {
        ObjectNode payload = Json.MAPPER.createObjectNode();
        payload.put("fileName", sd.file());
        payload.put("header", header);
        payload.put("declaredSign", sd.declaredSign());
        payload.put("programDirection", sd.programDirection());
        payload.put("cycle", sd.cycle());
        ArrayNode arr = payload.putArray("points");
        for (RawPoint p : points) {
            ObjectNode o = arr.addObject();
            o.put("seq", p.seq());
            o.put("tRaw", p.tRaw());
            o.put("hfRaw", p.hfRaw());
            if (p.tK() != null) {
                o.put("tK", p.tK());
            }
            if (p.hfW() != null) {
                o.put("hfW", p.hfW());
            }
            o.put("excluded", p.excluded());
            if (p.diag() != null) {
                o.put("diag", p.diag());
            }
        }
        insertSource("inst-seg-" + sd.segmentId(), SourceKind.INSTRUMENT_OUTPUT, sd.segmentId(), 1,
                header + " | 文件原名: " + sd.file(), payload.toString(), seqStart);
        return seqStart + 1;
    }

    private void storeScalarSource(SourceKind kind, String refId, String id,
                                   String value, String unit, String rep, long seq) {
        ObjectNode o = Json.MAPPER.createObjectNode();
        o.put("value", value);
        o.put("unit", unit);
        insertSource(id, kind, refId, 1, rep, o.toString(), seq);
    }

    private void insertSource(String id, SourceKind kind, String refId, int version,
                              String rep, String payload, long seq) {
        int existing = repo.latestSourceVersion(id);
        if (existing >= version) {
            version = existing;
            return;
        }
        repo.insertSource(new SourceRecord(id, kind, refId, version, rep, payload, seq));
    }

    // =====================================================================
    // 来源读取（始终取当前指针版本）
    // =====================================================================

    private JsonNode latestSourcePayload(String id) {
        int v = repo.latestSourceVersion(id);
        for (SourceRecord s : repo.allSources()) {
            if (s.id().equals(id) && s.version() == v) {
                try {
                    return Json.MAPPER.readTree(s.payloadJson());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return null;
    }

    private SourceRecord latestSourceRecord(String id) {
        int v = repo.latestSourceVersion(id);
        for (SourceRecord s : repo.allSources()) {
            if (s.id().equals(id) && s.version() == v) {
                return s;
            }
        }
        return null;
    }

    private double currentMassG(String massId) {
        JsonNode n = latestSourcePayload(massId);
        Units.Parse p = Units.massG(n.get("value").asText(), n.get("unit").asText());
        return p.value == null ? -1 : p.value;
    }

    private double[] currentCalibration() {
        SourceRecord s = null;
        int best = 0;
        for (SourceRecord r : repo.allSources()) {
            if (r.kind() == SourceKind.TEMPERATURE_CAL && r.version() > best) {
                s = r;
                best = r.version();
            }
        }
        if (s == null) {
            return new double[]{0, 1};
        }
        try {
            JsonNode n = Json.MAPPER.readTree(s.payloadJson());
            double off = n.path("offsetC").asDouble(0);
            double slope = n.path("slope").asDouble(1);
            return new double[]{off, slope};
        } catch (Exception e) {
            return new double[]{0, 1};
        }
    }

    private List<BaselineEngine.UserTangent> userTangentsFor(String intervalRef) {
        List<BaselineEngine.UserTangent> out = new ArrayList<>();
        String massId = "mass-sample-PET-042";
        double mass = currentMassG(massId);
        for (SourceRecord s : repo.allSources()) {
            if (s.kind() != SourceKind.USER_TANGENT || !s.refId().equals(intervalRef)) {
                continue;
            }
            try {
                JsonNode n = Json.MAPPER.readTree(s.payloadJson());
                double t0 = n.get("t0C").asDouble() + 273.15;
                double t1 = n.get("t1C").asDouble() + 273.15;
                double y0 = Units.powerW(n.get("y0mW").asText(), "mW").value / mass;
                double y1 = Units.powerW(n.get("y1mW").asText(), "mW").value / mass;
                out.add(new BaselineEngine.UserTangent(t0, y0, t1, y1, s.id(), s.version()));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    // =====================================================================
    // 依赖图重算
    // =====================================================================

    private String curveId(String segId) {
        return ArtifactKind.NORMALIZED_CURVE + ":" + segId;
    }

    private String baselineId(String intervalId) {
        return ArtifactKind.BASELINE_SET + ":" + intervalId;
    }

    private String integrationId(String transitionId) {
        return ArtifactKind.TRANSITION_INTEGRATION + ":" + transitionId;
    }

    /** 段级依赖指纹：仪器输出+质量+标定+规则版本。 */
    private String curveDepsHash(String segId) {
        JsonNode inst = latestSourcePayload("inst-seg-" + segId);
        JsonNode mass = latestSourcePayload("mass-sample-PET-042");
        double[] cal2 = currentCalibration();
        String raw = segId + "|"
                + (inst == null ? "" : inst.toString().hashCode()) + "|"
                + (mass == null ? "" : mass.toString()) + "|"
                + cal2[0] + "," + cal2[1];
        return Integer.toHexString(raw.hashCode());
    }

    private String baselineDepsHash(IntervalDef iv, String curveHash) {
        JsonNode rate = latestSourcePayload("rate-seg-" + iv.segmentId());
        JsonNode atm = latestSourcePayload("atm-seg-" + iv.segmentId());
        String tang = "";
        for (SourceRecord s : repo.allSources()) {
            if (s.kind() == SourceKind.USER_TANGENT && s.refId().equals(iv.segmentId() + "#" + iv.intervalId())) {
                tang += s.id() + "v" + s.version() + ";";
            }
        }
        String raw = iv.intervalId() + "|" + String.format("%.4f,%.4f", iv.t0K(), iv.t1K())
                + "|" + curveHash + "|" + ruleSet.version() + "|"
                + (rate == null ? "" : rate.toString())
                + (atm == null ? "" : atm.toString()) + "|" + tang;
        return Integer.toHexString(raw.hashCode());
    }

    private String integrationDepsHash(String baselineHash) {
        JsonNode mass = latestSourcePayload("mass-sample-PET-042");
        return Integer.toHexString((baselineHash + "|" + ruleSet.version()
                + "|" + (mass == null ? "" : mass.toString())).hashCode());
    }

    private void recomputeAll(long seqStart) throws Exception {
        recompute(segments.keySet().stream().toList(), null, seqStart);
    }

    /**
     * 依赖图传播：从受影响段开始，先重建归一化，再重建这些段上区间的基线，
     * 再重建关联积分；其余目标发 DERIVED_STILL_VALID 事件说明为何无需重算。
     *
     * @param affectedSegments 源改动直接命中的段；null 表示全部
     * @param forceIntervals   强制重建的区间（拖动/选基线）；可为 null
     */
    private void recompute(List<String> affectedSegments, List<String> forceIntervals,
                           long seqStart) throws Exception {
        if (affectedSegments == null) {
            affectedSegments = new ArrayList<>(segments.keySet());
        }
        List<String> force = forceIntervals == null ? List.of() : forceIntervals;
        long[] seq = {Math.max(seqStart, repo.nextSequence("artifacts") - 1)};

        Map<String, NormalizedCurve> curves = new LinkedHashMap<>();
        for (String segId : segments.keySet()) {
            SegmentMeta meta = segments.get(segId);
            boolean affected = affectedSegments.contains(segId);
            String hash = curveDepsHash(segId);
            Artifact current = currentArtifact(curveId(segId));
            if (current != null && !affected && Objects.equals(current.depsHash(), hash)) {
                curves.put(segId, Json.MAPPER.readValue(current.contentJson(), NormalizedCurve.class));
                continue;
            }
            NormalizedCurve curve = computeCurve(meta);
            Artifact savedCurve = persistArtifact(curveId(segId), ArtifactKind.NORMALIZED_CURVE,
                    segId, curve, hash, ruleSet.version(), ++seq[0], current == null ? null : current.version());
            curves.put(segId, Json.MAPPER.readValue(savedCurve.contentJson(), NormalizedCurve.class));
        }

        for (IntervalDef iv : intervals) {
            String ch = curveDepsHash(iv.segmentId());
            String bhash = baselineDepsHash(iv, ch);
            Artifact curB = currentArtifact(baselineId(iv.intervalId()));
            String selected0 = null;
            boolean forceThis = force.contains(iv.intervalId());
            if (curB != null) {
                JsonNode prev = Json.MAPPER.readTree(curB.contentJson());
                if (prev.has("selectedCandidateId") && !prev.get("selectedCandidateId").isNull()) {
                    selected0 = prev.get("selectedCandidateId").asText();
                }
            }
            final String selected = selected0;
            boolean stillValid = curB != null && !forceThis
                    && Objects.equals(curB.depsHash(), bhash);
            if (stillValid) {
                emit(EngineEvent.DERIVED_STILL_VALID,
                        "基线 " + iv.intervalId() + " 的依赖指纹未变（" + bhash + "），沿用 v"
                                + curB.version() + " 无需重算",
                        node -> {
                            node.put("targetId", iv.intervalId());
                            node.put("artifactId", baselineId(iv.intervalId()));
                            node.put("version", curB.version());
                            node.put("depsHash", bhash);
                        });
            } else {
                NormalizedCurve curve = curves.get(iv.segmentId());
                if (curve == null) {
                    Artifact a = currentArtifact(curveId(iv.segmentId()));
                    curve = Json.MAPPER.readValue(a.contentJson(), NormalizedCurve.class);
                }
                List<BaselineEngine.UserTangent> uts =
                        userTangentsFor(iv.segmentId() + "#" + iv.intervalId());
                BaselineResult br = baselineEngine.build(iv.intervalId(), curve,
                        iv.t0K(), iv.t1K(), ruleSet, uts);
                if (selected != null) {
                    boolean exists = br.candidates().stream()
                            .anyMatch(c -> c.candidateId().equals(selected));
                    br = new BaselineResult(br.intervalId(), br.segmentId(), br.t0K(), br.t1K(),
                            br.candidates(), exists ? selected : null,
                            appendSelectedNote(br.diagnostics(), exists, selected),
                            br.ruleVersion());
                    if (!exists) {
                        emit(EngineEvent.MULTIPLE_BASELINES_RETAINED,
                                "原选择 " + selected + " 在新候选集合中不再存在，选择重置为未决定，候选全部保留",
                                n -> n.put("intervalId", iv.intervalId()));
                    }
                }
                persistArtifact(baselineId(iv.intervalId()), ArtifactKind.BASELINE_SET,
                        iv.intervalId(), br, bhash, ruleSet.version(), ++seq[0],
                        curB == null ? null : curB.version());
                emitBaselineEvents(iv, br, curB);
            }

            String ihash = integrationDepsHash(bhash);
            Artifact curI = currentArtifact(integrationId(iv.transitionId()));
            if (curI != null && !forceThis && Objects.equals(curI.depsHash(), ihash)) {
                emit(EngineEvent.DERIVED_STILL_VALID,
                        "积分 " + iv.transitionId() + " 依赖未变，沿用 v" + curI.version(),
                        node -> {
                            node.put("targetId", iv.transitionId());
                            node.put("artifactId", integrationId(iv.transitionId()));
                            node.put("version", curI.version());
                        });
                continue;
            }
            Artifact bArt = currentArtifact(baselineId(iv.intervalId()));
            BaselineResult brNow = Json.MAPPER.readValue(bArt.contentJson(), BaselineResult.class);
            NormalizedCurve curve = curves.get(iv.segmentId());
            if (curve == null) {
                curve = Json.MAPPER.readValue(
                        currentArtifact(curveId(iv.segmentId())).contentJson(), NormalizedCurve.class);
            }
            JsonNode rateN = latestSourcePayload("rate-seg-" + iv.segmentId());
            double rate = 0;
            if (rateN != null) {
                Units.Parse rp = Units.rateKPerMin(rateN.get("value").asText(), rateN.get("unit").asText());
                rate = rp.value == null ? 0 : rp.value;
            }
            IntegrationResult ir = integrationEngine.integrate(
                    iv.transitionId(), curve, brNow, ruleSet, cal, Math.abs(rate));
            persistArtifact(integrationId(iv.transitionId()), ArtifactKind.TRANSITION_INTEGRATION,
                    iv.transitionId(), ir, ihash, ruleSet.version(), ++seq[0],
                    curI == null ? null : curI.version());
        }
    }

    private List<String> appendSelectedNote(List<String> diags, boolean exists, String selected) {
        List<String> d = new ArrayList<>(diags);
        if (exists) {
            d.add("SELECTED_BASELINE: 已保留人工选择 " + selected);
        }
        return d;
    }

    private NormalizedCurve computeCurve(SegmentMeta meta) {
        JsonNode calN = latestCalNode();
        double off = 0;
        double slope = 1;
        double offUnc = 0.3;
        double slopeUnc = 0.002;
        double massUnc = 0.002;
        if (calN != null) {
            off = calN.path("offsetC").asDouble(0);
            slope = calN.path("slope").asDouble(1);
            offUnc = calN.path("offsetUncertaintyK").asDouble(0.3);
            slopeUnc = calN.path("slopeUncertainty").asDouble(0.002);
            massUnc = calN.path("massRelUncertainty").asDouble(0.002);
        }
        cal.offsetUncertaintyK = offUnc;
        cal.slopeUncertainty = slopeUnc;
        cal.massRelUncertainty = massUnc;
        JsonNode massN = latestSourcePayload("mass-sample-PET-042");
        double massG = currentMassG("mass-sample-PET-042");
        String massVersion = "v" + repo.latestSourceVersion("mass-sample-PET-042");
        String sign = meta.declaredSign();
        if ((sign == null || sign.isBlank())) {
            JsonNode inst = latestSourcePayload("inst-seg-" + meta.segmentId());
            sign = inst == null ? "UNKNOWN" : inst.path("declaredSign").asText("UNKNOWN");
        }
        return new NormalizationEngine().normalize(new NormalizationEngine.Input(
                meta.segmentId(), meta.programDirection(), sign, off, slope,
                massG, massVersion, meta.points(), meta.fileName()));
    }

    private JsonNode latestCalNode() {
        SourceRecord best = null;
        for (SourceRecord s : repo.allSources()) {
            if (s.kind() == SourceKind.TEMPERATURE_CAL
                    && (best == null || s.version() > best.version())) {
                best = s;
            }
        }
        if (best == null) {
            return null;
        }
        try {
            return Json.MAPPER.readTree(best.payloadJson());
        } catch (Exception e) {
            return null;
        }
    }

    private Artifact currentArtifact(String id) {
        Map<String, Integer> ptrs = repo.currentPointers();
        Integer v = ptrs.get(id);
        if (v == null) {
            return null;
        }
        return repo.artifactVersion(id, v);
    }

    private <T> Artifact persistArtifact(String id, ArtifactKind kind, String targetId,
                                         T content, String depsHash, String ruleVer,
                                         long sequence, Integer previousVersion) throws Exception {
        String body = Json.MAPPER.writeValueAsString(content);
        if (previousVersion != null) {
            Artifact prev = repo.artifactVersion(id, previousVersion);
            if (prev != null && prev.contentJson().equals(body)) {
                emit(EngineEvent.DERIVED_STILL_VALID,
                        id + " 的依赖虽有变化，但用 " + ruleVer + " 重算结果与 v"
                                + previousVersion + " 完全一致，当前指针不移动（最小传播）",
                        node -> {
                            node.put("artifactId", id);
                            node.put("version", previousVersion);
                            node.put("depsHash", depsHash);
                            node.put("reason", "recomputed-identical");
                        });
                return prev;
            }
        }
        int newVersion = repo.latestArtifactVersion(id) + 1;
        Artifact a = new Artifact(id, newVersion, kind, targetId, "OK", body,
                depsHash, ruleVer, sequence);
        repo.insertArtifact(a);
        repo.movePointer(id, newVersion);
        if (previousVersion != null) {
            emit(EngineEvent.DERIVED_STALE,
                    id + " 的依赖已变化，旧版本 v" + previousVersion + " 保留，当前指针切换至 v" + newVersion,
                    node -> {
                        node.put("artifactId", id);
                        node.put("oldVersion", previousVersion);
                        node.put("newVersion", newVersion);
                        node.put("depsHash", depsHash);
                    });
        }
        return a;
    }

    private void emitBaselineEvents(IntervalDef iv, BaselineResult br, Artifact previous) {
        boolean empty = br.diagnostics().stream().anyMatch(d -> d.startsWith("EMPTY_INTERVAL"));
        if (empty) {
            emit(EngineEvent.EMPTY_INTERVAL_REJECTED,
                    iv.segmentId() + " 上拖动区间 " + iv.intervalId() + " 落在空区间，基线/积分被拒绝",
                    n -> {
                        n.put("intervalId", iv.intervalId());
                        n.put("segmentId", iv.segmentId());
                        n.put("t0K", iv.t0K());
                        n.put("t1K", iv.t1K());
                    });
        }
        if (br.candidates().size() > 1) {
            ArrayNode ids = Json.MAPPER.createArrayNode();
            br.candidates().forEach(c -> ids.add(c.candidateId()));
            emit(EngineEvent.MULTIPLE_BASELINES_RETAINED,
                    iv.intervalId() + " 存在 " + br.candidates().size()
                            + " 个并列合理基线，差异全部保留，不吞成单一答案",
                    n -> {
                        n.put("intervalId", iv.intervalId());
                        n.set("candidateIds", ids);
                        n.putNull("selectedCandidateId");
                    });
        }
        boolean piecewise = br.candidates().stream()
                .anyMatch(c -> "PIECEWISE_OVERLAP".equals(c.method()));
        if (piecewise) {
            emit(EngineEvent.OVERLAP_SPLIT_DETECTED,
                    iv.intervalId() + " 检测到重叠转折，按规则 " + ruleSet.version()
                            + " 分离阈值 " + ruleSet.overlapSeparationK() + "K 生成分段基线",
                    n -> {
                        n.put("intervalId", iv.intervalId());
                        n.put("ruleVersion", ruleSet.version());
                        n.put("separationK", ruleSet.overlapSeparationK());
                    });
        }
    }

    private void emitSignAndSegmentEvents(String segId) {
        Artifact a = currentArtifact(curveId(segId));
        if (a == null) {
            return;
        }
        try {
            NormalizedCurve curve = Json.MAPPER.readValue(a.contentJson(), NormalizedCurve.class);
            SegmentMeta meta = segments.get(segId);
            emit(EngineEvent.SIGN_CONVENTION_RESOLVED,
                    segId + " 吸放热方向依据仪器表头 " + curve.signBasis()
                            + " 解析（文件名为 " + meta.fileName() + "，不参与判断）；规范化基准 ENDO_UP",
                    n -> {
                        n.put("segmentId", segId);
                        n.put("declaredSign", curve.signBasis());
                        n.put("canonical", "ENDO_UP");
                        n.put("status", curve.signStatus());
                        n.put("fileName", meta.fileName());
                    });
            emit(EngineEvent.SEGMENT_SEPARATION_ENFORCED,
                    segId + " 属于循环 " + meta.cycle() + " 的 " + curve.direction()
                            + " 段；积分只允许在本段内进行，禁止与加热/冷却其它段混用",
                    n -> {
                        n.put("segmentId", segId);
                        n.put("cycle", meta.cycle());
                        n.put("direction", curve.direction());
                    });
            curve.diagnostics().stream()
                    .filter(d -> d.startsWith("非等间隔"))
                    .findFirst()
                    .ifPresent(d -> emit(EngineEvent.NON_UNIFORM_GRID_DETECTED,
                            segId + ": " + d,
                            n -> {
                                n.put("segmentId", segId);
                                n.put("integration", "逐点实际 ΔT 梯形积分");
                            }));
            long kept = meta.points().stream().filter(RawPoint::excluded).count();
            if (kept > 0) {
                emit(EngineEvent.UNCONVERTIBLE_POINT_KEPT,
                        segId + " 有 " + kept + " 个无法换算的点保留在原位置（带诊断），未为画图删除",
                        n -> {
                            n.put("segmentId", segId);
                            n.put("keptExcluded", kept);
                        });
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // =====================================================================
    // 对外操作：参数修订 / 质量修订 / 局部源改动 / 冲突裁定 / 区间拖动
    // =====================================================================

    /** beat 2：规则参数修订 v1 -> v2。 */
    public void reviseRuleParameters(String author) throws Exception {
        repo.beginTx();
        try {
            long rev = repo.currentRevision();
            RuleSet old = ruleSet;
            ruleSet = RuleSet.v2();
            repo.setCaseMeta("rule_version", ruleSet.version());
            long seq = repo.nextSequence("events");
            recompute(new ArrayList<>(segments.keySet()), null, seq);
            emit(EngineEvent.RULE_REVISION,
                    "参数修订：规则 " + old.version() + " -> " + ruleSet.version()
                            + "，重叠分离阈值 " + old.overlapSeparationK() + "K -> "
                            + ruleSet.overlapSeparationK() + "K；依赖图只重建受影响产物",
                    n -> {
                        n.put("author", author);
                        n.put("oldRule", old.version());
                        n.put("newRule", ruleSet.version());
                        n.put("oldSeparationK", old.overlapSeparationK());
                        n.put("newSeparationK", ruleSet.overlapSeparationK());
                    });
            repo.bumpRevision(rev);
            repo.commitTx();
        } catch (RuntimeException e) {
            repo.rollbackTx();
            throw e;
        }
    }

    /** beat 3：质量修订。所有段的归一化及下游全部过期。 */
    public void reviseMass(String newValue, String unit, String originalRep, String author) throws Exception {
        repo.beginTx();
        try {
            long rev = repo.currentRevision();
            String id = "mass-sample-PET-042";
            int v = repo.latestSourceVersion(id) + 1;
            ObjectNode o = Json.MAPPER.createObjectNode();
            o.put("value", newValue);
            o.put("unit", unit);
            long seq = repo.nextSequence("sources");
            repo.insertSource(new SourceRecord(id, SourceKind.SAMPLE_MASS, "sample-PET-042", v,
                    originalRep, o.toString(), seq));
            recompute(new ArrayList<>(segments.keySet()), null,
                    repo.nextSequence("artifacts"));
            emit(EngineEvent.MASS_REVISION,
                    "质量修订为 " + newValue + " " + unit + "（新版本 v" + v
                            + "）：三段归一化及全部归一化派生（基线/积分）过期重算；旧版本保留",
                    n -> {
                        n.put("sourceId", id);
                        n.put("version", v);
                        n.put("newValue", newValue);
                        n.put("unit", unit);
                        n.put("author", author);
                        n.set("affectedTargets", affectedAllTargets());
                    });
            repo.bumpRevision(rev);
            repo.commitTx();
        } catch (RuntimeException e) {
            repo.rollbackTx();
            throw e;
        }
    }

    /** beat 4：只影响局部依赖的源数据改动（seg2 气氛流量）。 */
    public void changeAtmosphere(String segmentId, String newFlowRaw, String author) throws Exception {
        repo.beginTx();
        try {
            long rev = repo.currentRevision();
            SegmentMeta meta = segments.get(segmentId);
            String id = "atm-seg-" + segmentId;
            int v = repo.latestSourceVersion(id) + 1;
            ObjectNode o = Json.MAPPER.createObjectNode();
            o.put("gas", meta.gas());
            o.put("flowRaw", newFlowRaw);
            o.put("flowUnit", meta.flowUnit());
            long seq = repo.nextSequence("sources");
            repo.insertSource(new SourceRecord(id, SourceKind.ATMOSPHERE, segmentId, v,
                    "气氛记录（修订）: " + meta.gas() + " " + newFlowRaw + " " + meta.flowUnit(),
                    o.toString(), seq));
            segments.put(segmentId, new SegmentMeta(meta.segmentId(), meta.cycle(),
                    meta.programDirection(), meta.declaredSign(), meta.fileName(), meta.header(),
                    meta.tUnit(), meta.hfUnit(), meta.rateValue(), meta.rateUnit(),
                    meta.gas(), newFlowRaw, meta.flowUnit(), meta.points()));
            recompute(List.of(segmentId), null, repo.nextSequence("artifacts"));
            ArrayNode others = Json.MAPPER.createArrayNode();
            for (String s : segments.keySet()) {
                if (!s.equals(segmentId)) {
                    others.add(s);
                }
            }
            emit(EngineEvent.LOCAL_SOURCE_CHANGE,
                    "局部源数据改动：仅 " + segmentId + " 气氛流量 -> " + newFlowRaw
                            + "，重算影响限制在该段；其它段仍有效",
                    n -> {
                        n.put("segmentId", segmentId);
                        n.put("sourceId", id);
                        n.put("version", v);
                        n.put("author", author);
                        n.set("unaffectedSegments", others);
                    });
            repo.bumpRevision(rev);
            repo.commitTx();
        } catch (RuntimeException e) {
            repo.rollbackTx();
            throw e;
        }
    }

    private ArrayNode affectedAllTargets() {
        ArrayNode arr = Json.MAPPER.createArrayNode();
        for (String s : segments.keySet()) {
            arr.add(curveId(s));
        }
        for (IntervalDef iv : intervals) {
            arr.add(baselineId(iv.intervalId()));
            arr.add(integrationId(iv.transitionId()));
        }
        return arr;
    }

    /** 曲线页拖动区间。跨段区间（加热/冷却混用）被拒绝；空区间记录 INVALID。 */
    public void adjustInterval(String intervalId, String segmentId, double t0K, double t1K,
                               String author, long baseRevision) throws Exception {
        repo.beginTx();
        try {
            long rev = repo.currentRevision();
            if (baseRevision >= 0 && rev != baseRevision) {
                throw new ConflictException(baseRevision, rev);
            }
            IntervalDef existing = intervals.stream()
                    .filter(i -> i.intervalId().equals(intervalId)).findFirst().orElse(null);
            if (existing != null && !existing.segmentId().equals(segmentId)) {
                SegmentMeta a = segments.get(existing.segmentId());
                SegmentMeta b = segments.get(segmentId);
                throw new IllegalArgumentException("拒绝跨段区间：" + existing.intervalId()
                        + " 原属 " + a.programDirection() + " 段 " + existing.segmentId()
                        + "，不能移到 " + b.programDirection() + " 段 " + segmentId
                        + "（加热与冷却段不混用）");
            }
            String hint = existing == null ? "" : existing.hint();
            String transitionId = existing == null
                    ? "tr-" + intervalId.replace("interval-", "") : existing.transitionId();
            IntervalDef def = new IntervalDef(intervalId, segmentId, transitionId, t0K, t1K, hint);
            if (existing == null) {
                intervals.add(def);
            } else {
                intervals.set(intervals.indexOf(existing), def);
            }
            long seq = repo.nextSequence("sources");
            repo.upsertInterval(intervalId, segmentId, transitionId, t0K, t1K, seq);
            recompute(List.of(segmentId), List.of(intervalId), repo.nextSequence("artifacts"));
            emit(EngineEvent.INTERVAL_ADJUSTED,
                    "区间 " + intervalId + " 拖动为 [" + String.format("%.2f", t0K) + ","
                            + String.format("%.2f", t1K) + "]K，基线与积分重算",
                    n -> {
                        n.put("intervalId", intervalId);
                        n.put("segmentId", segmentId);
                        n.put("t0K", t0K);
                        n.put("t1K", t1K);
                        n.put("author", author);
                    });
            repo.bumpRevision(rev);
            repo.commitTx();
        } catch (RuntimeException e) {
            repo.rollbackTx();
            throw e;
        }
    }

    /**
     * 人工接受/否定基线候选。乐观锁：两位使用者基于同一修订并发提交 -> ConflictException。
     * 未选候选保留，仅在 baseline 内容里标记 selectedCandidateId。
     */
    public void decideBaseline(String intervalId, String candidateId, String action,
                               String author, long baseRevision) {
        repo.beginTx();
        try {
            long rev = repo.currentRevision();
            if (baseRevision >= 0 && rev != baseRevision) {
                throw new ConflictException(baseRevision, rev);
            }
            Artifact bArt = currentArtifact(baselineId(intervalId));
            BaselineResult br = Json.MAPPER.readValue(bArt.contentJson(), BaselineResult.class);
            boolean exists = br.candidates().stream().anyMatch(c -> c.candidateId().equals(candidateId));
            if (!exists) {
                throw new IllegalArgumentException("候选 " + candidateId + " 不属于区间 " + intervalId);
            }
            String selected = "REJECT".equals(action) ? null : candidateId;
            BaselineResult updated = new BaselineResult(br.intervalId(), br.segmentId(),
                    br.t0K(), br.t1K(), br.candidates(), selected,
                    appendDecisionNote(br.diagnostics(), action, candidateId, author),
                    br.ruleVersion());
            IntervalDef iv = intervals.stream().filter(i -> i.intervalId().equals(intervalId))
                    .findFirst().orElseThrow();
            String hash = baselineDepsHash(iv, curveDepsHash(iv.segmentId()));
            long seq = repo.nextSequence("artifacts");
            int newVersion = repo.latestArtifactVersion(baselineId(intervalId)) + 1;
            Artifact a = new Artifact(baselineId(intervalId), newVersion, ArtifactKind.BASELINE_SET,
                    intervalId, "OK", Json.MAPPER.writeValueAsString(updated),
                    hash, ruleSet.version(), seq);
            repo.insertArtifact(a);
            repo.movePointer(baselineId(intervalId), newVersion);
            recompute(List.of(iv.segmentId()), List.of(intervalId), seq + 1);

            long dseq = repo.nextSequence("decisions");
            ObjectNode pay = Json.MAPPER.createObjectNode();
            pay.put("intervalId", intervalId);
            pay.put("candidateId", candidateId);
            pay.put("action", action);
            pay.put("baseRevision", rev);
            Decision d = new Decision("dec-" + dseq, dseq, author, action, candidateId,
                    pay.toString(), rev, null);
            repo.insertDecision(d, "ACTIVE");

            emit("ACCEPT".equals(action) ? EngineEvent.BASELINE_ACCEPTED : EngineEvent.BASELINE_REJECTED,
                    author + " " + ("ACCEPT".equals(action) ? "接受" : "否定")
                            + " 基线候选 " + candidateId + "；其余候选保留不删除",
                    n -> {
                        n.put("intervalId", intervalId);
                        n.put("candidateId", candidateId);
                        n.put("action", action);
                        n.put("author", author);
                        n.put("decisionId", d.id());
                    });
            repo.bumpRevision(rev);
            repo.commitTx();
        } catch (ConflictException e) {
            repo.rollbackTx();
            throw e;
        } catch (Exception e) {
            repo.rollbackTx();
            throw new IllegalStateException(e);
        }
    }

    private List<String> appendDecisionNote(List<String> diags, String action,
                                            String candidateId, String author) {
        List<String> d = new ArrayList<>(diags);
        d.add("DECISION " + action + ": " + candidateId + " by " + author);
        return d;
    }

    /** beat 5：制造两位审阅者在同一版本的互相冲突决定，并产生解决事件（保留双方）。 */
    public Map<String, Object> conflictingDecisions(String intervalId,
                                                    String candidateA, String candidateB) throws Exception {
        long base = repo.currentRevision();
        decideBaseline(intervalId, candidateA, "ACCEPT", "reviewer-li", base);
        Map<String, Object> conflictInfo = new LinkedHashMap<>();
        conflictInfo.put("baseRevision", base);
        conflictInfo.put("serverRevision", repo.currentRevision());
        try {
            decideBaseline(intervalId, candidateB, "ACCEPT", "reviewer-wang", base);
        } catch (ConflictException ce) {
            repo.beginTx();
            try {
                long rev = repo.currentRevision();
                long dseq = repo.nextSequence("decisions");
                ObjectNode pay = Json.MAPPER.createObjectNode();
                pay.put("intervalId", intervalId);
                pay.put("candidateId", candidateB);
                pay.put("action", "ACCEPT");
                pay.put("baseRevision", base);
                Decision loser = new Decision("dec-" + dseq, dseq, "reviewer-wang", "ACCEPT",
                        candidateB, pay.toString(), base, null);
                repo.insertDecision(loser, "CONFLICTED");
                long resolveSeq = repo.nextSequence("events");
                ArrayNode both = Json.MAPPER.createArrayNode();
                both.add(candidateA);
                both.add(candidateB);
                emit(EngineEvent.CONFLICTING_DECISION,
                        "reviewer-li 与 reviewer-wang 从修订 " + base
                                + " 分别接受不同基线；冲突未被静默覆盖，两个决定都已存档",
                        n -> {
                            n.put("intervalId", intervalId);
                            n.set("candidates", both);
                            n.put("baseRevision", base);
                        });
                long resolvedSeq = repo.nextSequence("events");
                repo.supersedeDecision(loser.id(), resolvedSeq);
                emit(EngineEvent.CONFLICT_RESOLVED,
                        "冲突解决：保留 reviewer-li 的当前选择，reviewer-wang 的候选标记为 CONFLICTED 但不删除，可随时比较后改选",
                        n -> {
                            n.put("intervalId", intervalId);
                            n.put("kept", candidateA);
                            n.put("retained", candidateB);
                            n.put("supersededDecision", loser.id());
                        });
                repo.bumpRevision(rev);
                repo.commitTx();
            } catch (Exception ex) {
                repo.rollbackTx();
                throw ex;
            }
        }
        return conflictInfo;
    }

    // =====================================================================
    // 事件 / 历史视图 / 快照
    // =====================================================================

    private interface Pay {
        void fill(ObjectNode node) throws Exception;
    }

    private void emit(String type, String summary, Pay pay) {
        long seq = repo.nextSequence("events");
        ObjectNode p = Json.MAPPER.createObjectNode();
        p.put("eventSeq", seq);
        try {
            pay.fill(p);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        DomainEvent e = new DomainEvent("evt-" + seq, seq, type, summary,
                p.toString(), dependencySnapshot().toString());
        repo.insertEvent(e);
    }

    private ObjectNode dependencySnapshot() {
        ObjectNode snap = Json.MAPPER.createObjectNode();
        snap.put("ruleVersion", ruleSet.version());
        snap.put("docRevision", repo.currentRevision() + 1);
        ObjectNode src = snap.putObject("sources");
        for (SourceRecord s : repo.allSources()) {
            src.put(s.id(), s.version());
        }
        ObjectNode ptr = snap.putObject("artifacts");
        repo.currentPointers().forEach(ptr::put);
        return snap;
    }

    /**
     * 回到任意旧事件：返回当时依赖快照所指向的全部产物版本内容。
     * 不修改任何当前指针——历史视图是只读重建，绝不用最新结果覆盖。
     */
    public Map<String, Object> asOfEvent(long eventSequence) throws Exception {
        DomainEvent target = null;
        for (DomainEvent e : repo.allEvents()) {
            if (e.sequence() <= eventSequence) {
                target = e;
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("没有序号 <= " + eventSequence + " 的事件");
        }
        JsonNode snap = Json.MAPPER.readTree(target.depSnapshotJson());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("event", Map.of("id", target.id(), "sequence", target.sequence(),
                "type", target.type(), "summary", target.summary()));
        view.put("ruleVersion", snap.path("ruleVersion").asText());
        List<Map<String, Object>> arts = new ArrayList<>();
        JsonNode ptrs = snap.get("artifacts");
        ptrs.fieldNames().forEachRemaining(id -> {
            int v = ptrs.get(id).asInt();
            Artifact a = repo.artifactVersion(id, v);
            if (a != null) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", a.id());
                m.put("version", a.version());
                m.put("ruleVersion", a.ruleVersion());
                m.put("depsHash", a.depsHash());
                try {
                    m.put("content", Json.MAPPER.readValue(a.contentJson(), Object.class));
                } catch (Exception ignored) {
                    m.put("content", a.contentJson());
                }
                arts.add(m);
            }
        });
        view.put("artifacts", arts);
        return view;
    }

    // =====================================================================
    // 状态视图
    // =====================================================================

    public Map<String, Object> currentState() throws Exception {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("caseId", repo.getCaseMeta("case_id"));
        state.put("title", repo.getCaseMeta("case_title"));
        state.put("ruleVersion", ruleSet.version());
        state.put("docRevision", repo.currentRevision());

        List<Map<String, Object>> segList = new ArrayList<>();
        for (String segId : segments.keySet()) {
            SegmentMeta m = segments.get(segId);
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("segmentId", segId);
            sm.put("cycle", m.cycle());
            sm.put("direction", m.programDirection());
            sm.put("declaredSign", m.declaredSign());
            sm.put("fileName", m.fileName());
            sm.put("header", m.header());
            sm.put("rate", Map.of("value", m.rateValue(), "unit", m.rateUnit()));
            sm.put("atmosphere", Map.of("gas", m.gas(), "flowRaw", m.flowRaw(),
                    "flowUnit", m.flowUnit()));
            sm.put("rawPointCount", m.points().size());
            sm.put("excludedCount", m.points().stream().filter(RawPoint::excluded).count());
            Artifact ca = currentArtifact(curveId(segId));
            if (ca != null) {
                sm.put("curveVersion", ca.version());
                sm.put("curve", Json.MAPPER.readValue(ca.contentJson(), Object.class));
            }
            segList.add(sm);
        }
        state.put("segments", segList);
        state.put("intervals", repo.allIntervals());
        state.put("sources", repo.allSources().stream().map(s -> Map.of(
                "id", s.id(), "version", s.version(), "kind", s.kind().name(),
                "refId", s.refId(), "originalRep", s.originalRep(),
                "payload", parseSafe(s.payloadJson()))).toList());

        List<Map<String, Object>> artList = new ArrayList<>();
        Map<String, Integer> ptrs = repo.currentPointers();
        for (Map.Entry<String, Integer> e : ptrs.entrySet()) {
            Artifact a = repo.artifactVersion(e.getKey(), e.getValue());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id());
            m.put("version", a.version());
            m.put("kind", a.kind().name());
            m.put("status", a.status());
            m.put("ruleVersion", a.ruleVersion());
            m.put("depsHash", a.depsHash());
            m.put("content", Json.MAPPER.readValue(a.contentJson(), Object.class));
            artList.add(m);
        }
        state.put("artifacts", artList);
        state.put("events", repo.allEvents().stream().map(e -> Map.of(
                "id", e.id(), "sequence", e.sequence(), "type", e.type(),
                "summary", e.summary(), "payload", parseSafe(e.payloadJson()),
                "depSnapshot", parseSafe(e.depSnapshotJson()))).toList());
        state.put("decisions", repo.allDecisions());
        return state;
    }

    private Object parseSafe(String json) {
        try {
            return Json.MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    public List<IntervalDef> intervalDefs() {
        return List.copyOf(intervals);
    }

    public RuleSet rules() {
        return ruleSet;
    }

    public long revision() {
        return repo.currentRevision();
    }

    private Pay importPayload(CasePack pack) {
        return n -> {
            n.put("caseId", pack.caseId());
            n.put("schemaVersion", pack.schemaVersion());
            ArrayNode seg = n.putArray("segments");
            for (CasePack.SegmentDef s : pack.segments()) {
                ObjectNode o = seg.addObject();
                o.put("segmentId", s.segmentId());
                o.put("file", s.file());
                o.put("cycle", s.cycle());
                o.put("programDirection", s.programDirection());
                o.put("declaredSign", s.declaredSign());
            }
            ArrayNode kinds = n.putArray("sourceKinds");
            for (SourceKind k : SourceKind.values()) {
                kinds.add(k.name());
            }
        };
    }

    // =====================================================================
    // 可重复重放：按 manifest 的 beats 逐条执行，实际经过七种边界情形
    // =====================================================================

    public void replayAllBeats() throws Exception {
        reviseRuleParameters("replay");
        reviseMass("8.37", "mg", "复核称量单 #B-042R：8.37 mg", "replay");
        changeAtmosphere("seg2", "45", "replay");

        IntervalDef melt = intervals.stream().filter(i -> i.intervalId().equals("interval-melt"))
                .findFirst().orElseThrow();
        Artifact bArt = currentArtifact(baselineId("interval-melt"));
        BaselineResult br = Json.MAPPER.readValue(bArt.contentJson(), BaselineResult.class);
        String tangent = br.candidates().stream()
                .filter(c -> "TANGENT_LINE".equals(c.method()) && "AUTO".equals(c.origin()))
                .map(BaselineCandidate::candidateId).findFirst().orElse(br.candidates().get(0).candidateId());
        String sigmoid = br.candidates().stream()
                .filter(c -> "SIGMOID_STEP".equals(c.method()))
                .map(BaselineCandidate::candidateId).findFirst().orElse(br.candidates().get(0).candidateId());
        conflictingDecisions("interval-melt", tangent, sigmoid);

        // 空区间：把一个临时区间拖到无点区域（120-121K 远低于最低采样温度）
        adjustInterval("interval-empty-probe", "seg1", 120.0, 121.0, "replay", -1);

        emit(EngineEvent.REPLAY_COMPLETED,
                "可重复重放完成：经过反向热流/非等间隔温度/重叠转折/循环段切换/"
                        + "质量版本变更/并列基线/空区间全部情形，可从事件反查材料、规则版本与决定",
                n -> {
                    n.put("edgeCases", 7);
                    n.put("finalRule", ruleSet.version());
                    n.put("finalRevision", repo.currentRevision());
                });
    }

    // =====================================================================
    // 导出 / 删除后再导入：身份、顺序、来源关系保持
    // =====================================================================

    public String exportSnapshot() throws Exception {
        ObjectNode root = Json.MAPPER.createObjectNode();
        root.put("format", "dsc-transition-review-snapshot");
        root.put("formatVersion", 1);
        root.put("caseId", repo.getCaseMeta("case_id"));
        root.put("title", repo.getCaseMeta("case_title"));
        root.put("ruleVersion", ruleSet.version());

        ArrayNode segs = root.putArray("segments");
        for (SegmentMeta m : segments.values()) {
            ObjectNode o = segs.addObject();
            o.put("segmentId", m.segmentId());
            o.put("cycle", m.cycle());
            o.put("programDirection", m.programDirection());
            o.put("declaredSign", m.declaredSign());
            o.put("fileName", m.fileName());
            o.put("header", m.header());
            o.put("tUnit", m.tUnit());
            o.put("hfUnit", m.hfUnit());
            o.put("rateValue", m.rateValue());
            o.put("rateUnit", m.rateUnit());
            o.put("gas", m.gas());
            o.put("flowRaw", m.flowRaw());
            o.put("flowUnit", m.flowUnit());
            ArrayNode pts = o.putArray("points");
            for (RawPoint p : m.points()) {
                ObjectNode po = pts.addObject();
                po.put("seq", p.seq());
                po.put("tRaw", p.tRaw());
                po.put("hfRaw", p.hfRaw());
                if (p.tK() != null) {
                    po.put("tK", p.tK());
                }
                if (p.hfW() != null) {
                    po.put("hfW", p.hfW());
                }
                po.put("excluded", p.excluded());
                if (p.diag() != null) {
                    po.put("diag", p.diag());
                }
            }
        }
        ArrayNode ivs = root.putArray("intervals");
        for (IntervalDef iv : intervals) {
            ObjectNode o = ivs.addObject();
            o.put("intervalId", iv.intervalId());
            o.put("segmentId", iv.segmentId());
            o.put("transitionId", iv.transitionId());
            o.put("t0K", iv.t0K());
            o.put("t1K", iv.t1K());
            o.put("hint", iv.hint());
        }
        ArrayNode srcs = root.putArray("sources");
        for (SourceRecord s : repo.allSources()) {
            ObjectNode o = srcs.addObject();
            o.put("id", s.id());
            o.put("version", s.version());
            o.put("kind", s.kind().name());
            o.put("refId", s.refId());
            o.put("originalRep", s.originalRep());
            o.put("payload", Json.MAPPER.readTree(s.payloadJson()));
            o.put("sequence", s.sequence());
        }
        ArrayNode arts = root.putArray("artifacts");
        for (Artifact a : repo.allArtifacts()) {
            ObjectNode o = arts.addObject();
            o.put("id", a.id());
            o.put("version", a.version());
            o.put("kind", a.kind().name());
            o.put("targetId", a.targetId());
            o.put("status", a.status());
            o.put("content", Json.MAPPER.readTree(a.contentJson()));
            o.put("depsHash", a.depsHash());
            o.put("ruleVersion", a.ruleVersion());
            o.put("sequence", a.sequence());
        }
        ObjectNode ptrs = root.putObject("pointers");
        repo.currentPointers().forEach(ptrs::put);
        ArrayNode evs = root.putArray("events");
        for (var e : repo.allEvents()) {
            ObjectNode o = evs.addObject();
            o.put("id", e.id());
            o.put("sequence", e.sequence());
            o.put("type", e.type());
            o.put("summary", e.summary());
            o.put("payload", Json.MAPPER.readTree(e.payloadJson()));
            o.put("depSnapshot", Json.MAPPER.readTree(e.depSnapshotJson()));
        }
        ArrayNode decs = root.putArray("decisions");
        for (var d : repo.allDecisions()) {
            decs.addObject().putAll((ObjectNode) Json.MAPPER.valueToTree(d));
        }
        root.put("docRevision", repo.currentRevision());
        return Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    /** 从快照重建（身份/顺序/来源关系完全保持，不触发重算）。 */
    public void importSnapshot(String json) throws Exception {
        JsonNode root = Json.MAPPER.readTree(json);
        repo.beginTx();
        try {
            segments.clear();
            intervals.clear();
            for (JsonNode s : root.get("segments")) {
                List<RawPoint> pts = new ArrayList<>();
                for (JsonNode p : s.get("points")) {
                    pts.add(new RawPoint(p.get("seq").asLong(), p.get("tRaw").asText(),
                            p.get("hfRaw").asText(),
                            p.has("tK") && !p.get("tK").isNull() ? p.get("tK").asDouble() : null,
                            p.has("hfW") && !p.get("hfW").isNull() ? p.get("hfW").asDouble() : null,
                            p.get("excluded").asBoolean(),
                            p.has("diag") && !p.get("diag").isNull() ? p.get("diag").asText() : null));
                }
                SegmentMeta m = new SegmentMeta(s.get("segmentId").asText(),
                        s.get("cycle").asInt(), s.get("programDirection").asText(),
                        s.get("declaredSign").asText(), s.get("fileName").asText(),
                        s.get("header").asText(), s.get("tUnit").asText(), s.get("hfUnit").asText(),
                        s.get("rateValue").asText(), s.get("rateUnit").asText(),
                        s.get("gas").asText(), s.get("flowRaw").asText(),
                        s.get("flowUnit").asText(), pts);
                segments.put(m.segmentId(), m);
            }
            for (JsonNode iv : root.get("intervals")) {
                intervals.add(new IntervalDef(iv.get("intervalId").asText(),
                        iv.get("segmentId").asText(), iv.get("transitionId").asText(),
                        iv.get("t0K").asDouble(), iv.get("t1K").asDouble(),
                        iv.path("hint").asText("")));
            }
            for (JsonNode s : root.get("sources")) {
                repo.insertSource(new SourceRecord(s.get("id").asText(),
                        SourceKind.valueOf(s.get("kind").asText()), s.get("refId").asText(),
                        s.get("version").asInt(), s.get("originalRep").asText(),
                        Json.MAPPER.writeValueAsString(s.get("payload")),
                        s.get("sequence").asLong()));
            }
            for (JsonNode a : root.get("artifacts")) {
                repo.insertArtifact(new Artifact(a.get("id").asText(), a.get("version").asInt(),
                        ArtifactKind.valueOf(a.get("kind").asText()), a.get("targetId").asText(),
                        a.get("status").asText(),
                        Json.MAPPER.writeValueAsString(a.get("content")),
                        a.get("depsHash").asText(), a.get("ruleVersion").asText(),
                        a.get("sequence").asLong()));
            }
            JsonNode ptrs = root.get("pointers");
            ptrs.fieldNames().forEachRemaining(id ->
                    repo.movePointer(id, ptrs.get(id).asInt()));
            for (JsonNode e : root.get("events")) {
                repo.insertEvent(new DomainEvent(e.get("id").asText(), e.get("sequence").asLong(),
                        e.get("type").asText(), e.get("summary").asText(),
                        Json.MAPPER.writeValueAsString(e.get("payload")),
                        Json.MAPPER.writeValueAsString(e.get("depSnapshot"))));
            }
            for (JsonNode d : root.get("decisions")) {
                Long sup = d.has("supersededBy") && !d.get("supersededBy").isNull()
                        ? d.get("supersededBy").asLong() : null;
                repo.insertDecision(new Decision(d.get("id").asText(), d.get("sequence").asLong(),
                        d.get("author").asText(), d.get("action").asText(),
                        d.get("targetId").asText(), Json.MAPPER.writeValueAsString(d.get("payload")),
                        d.get("baseVersion").asLong(), sup),
                        d.path("conflictStatus").asText("ACTIVE"));
            }
            ruleSet = RuleSet.of(root.get("ruleVersion").asText());
            repo.setCaseMeta("rule_version", ruleSet.version());
            repo.setCaseMeta("case_id", root.path("caseId").asText(""));
            repo.setCaseMeta("case_title", root.path("title").asText(""));
            long rev = root.path("docRevision").asLong(repo.currentRevision());
            while (repo.currentRevision() < rev) {
                repo.bumpRevision(repo.currentRevision());
            }
            repo.commitTx();
        } catch (Exception e) {
            repo.rollbackTx();
            throw e;
        }
    }
}
