package dsc.derive;

import dsc.store.Store;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 应用服务：把数据、派生和裁定连在一起。
 * 每个动作都产生机器可读事件（输入片段、判定状态、受影响对象），
 * 采纳只移动指针，未选候选永远保留。
 */
public class Workspace {
    private final Store store;
    private final Engine engine;

    public Workspace(Store store, Engine engine) {
        this.store = store;
        this.engine = engine;
    }

    public Store store() { return store; }
    public Engine engine() { return engine; }

    // ---------- 来源 ----------

    public Store.Version importSource(String sample, String category, String key,
                                      Map<String, Object> payload) throws SQLException {
        Store.Version v = store.putVersion(sample, category, key, Map.of(), payload);
        store.appendEvent(sample, "SOURCE_IMPORTED", Map.of(
                "category", category, "key", key, "versionId", v.id(),
                "input", payload), null);
        return v;
    }

    /** 源数据修订：写新版本 -> 依赖图传播 -> 事件记录受影响对象。 */
    public Map<String, Object> reviseSource(String sample, String category, String key,
                                            Map<String, Object> payload, String baseVersionId)
            throws SQLException, Store.Conflict {
        Store.Version v = store.saveWithBase(sample, category, key, payload, baseVersionId);
        Map<String, Object> derived = engine.deriveAll(sample);
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("category", category);
        ev.put("key", key);
        ev.put("versionId", v.id());
        ev.put("input", payload);
        ev.put("propagation", derived.get("report"));
        store.appendEvent(sample, "SOURCE_REVISED", ev, null);
        return derived;
    }

    // ---------- 事件窗口与基线候选 ----------

    @SuppressWarnings("unchecked")
    public Map<String, Object> createWindow(String sample, String run, int segment, double t1, double t2)
            throws SQLException {
        String win = "win-" + run + "-" + segment + "-" + (int) t1 + "-" + (int) t2;
        Map<String, Object> params = Map.of("run", run, "segment", segment, "t1", t1, "t2", t2);
        List<Map<String, Object>> report = new ArrayList<>();
        Store.Version analysis = engine.ensureAnalysis(sample, win, params, report);

        // 决策节点：候选从分析复制，全部保留；采纳只是状态标记
        Optional<Store.Version> decCur = store.current(sample, Engine.DEC_BASELINE, win);
        if (decCur.isEmpty()) {
            List<Map<String, Object>> cands = new ArrayList<>();
            for (Map<String, Object> c : (List<Map<String, Object>>) analysis.payload().get("candidates")) {
                Map<String, Object> cc = new LinkedHashMap<>(c);
                cc.put("id", win + ":" + c.get("kind"));
                cc.put("status", "CANDIDATE");
                cands.add(cc);
            }
            Map<String, Object> dec = new LinkedHashMap<>();
            dec.put("windowId", win);
            dec.put("analysisVersionId", analysis.id());
            dec.put("candidates", cands);
            dec.put("adopted", List.of());
            dec.put("resolutions", List.of());
            store.putVersion(sample, Engine.DEC_BASELINE, win,
                    Map.of(Engine.DER_ANALYSIS + "|" + win, analysis.id()), dec);
        }
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("windowId", win);
        ev.put("params", params);
        ev.put("analysisVersionId", analysis.id());
        ev.put("diagnostics", analysis.payload().get("diagnostics"));
        store.appendEvent(sample, "WINDOW_CREATED", ev, null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("windowId", win);
        out.put("analysis", analysis);
        return out;
    }

    /** 采纳/否定候选基线：带基准版本的冲突检查；未选候选保留。 */
    @SuppressWarnings("unchecked")
    public Store.Version decide(String sample, String win, String candidateKind, String action,
                                String user, String baseVersionId) throws SQLException, Store.Conflict {
        Store.Version cur = store.current(sample, Engine.DEC_BASELINE, win).orElseThrow();
        Map<String, Object> payload = deepCopy(cur.payload());
        List<Map<String, Object>> cands = (List<Map<String, Object>>) payload.get("candidates");
        boolean found = false;
        for (Map<String, Object> c : cands) {
            if (candidateKind.equals(c.get("kind"))) {
                c.put("status", "ADOPT".equals(action) ? "ADOPTED" : "REJECTED");
                c.put("decidedBy", user);
                found = true;
            }
        }
        if (!found) throw new IllegalArgumentException("candidate not found: " + candidateKind);
        List<String> adopted = new ArrayList<>((List<String>) payload.get("adopted"));
        if ("ADOPT".equals(action) && !adopted.contains(candidateKind)) adopted.add(candidateKind);
        if ("REJECT".equals(action)) adopted.remove(candidateKind);
        payload.put("adopted", adopted);
        Store.Version v = store.saveWithBase(sample, Engine.DEC_BASELINE, win, payload, baseVersionId);
        store.appendEvent(sample, "DECISION_" + action, Map.of(
                "windowId", win, "candidate", candidateKind, "user", user,
                "status", "ADOPT".equals(action) ? "ADOPTED" : "REJECTED",
                "decisionVersionId", v.id()), null);
        return v;
    }

    /** 冲突解决：保留双方候选差异，不强制单一答案。 */
    @SuppressWarnings("unchecked")
    public Store.Version resolveKeepBoth(String sample, String win, Store.Version conflictCurrent,
                                         Map<String, Object> myPayload, String userA, String userB)
            throws SQLException {
        Map<String, Object> theirs = conflictCurrent.payload();
        Map<String, Object> merged = deepCopy(theirs);
        List<Map<String, Object>> mergedCands = (List<Map<String, Object>>) merged.get("candidates");
        // 把我方版本中的候选状态合并进来（按 kind 对齐），双方都保留
        for (Map<String, Object> mine : (List<Map<String, Object>>) myPayload.get("candidates")) {
            for (Map<String, Object> c : mergedCands) {
                if (c.get("kind").equals(mine.get("kind")) && !c.get("status").equals(mine.get("status"))) {
                    c.put("status", "CONTESTED");
                    c.put("contestedBy", List.of(userA, userB));
                }
            }
        }
        List<String> adopted = new ArrayList<>((List<String>) merged.get("adopted"));
        for (String k : (List<String>) myPayload.get("adopted")) {
            if (!adopted.contains(k)) adopted.add(k);
        }
        merged.put("adopted", adopted);
        List<Map<String, Object>> resolutions = new ArrayList<>((List<Map<String, Object>>) merged.get("resolutions"));
        resolutions.add(Map.of("strategy", "keep-both", "users", List.of(userA, userB),
                "note", "互相冲突的人工决定：双方候选均保留，不覆盖"));
        merged.put("resolutions", resolutions);
        Store.Version v = store.putVersion(sample, Engine.DEC_BASELINE, win,
                conflictCurrent.deps(), merged);
        store.appendEvent(sample, "DECISION_RESOLVED", Map.of(
                "windowId", win, "strategy", "keep-both",
                "decisionVersionId", v.id()), null);
        return v;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map<?, ?> mm) v = deepCopy((Map<String, Object>) mm);
            else if (v instanceof List<?> ll) {
                List<Object> nl = new ArrayList<>();
                for (Object o : ll) {
                    if (o instanceof Map<?, ?> mm) nl.add(deepCopy((Map<String, Object>) mm));
                    else nl.add(o);
                }
                v = nl;
            }
            out.put(e.getKey(), v);
        }
        return out;
    }
}
