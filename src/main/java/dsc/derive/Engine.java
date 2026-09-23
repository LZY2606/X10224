package dsc.derive;

import dsc.domain.Analysis;
import dsc.domain.Curves;
import dsc.domain.InstrumentFile;
import dsc.domain.Units;
import dsc.store.Store;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 依赖图传播重算：源数据变更后只更新受影响的派生节点。
 * 每个派生版本记录其依赖版本身份；有效性 = 记录的依赖版本与当前指针逐一相等。
 * 报告说明每项结果为何仍有效或为何需要重算。
 */
public class Engine {
    public static final String SRC_INST = "source:instrument_output";
    public static final String SRC_MASS = "source:sample_mass";
    public static final String SRC_RATE = "source:scan_rate";
    public static final String SRC_ATM = "source:atmosphere";
    public static final String SRC_CAL = "source:temp_calibration";
    public static final String SRC_TANGENT = "source:user_tangent";
    public static final String DER_CURVE = "derived:normalized_curve";
    public static final String DER_SEGMENTS = "derived:segments";
    public static final String DER_ANALYSIS = "derived:event_analysis";
    public static final String DEC_BASELINE = "decision:baseline";

    private final Store store;

    public Engine(Store store) {
        this.store = store;
    }

    // ---------- 依赖声明 ----------

    private Map<String, String> declaredDepKeys(String category, String key, Map<String, Object> payload) {
        Map<String, String> declared = new LinkedHashMap<>();
        switch (category) {
            case DER_CURVE -> {
                declared.put(SRC_INST + "|" + key, null);
                declared.put(SRC_MASS + "|mass", null);
                declared.put(SRC_CAL + "|cal", null);
                declared.put(SRC_RATE + "|rate", null);
            }
            case DER_SEGMENTS -> declared.put(DER_CURVE + "|" + key, null);
            case DER_ANALYSIS -> {
                String run = payload != null && payload.get("params") instanceof Map<?, ?> p
                        ? String.valueOf(((Map<?, ?>) p).get("run")) : null;
                if (run != null) {
                    declared.put(DER_CURVE + "|" + run, null);
                    declared.put(SRC_TANGENT + "|" + key, null);
                }
            }
            default -> { }
        }
        return declared;
    }

    /** 有效性检查：记录的依赖版本与当前指针逐一比对，返回差异说明。 */
    private List<String> stalenessReasons(String sample, String category, String key,
                                          Store.Version current, Map<String, Object> payloadForDeps) throws SQLException {
        List<String> reasons = new ArrayList<>();
        Map<String, String> declared = declaredDepKeys(category, key, payloadForDeps);
        for (String depKey : declared.keySet()) {
            String recorded = current == null ? null : current.deps().get(depKey);
            String[] parts = depKey.split("\\|", 2);
            String actual = store.current(sample, parts[0], parts[1]).map(Store.Version::id).orElse(null);
            if (recorded == null && actual == null) continue;
            if (recorded != null && recorded.equals(actual)) continue;
            reasons.add(depKey + ": " + (recorded == null ? "无" : recorded) + " -> " + (actual == null ? "缺失" : actual));
        }
        return reasons;
    }

    private Map<String, String> resolveDeps(String sample, String category, String key,
                                            Map<String, Object> payloadForDeps) throws SQLException {
        Map<String, String> deps = new LinkedHashMap<>();
        for (String depKey : declaredDepKeys(category, key, payloadForDeps).keySet()) {
            String[] parts = depKey.split("\\|", 2);
            deps.put(depKey, store.current(sample, parts[0], parts[1]).map(Store.Version::id).orElse(null));
        }
        return deps;
    }

    // ---------- 计算 ----------

    @SuppressWarnings("unchecked")
    private Map<String, Object> computeNormalized(String sample, String run) throws SQLException {
        Store.Version inst = store.current(sample, SRC_INST, run).orElseThrow();
        Map<String, Object> src = inst.payload();
        InstrumentFile raw = InstrumentFile.parse(String.valueOf(src.get("rawText")));

        Double massG = null, massRelUnc = null;
        Optional<Store.Version> mass = store.current(sample, SRC_MASS, "mass");
        if (mass.isPresent()) {
            Map<String, Object> m = mass.get().payload();
            Double f = Units.scale(String.valueOf(m.get("unit")), "g");
            if (f != null) {
                massG = ((Number) m.get("value")).doubleValue() * f;
                if (m.get("relUnc") instanceof Number n) massRelUnc = n.doubleValue();
            }
        }
        Double calA = null, calB = null, tempUnc = null;
        Optional<Store.Version> cal = store.current(sample, SRC_CAL, "cal");
        if (cal.isPresent()) {
            Map<String, Object> c = cal.get().payload();
            if (c.get("a") instanceof Number n) calA = n.doubleValue();
            if (c.get("b") instanceof Number n) calB = n.doubleValue();
            if (c.get("uncertaintyC") instanceof Number n) tempUnc = n.doubleValue();
        }
        Double rateKs = null;
        Optional<Store.Version> rate = store.current(sample, SRC_RATE, "rate");
        if (rate.isPresent()) {
            Map<String, Object> r = rate.get().payload();
            Double f = Units.scale(String.valueOf(r.get("unit")), "K/s");
            if (f != null) rateKs = ((Number) r.get("value")).doubleValue() * f;
        }
        Map<String, Object> out = Curves.normalize(raw, massG, massRelUnc, calA, calB, tempUnc, rateKs);
        out.put("run", run);
        out.put("sourceFile", src.get("fileName"));
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> computeSegments(String sample, String run) throws SQLException {
        Store.Version curve = store.current(sample, DER_CURVE, run).orElseThrow();
        Map<String, Object> p = curve.payload();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("run", run);
        out.put("segments", p.get("segments"));
        out.put("direction", p.get("direction"));
        out.put("diagnostics", p.get("diagnostics"));
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> computeAnalysis(String sample, String win, Map<String, Object> params) throws SQLException {
        String run = String.valueOf(params.get("run"));
        Store.Version curve = store.current(sample, DER_CURVE, run).orElseThrow();
        Map<String, Object> cp = curve.payload();
        List<Double> temps = (List<Double>) cp.get("temperature");
        List<Double> hfs = (List<Double>) cp.get("heatFlow");
        List<Map<String, Object>> segs = (List<Map<String, Object>>) cp.get("segments");
        double[] T = temps.stream().mapToDouble(Double::doubleValue).toArray();
        double[] Y = hfs.stream().mapToDouble(Double::doubleValue).toArray();

        int segIdx = params.get("segment") instanceof Number n ? n.intValue() : 0;
        Map<String, Object> seg = segs.get(segIdx);
        int segStart = ((Number) seg.get("start")).intValue();
        int segEnd = ((Number) seg.get("end")).intValue();

        Double rateKs = null;
        if (cp.get("scanRate") instanceof Map<?, ?> q && ((Map<?, ?>) q).get("value") instanceof Number n) {
            rateKs = n.doubleValue();
        }
        Double tempUnc = cp.get("tempUnc") instanceof Number n ? n.doubleValue() : null;
        Double massRelUnc = cp.get("massRelUnc") instanceof Number n ? n.doubleValue() : null;
        boolean directionKnown = !"UNKNOWN".equals(cp.get("direction"));

        Map<String, Object> tangent = null;
        Optional<Store.Version> tv = store.current(sample, SRC_TANGENT, win);
        if (tv.isPresent()) tangent = tv.get().payload();

        // 冷却段速率取负：积分符号约定与升温段一致（加热与冷却段不混用，但符号规则统一）
        if (rateKs != null && "COOLING".equals(seg.get("direction"))) rateKs = -rateKs;
        double t1 = ((Number) params.get("t1")).doubleValue();
        double t2 = ((Number) params.get("t2")).doubleValue();
        Map<String, Object> out = Analysis.analyze(T, Y, segStart, segEnd, t1, t2, rateKs,
                tangent, tempUnc, massRelUnc, directionKnown);
        out.put("params", params);
        out.put("run", run);
        out.put("segmentDirection", seg.get("direction"));
        out.put("cycle", seg.get("cycle"));
        return out;
    }

    // ---------- 确保有效（依赖图传播） ----------

    public Store.Version ensureNormalized(String sample, String run, List<Map<String, Object>> report) throws SQLException {
        Store.Version cur = store.current(sample, DER_CURVE, run).orElse(null);
        List<String> reasons = stalenessReasons(sample, DER_CURVE, run, cur, null);
        if (cur != null && reasons.isEmpty()) {
            report.add(entry(DER_CURVE, run, "VALID", List.of("依赖版本均未变化"), cur.id()));
            return cur;
        }
        Map<String, Object> payload = computeNormalized(sample, run);
        Map<String, String> deps = resolveDeps(sample, DER_CURVE, run, null);
        Store.Version v = store.putVersion(sample, DER_CURVE, run, deps, payload);
        report.add(entry(DER_CURVE, run, cur == null ? "CREATED" : "RECOMPUTED", reasons, v.id()));
        return v;
    }

    public Store.Version ensureSegments(String sample, String run, List<Map<String, Object>> report) throws SQLException {
        Store.Version cur = store.current(sample, DER_SEGMENTS, run).orElse(null);
        List<String> reasons = stalenessReasons(sample, DER_SEGMENTS, run, cur, null);
        if (cur != null && reasons.isEmpty()) {
            report.add(entry(DER_SEGMENTS, run, "VALID", List.of("依赖版本均未变化"), cur.id()));
            return cur;
        }
        Map<String, Object> payload = computeSegments(sample, run);
        Store.Version v = store.putVersion(sample, DER_SEGMENTS, run, resolveDeps(sample, DER_SEGMENTS, run, null), payload);
        report.add(entry(DER_SEGMENTS, run, cur == null ? "CREATED" : "RECOMPUTED", reasons, v.id()));
        return v;
    }

    @SuppressWarnings("unchecked")
    public Store.Version ensureAnalysis(String sample, String win, Map<String, Object> params,
                                        List<Map<String, Object>> report) throws SQLException {
        Store.Version cur = store.current(sample, DER_ANALYSIS, win).orElse(null);
        Map<String, Object> effectiveParams = params != null ? params
                : cur != null && cur.payload().get("params") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : null;
        if (effectiveParams == null) throw new IllegalStateException("analysis params unknown for " + win);
        List<String> reasons = stalenessReasons(sample, DER_ANALYSIS, win, cur, Map.of("params", effectiveParams));
        if (cur != null && params == null && reasons.isEmpty()) {
            report.add(entry(DER_ANALYSIS, win, "VALID", List.of("依赖版本均未变化"), cur.id()));
            return cur;
        }
        Map<String, Object> payload = computeAnalysis(sample, win, effectiveParams);
        Store.Version v = store.putVersion(sample, DER_ANALYSIS, win,
                resolveDeps(sample, DER_ANALYSIS, win, Map.of("params", effectiveParams)), payload);
        report.add(entry(DER_ANALYSIS, win, cur == null ? "CREATED" : "RECOMPUTED", reasons, v.id()));
        return v;
    }

    /** 全量传播：只重算受影响节点，报告每项结果为何仍有效或为何重算。 */
    public Map<String, Object> deriveAll(String sample) throws SQLException {
        List<Map<String, Object>> report = new ArrayList<>();
        for (String run : store.keys(sample, SRC_INST)) {
            ensureNormalized(sample, run, report);
            ensureSegments(sample, run, report);
        }
        for (String win : store.keys(sample, DER_ANALYSIS)) {
            ensureAnalysis(sample, win, null, report);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("report", report);
        return out;
    }

    /** 解释某项结果为何仍有效或为何需要重算。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> explain(String sample, String category, String key) throws SQLException {
        Store.Version cur = store.current(sample, category, key).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("category", category);
        out.put("key", key);
        if (cur == null) {
            out.put("status", "MISSING");
            return out;
        }
        Map<String, Object> params = cur.payload().get("params") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : null;
        List<String> reasons = stalenessReasons(sample, category, key, cur,
                params != null ? Map.of("params", params) : null);
        out.put("id", cur.id());
        out.put("version", cur.version());
        out.put("status", reasons.isEmpty() ? "VALID" : "STALE");
        out.put("reasons", reasons.isEmpty() ? List.of("依赖版本均未变化，结果仍有效") : reasons);
        return out;
    }

    private Map<String, Object> entry(String category, String key, String action, List<String> reasons, String id) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("category", category);
        e.put("key", key);
        e.put("action", action);
        e.put("reasons", reasons);
        e.put("versionId", id);
        return e;
    }
}
