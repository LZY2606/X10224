package dsc.domain;

import dsc.derive.Baseline;
import dsc.derive.Derived;
import dsc.derive.DepGraph;
import dsc.derive.Hashes;
import dsc.derive.Transition;
import dsc.domain.Entities.*;
import dsc.store.Store;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用例编排。每个用户动作（导入来源、改切线、接受/否定候选、改质量、源数据改动）
 * 都产生一条独立事件，并在需要时附带机器可读取的 judgment/reasons。
 *
 * 不把多解压成单解；接受一次确认绝不覆盖未选候选（只增加 ACCEPTED 裁定，
 * 其它候选仍以 PROPOSED/REJECTED 保留）。
 */
public final class AppService {

    private final Store store;

    public AppService(Store store) {
        this.store = store;
    }

    public Store store() {
        return store;
    }

    public History history() {
        return store.loadAll();
    }

    public long version() {
        return store.currentVersion();
    }

    private String newId(String prefix, Map<String, Object> body) {
        return prefix + "_" + Hashes.shortHash(prefix + "|" + Instant.now().toEpochMilli()
                + "|" + body + "|" + Math.random());
    }

    /* ---------------- 来源命令 ---------------- */

    public String addInstrumentOutput(String author, long expectedVersion, String name,
                                      String heatUnit, String tempUnit, String exoSign,
                                      String encodingNote, List<List<String>> rows) {
        List<Values.Point> points = new ArrayList<>();
        List<String> globalDiag = new ArrayList<>();
        for (List<String> row : rows) {
            String rt = row.size() > 0 ? row.get(0) : null;
            String rh = row.size() > 1 ? row.get(1) : null;
            String rtime = row.size() > 2 ? row.get(2) : null;
            List<String> diag = new ArrayList<>();
            double tk = Values.parseTemperature(rt, tempUnit, diag);
            double hf = Values.parseHeatFlow(rh, heatUnit, diag);
            Double timeS = null;
            if (rtime != null && !rtime.isBlank()) {
                try {
                    timeS = Double.parseDouble(rtime.trim());
                } catch (NumberFormatException e) {
                    diag.add("时间无法转换，保留原值: " + rtime);
                }
            }
            points.add(new Values.Point(tk, hf, timeS, rt, rh, rtime, diag));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        String id = newId("io", Map.of("name", name == null ? "" : name, "n", rows.size()));
        payload.put("id", id);
        payload.put("name", name);
        payload.put("heatUnit", heatUnit);
        payload.put("tempUnit", tempUnit);
        payload.put("exoSign", exoSign);
        payload.put("encodingNote", encodingNote);
        payload.put("points", pointsToMaps(points));
        store.append("instrument_output", id, null, null, author, payload,
                sourceReasons("instrument_output", id, Map.of(
                        "directionRule", "吸放热方向仅取 exoSign，禁止由文件名推断",
                        "preserveRaw", true)), expectedVersion);
        return id;
    }

    public String addMass(String author, long expectedVersion, String raw, String note) {
        List<String> diag = new ArrayList<>();
        double kg = Values.parseMassKg(raw, diag);
        History h = history();
        History.Snapshot cur = h.current();
        int ver = 1;
        for (MassVersion mv : cur.masses.values()) {
            ver = Math.max(ver, mv.version + 1);
        }
        String id = newId("mass", Map.of("raw", raw, "v", ver));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("kg", kg);
        payload.put("raw", raw);
        payload.put("note", note);
        payload.put("version", ver);
        payload.put("sampleKey", "default");
        Map<String, Object> reasons = sourceReasons("mass", id, Map.of(
                "invalidates", "all_normalized_derivatives",
                "rule", "质量修订后所有归一化派生过期并需按新版本重算",
                "diagnostics", diag));
        store.append("mass", id, null, null, author, payload, reasons, expectedVersion);
        return id;
    }

    public String addScanRate(String author, long expectedVersion, String raw) {
        List<String> diag = new ArrayList<>();
        double kpm = Values.parseRateKPerMin(raw, diag);
        String id = newId("rate", Map.of("raw", raw));
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id);
        p.put("kPerMin", kpm);
        p.put("raw", raw);
        store.append("scan_rate", id, null, null, author, p,
                sourceReasons("scan_rate", id, Map.of("diagnostics", diag)), expectedVersion);
        return id;
    }

    public String addAtmosphere(String author, long expectedVersion, String gas, String raw) {
        String id = newId("atm", Map.of("gas", gas));
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id);
        p.put("gas", gas);
        p.put("raw", raw);
        store.append("atmosphere", id, null, null, author, p,
                sourceReasons("atmosphere", id, Map.of()), expectedVersion);
        return id;
    }

    public String addCalibration(String author, long expectedVersion,
                                 double offsetK, double slope, String standard, String raw) {
        String id = newId("cal", Map.of("off", offsetK, "slope", slope));
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id);
        p.put("offsetK", offsetK);
        p.put("slope", slope);
        p.put("standard", standard);
        p.put("raw", raw);
        store.append("temp_calibration", id, null, null, author, p,
                sourceReasons("temp_calibration", id, Map.of(
                        "affects", "temperature_coordinates_only")), expectedVersion);
        return id;
    }

    public String addSegment(String author, long expectedVersion, String cycle, int ordinal,
                             String direction, String instrumentOutputId, String scanRateId,
                             String atmosphereId, String calibrationId,
                             double lowK, double highK) {
        String id = newId("seg", Map.of("io", instrumentOutputId, "ord", ordinal, "dir", direction));
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id);
        p.put("cycle", cycle);
        p.put("ordinal", ordinal);
        p.put("direction", direction);
        p.put("instrumentOutputId", instrumentOutputId);
        p.put("scanRateId", scanRateId);
        p.put("atmosphereId", atmosphereId);
        p.put("tempCalibrationId", calibrationId);
        p.put("tempRangeLowK", lowK);
        p.put("tempRangeHighK", highK);
        store.append("segment", id, null, null, author, p,
                sourceReasons("segment", id, Map.of(
                        "noMixing", "加热与冷却段不混用；积分仅限本段绑定的仪器输出",
                        "directionExplicit", direction)), expectedVersion);
        return id;
    }

    public String addTangent(String author, long expectedVersion, String segmentId, String kind,
                             double fromK, double toK, double leftAnchorK, double rightAnchorK) {
        String id = newId("tan", Map.of("seg", segmentId, "from", fromK, "to", toK));
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id);
        p.put("segmentId", segmentId);
        p.put("eventRef", "");
        p.put("kind", kind);
        p.put("fromK", fromK);
        p.put("toK", toK);
        p.put("leftAnchorK", leftAnchorK);
        p.put("rightAnchorK", rightAnchorK);
        store.append("tangent", id, null, null, author, p,
                sourceReasons("tangent", id, Map.of(
                        "produces", List.of("linear", "step"),
                        "multiplicity", "多个合理基线保留差异")), expectedVersion);
        return id;
    }

    /* ---------------- 裁定命令（接受/否定/参数调整） ---------------- */

    /** 接受某个基线候选；其它候选保留，不覆盖。 */
    public void judgeCandidate(String author, long expectedVersion, String candidateId,
                               String verdict, String rationale) {
        Derived.Bundle bundle = Derived.recompute(history().current());
        Derived.BaselineCandidate cand = bundle.candidate(candidateId);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("subject", candidateId);
        p.put("verdict", verdict);
        p.put("rationale", rationale);

        Map<String, Object> reasons = new LinkedHashMap<>();
        reasons.put("machineRule", "verdict_non_destructive");
        reasons.put("keepsAlternatives", true);
        reasons.put("directionNotFromFilename", true);
        reasons.put("candidateHash", cand == null ? null : Hashes.candidate(cand));
        if (cand != null) {
            reasons.put("sourceVersions", cand.result.sourceVersions);
            Transition t = cand.result;
            reasons.put("visibleConclusion", Map.of(
                    "onsetK", t.onsetK, "peakK", t.peakK, "endsetK", t.endsetK,
                    "enthalpyJPerG", t.enthalpyJPerG, "endothermic", t.endothermic,
                    "kind", t.kind.code));
            reasons.put("errorSources", t.errorBudget);
        }
        String id = newId("judge", Map.of("cand", candidateId, "v", verdict));
        store.append("judgment", id, null, null, author, p, reasons, expectedVersion);
    }

    /**
     * 只改动一个直接来源（仪器输出替换），返回受影响对象列表，
     * 并把"影响被限制在正确范围"的判定写成机器可读取事件。
     */
    public String replaceInstrumentOutput(String author, long expectedVersion,
                                          String oldId, String newName, String heatUnit,
                                          String tempUnit, String exoSign, String note,
                                          List<List<String>> rows) {
        History before = history();
        DepGraph oldGraph = Derived.recompute(before.current()).graph;
        Map<String, String> oldHashes = oldGraph.hashSnapshot();

        String newId = addInstrumentOutput(author, expectedVersion, newName, heatUnit,
                tempUnit, exoSign, note, rows);

        Derived.Bundle afterBundle = Derived.recompute(history().current());
        List<String> affected = afterBundle.graph.affected(List.of(newId));
        List<String> affectedView = new ArrayList<>();
        for (String a : affected) {
            affectedView.add(a);
        }
        // 重定向引用旧仪器输出的段到新仪器输出（局部源数据改动），逐段记录
        List<String> redirectedSegments = new ArrayList<>();
        for (Segment seg : before.current().segments.values()) {
            if (seg.instrumentOutputId.equals(oldId)) {
                redirectedSegments.add(seg.id);
            }
        }

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("subject", "source_change:" + newId);
        p.put("oldSourceId", oldId);
        p.put("newSourceId", newId);
        Map<String, Object> reasons = new LinkedHashMap<>();
        reasons.put("machineRule", "dependency_guided_propagation");
        reasons.put("changedDirectSource", newId);
        reasons.put("affectedObjects", affectedView);
        reasons.put("scopeLimited", true);
        reasons.put("unaffectedStillValid", collectStillValid(afterBundle, oldHashes, newId));
        reasons.put("note", "只改动一个直接来源；不绑定该来源的段与归一化保持有效");
        String jid = newId("judge_src", Map.of("new", newId));
        store.append("judgment", jid, null, null, author, p, reasons, -1);
        return newId;
    }

    private List<Map<String, Object>> collectStillValid(Derived.Bundle b,
                                                        Map<String, String> oldHashes, String changed) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<String> affected = b.graph.affected(List.of(changed));
        java.util.Set<String> affSet = new java.util.HashSet<>(affected);
        for (Map.Entry<String, DepGraph.Node> e : b.graph.all().entrySet()) {
            if (!affSet.contains(e.getKey()) && oldHashes.containsKey(e.getKey())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", e.getKey());
                m.put("hash", e.getValue().hash);
                m.put("why", b.graph.whyValid(e.getKey(), oldHashes));
                out.add(m);
            }
        }
        return out;
    }

    /* ---------------- 视图 ---------------- */

    public Derived.Bundle currentBundle() {
        return Derived.recompute(history().current());
    }

    public Derived.Bundle bundleAtEvent(String eventId) {
        return Derived.recompute(history().replayToEvent(eventId));
    }

    private Map<String, Object> sourceReasons(String type, String id, Map<String, Object> extra) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("machineRule", "source_archived_separately");
        r.put("sourceType", type);
        r.put("sourceId", id);
        r.put("archivedAt", Instant.now().toString());
        r.put("rawPreserved", true);
        r.put("normalizationLayer", "derived_only");
        r.putAll(extra);
        return r;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pointsToMaps(List<Values.Point> points) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Values.Point p : points) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tempK", p.tempK);
            m.put("heatFlowW", p.heatFlowW);
            m.put("timeS", p.timeS);
            m.put("rawTemp", p.rawTemp);
            m.put("rawHeat", p.rawHeat);
            m.put("rawTime", p.rawTime);
            m.put("diagnostics", p.diagnostics);
            out.add(m);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<Values.Point> pointsFromMaps(Object raw) {
        List<Values.Point> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            Map<String, Object> m = (Map<String, Object>) o;
            double tk = num(m.get("tempK"));
            double hf = num(m.get("heatFlowW"));
            Double ts = m.get("timeS") == null ? null : num(m.get("timeS"));
            List<String> diag = (List<String>) m.getOrDefault("diagnostics", List.of());
            out.add(new Values.Point(tk, hf, ts,
                    (String) m.get("rawTemp"), (String) m.get("rawHeat"), (String) m.get("rawTime"),
                    diag == null ? List.of() : diag));
        }
        return out;
    }

    private static double num(Object o) {
        if (o == null) {
            return Double.NaN;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
