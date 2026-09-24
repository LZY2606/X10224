package dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 领域规则：每条规则产出机器可读的 Judgment，证据（输入片段、判定状态、受影响对象）
 * 全部落库。规则宁可保留多解/不确定，也不吞掉歧义。
 */
public final class Rules {
    private Rules() {}

    private static State.Judgment j(String id, String rule, String status, String subject, Map<String, Object> detail, int seq) {
        State.Judgment j = new State.Judgment();
        j.id = id;
        j.rule = rule;
        j.status = status;
        j.subject = subject;
        j.detail = detail;
        j.ts = System.currentTimeMillis();
        j.seq = seq;
        return j;
    }

    /** 反向热流：吸放热方向只能来自仪器表头元数据，文件名提示只记录在案，绝不参与判定。 */
    public static State.Judgment checkDirection(State.Curve c, int seq) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("curveId", c.id);
        d.put("directionMeta", c.directionMeta);
        d.put("filenameHint", c.filenameHint);
        d.put("policy", "direction-from-instrument-header-only");
        if ("exo-up".equals(c.directionMeta) || "endo-up".equals(c.directionMeta)) {
            if (c.filenameHint != null && !c.directionMeta.contains(c.filenameHint)) {
                d.put("note", "filename-hint-conflicts-with-header; header wins");
            }
            return j("jd-dir-" + c.id, "heat-flow-direction", "ok", c.id, d, seq);
        }
        d.put("candidates", List.of("exo-up", "endo-up"));
        d.put("note", "header missing/ambiguous; both conventions retained as candidates");
        return j("jd-dir-" + c.id, "heat-flow-direction", "undetermined", c.id, d, seq);
    }

    /** 非等间隔温度：积分必须按实际温度梯形求积，不得假设等间隔。 */
    public static State.Judgment checkSpacing(State.Curve c, int seq) {
        List<Double> deltas = new ArrayList<>();
        for (int i = 1; i < c.points.size(); i++) {
            deltas.add(Math.abs(c.points.get(i)[0] - c.points.get(i - 1)[0]));
        }
        double min = Double.MAX_VALUE, max = 0;
        for (double x : deltas) { min = Math.min(min, x); max = Math.max(max, x); }
        boolean uniform = deltas.isEmpty() || (max - min) < 1e-9;
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("curveId", c.id);
        d.put("minDeltaT", min == Double.MAX_VALUE ? 0 : min);
        d.put("maxDeltaT", max);
        d.put("policy", "trapezoid-on-actual-temperature");
        return j("jd-spc-" + c.id, "non-uniform-spacing", uniform ? "ok" : "flag", c.id, d, seq);
    }

    /** 重叠转折：同一扫描段上区间重叠的事件必须保留并列基线。 */
    public static List<State.Judgment> checkOverlaps(State st, int seq) {
        List<State.Judgment> out = new ArrayList<>();
        List<State.ThermalEvent> evs = new ArrayList<>(st.events.values());
        for (int i = 0; i < evs.size(); i++) {
            for (int k = i + 1; k < evs.size(); k++) {
                State.ThermalEvent a = evs.get(i), b = evs.get(k);
                if (!a.segmentId.equals(b.segmentId)) continue;
                double lo = Math.max(a.tStart, b.tStart), hi = Math.min(a.tEnd, b.tEnd);
                if (lo < hi) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("events", List.of(a.id, b.id));
                    d.put("overlapRange", List.of(lo, hi));
                    d.put("policy", "parallel-baselines-required");
                    out.add(j("jd-ovl-" + a.id + "-" + b.id, "overlapping-transitions", "flag",
                            a.segmentId, d, seq));
                }
            }
        }
        return out;
    }

    /** 循环段切换：积分区间不得跨越升温/降温段。 */
    public static State.Judgment checkSegmentMix(State st, String eventId, double t1, double t2, int seq) {
        State.ThermalEvent e = st.events.get(eventId);
        State.Segment seg = st.segments.get(e.segmentId);
        State.Curve c = st.curves.get(seg.curveId);
        int inside = 0;
        for (double[] p : c.points) if (p[0] >= Math.min(t1, t2) && p[0] <= Math.max(t1, t2)) inside++;
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("eventId", eventId);
        d.put("segmentKind", seg.kind);
        d.put("cycleIndex", seg.cycleIndex);
        d.put("pointsInRange", inside);
        // 区间跨段的判定：区间内同时出现升温与降温走向
        int rising = 0, falling = 0;
        double prev = Double.NaN;
        for (double[] p : c.points) {
            if (p[0] < Math.min(t1, t2) || p[0] > Math.max(t1, t2)) continue;
            if (!Double.isNaN(prev)) {
                if (p[0] > prev) rising++; else if (p[0] < prev) falling++;
            }
            prev = p[0];
        }
        boolean crosses = rising > 0 && falling > 0;
        d.put("risingSteps", rising);
        d.put("fallingSteps", falling);
        if (crosses) {
            d.put("policy", "heating-and-cooling-never-mixed");
            return j("jd-mix-" + eventId, "segment-mixing", "rejected", eventId, d, seq);
        }
        return j("jd-mix-" + eventId, "segment-mixing", "ok", eventId, d, seq);
    }

    /** 空区间：区间内无采样点时派生为空并附诊断，不崩溃、不伪造值。 */
    public static State.Judgment checkEmptyInterval(State.Curve c, String eventId, double t1, double t2, int seq) {
        int n = 0;
        for (double[] p : c.points) if (p[0] >= Math.min(t1, t2) && p[0] <= Math.max(t1, t2)) n++;
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("eventId", eventId);
        d.put("range", List.of(t1, t2));
        d.put("pointsInRange", n);
        if (n == 0) {
            d.put("diagnostic", "empty-interval: no samples in range; derived values left empty in place");
            return j("jd-empty-" + eventId, "empty-interval", "flag", eventId, d, seq);
        }
        return j("jd-empty-" + eventId, "empty-interval", "ok", eventId, d, seq);
    }

    /** 质量版本变更：所有归一化派生（J/g）过期，列出受影响对象。 */
    public static State.Judgment massRevision(State st, String oldMassId, String newMassId, int seq) {
        List<String> affected = new ArrayList<>();
        for (State.Derived d : st.derived.values()) {
            if ("ENTHALPY_NORMALIZED".equals(d.kind)) affected.add(d.id);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("oldMassId", oldMassId);
        d.put("newMassId", newMassId);
        d.put("affectedNormalizedDerived", affected);
        d.put("policy", "mass-revision-invalidates-all-normalized-derivations");
        return j("jd-mass-" + newMassId, "mass-revision", "flag", newMassId, d, seq);
    }

    /** 并列基线：多个合理候选保留差异（焓差 spread），不允许确认即覆盖。 */
    public static State.Judgment parallelBaselines(State st, String eventId, int seq) {
        List<Map<String, Object>> cands = new ArrayList<>();
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        boolean any = false;
        for (State.Derived der : st.derivedOf(eventId)) {
            if (!"ENTHALPY".equals(der.kind) || der.value.get("jPerG") == null) continue;
            double v = ((Number) der.value.get("jPerG")).doubleValue();
            min = Math.min(min, v); max = Math.max(max, v); any = true;
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("baselineId", der.baselineId);
            c.put("derivedId", der.id);
            c.put("jPerG", v);
            c.put("status", st.baselines.get(der.baselineId).status);
            cands.add(c);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("eventId", eventId);
        d.put("candidates", cands);
        d.put("spreadJPerG", any ? max - min : 0);
        d.put("policy", "all-plausible-baselines-retained");
        return j("jd-par-" + eventId, "parallel-baselines", cands.size() > 1 ? "flag" : "ok", eventId, d, seq);
    }
}
