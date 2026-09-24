package dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 依赖图传播：每个 Derived 通过 inputs(role -> "id@rev") 声明依赖坐标。
 * 源变化后只重算输入坐标失配的派生；仍有效的派生保留并说明理由。
 */
public final class Deriver {
    private Deriver() {}

    /** 为全部事件×全部基线候选建立/刷新派生（首次导入用）。 */
    public static void deriveAll(State st) {
        for (State.ThermalEvent e : st.events.values()) {
            for (State.Baseline bl : st.baselinesOf(e.id)) {
                deriveOne(st, e, bl);
            }
        }
    }

    /** 依赖传播：重算失配派生，返回每项结果为何有效/为何重算的报告。 */
    public static Map<String, Object> propagate(State st) {
        List<String> recomputed = new ArrayList<>();
        Map<String, String> kept = new LinkedHashMap<>();
        for (State.Derived d : st.derived.values()) {
            String mismatch = firstMismatch(st, d);
            if (mismatch == null) {
                if (!"stale".equals(d.status)) {
                    kept.put(d.id, "inputs unchanged at " + stamps(d));
                    continue;
                }
            }
            State.ThermalEvent e = st.events.get(d.eventId);
            State.Baseline bl = st.baselines.get(d.baselineId);
            if (e == null || bl == null) continue;
            recompute(st, d, e, bl);
            recomputed.add(d.id + " (was: " + (mismatch == null ? "marked-stale" : mismatch) + ")");
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("recomputed", recomputed);
        report.put("stillValid", kept);
        return report;
    }

    private static String stamps(State.Derived d) {
        return d.inputs.values().toString();
    }

    /** 返回第一个失配依赖的描述，全部匹配返回 null。 */
    public static String firstMismatch(State st, State.Derived d) {
        for (Map.Entry<String, String> in : d.inputs.entrySet()) {
            String[] parts = in.getValue().split("@");
            String id = parts[0];
            int rev = Integer.parseInt(parts[1]);
            Integer cur = currentRev(st, id);
            if (cur == null) return in.getKey() + " " + id + " missing";
            if (cur != rev) return in.getKey() + " " + id + " rev " + rev + " -> " + cur;
        }
        return null;
    }

    public static Integer currentRev(State st, String id) {
        if (st.curves.containsKey(id)) return st.curves.get(id).rev;
        if (st.segments.containsKey(id)) return st.segments.get(id).rev;
        if (st.masses.containsKey(id)) return st.masses.get(id).rev;
        if (st.calibrations.containsKey(id)) return st.calibrations.get(id).rev;
        if (st.tangents.containsKey(id)) return st.tangents.get(id).rev;
        if (st.events.containsKey(id)) return st.events.get(id).rev;
        if (st.baselines.containsKey(id)) return st.baselines.get(id).rev;
        return null;
    }

    /** 为单个 (event, baseline) 组合建立或重算全部派生。 */
    public static void deriveOne(State st, State.ThermalEvent e, State.Baseline bl) {
        State.Segment seg = st.segments.get(e.segmentId);
        State.Curve c = st.curves.get(seg.curveId);
        State.MassVersion mass = st.currentMass();
        State.Calibration cal = st.calibrations.get(seg.calibrationId);

        Map<String, String> baseInputs = new LinkedHashMap<>();
        baseInputs.put("event", e.id + "@" + e.rev);
        baseInputs.put("baseline", bl.id + "@" + bl.rev);
        baseInputs.put("curve", c.id + "@" + c.rev);
        baseInputs.put("segment", seg.id + "@" + seg.rev);
        if (cal != null) baseInputs.put("calibration", cal.id + "@" + cal.rev);

        Map<String, Object> r = Integrate.analyze(st, e, bl);

        upsert(st, "der:onset:" + e.id + ":" + bl.id, "ONSET", e, bl, baseInputs, r,
                r == null ? null : (Map<String, Object>) r.get("onset"), r);
        upsert(st, "der:peak:" + e.id + ":" + bl.id, "PEAK", e, bl, baseInputs, r,
                r == null ? null : (Map<String, Object>) r.get("peak"), r);
        upsert(st, "der:endset:" + e.id + ":" + bl.id, "ENDSET", e, bl, baseInputs, r,
                r == null ? null : (Map<String, Object>) r.get("endset"), r);
        Map<String, Object> enth = r == null ? null : Map.of("areaMJ", r.get("areaMJ"), "unit", "mJ");
        upsert(st, "der:enth:" + e.id + ":" + bl.id, "ENTHALPY", e, bl, baseInputs, r, enth, r);

        Map<String, String> normInputs = new LinkedHashMap<>(baseInputs);
        if (mass != null) normInputs.put("mass", mass.id + "@" + mass.rev);
        Map<String, Object> norm = r == null ? null
                : Map.of("jPerG", r.get("jPerG"), "unit", "J/g", "massId", mass == null ? "none" : mass.id);
        upsert(st, "der:enth-norm:" + e.id + ":" + bl.id, "ENTHALPY_NORMALIZED", e, bl, normInputs, r, norm, r);
    }

    @SuppressWarnings("unchecked")
    private static void upsert(State st, String id, String kind, State.ThermalEvent e, State.Baseline bl,
                               Map<String, String> inputs, Map<String, Object> full,
                               Map<String, Object> value, Map<String, Object> raw) {
        State.Derived d = st.derived.get(id);
        if (d == null) {
            d = new State.Derived();
            d.id = id;
            d.kind = kind;
            d.eventId = e.id;
            d.baselineId = bl.id;
            st.derived.put(id, d);
        }
        d.inputs = inputs;
        if (full == null) {
            d.status = "empty";
            d.value = new LinkedHashMap<>();
            d.errorSources = new ArrayList<>();
            d.note = "empty-interval: no samples in [" + e.tStart + ", " + e.tEnd + "]";
        } else {
            State.Curve c = st.curves.get(st.segments.get(e.segmentId).curveId);
            if ("unknown".equals(c.directionMeta) && "PEAK".equals(kind)) {
                d.status = "undetermined";
                d.note = "direction unknown; both conventions retained";
            } else {
                d.status = "valid";
                d.note = null;
            }
            d.value = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
            d.errorSources = (List<String>) raw.get("errorSources");
        }
    }

    private static void recompute(State st, State.Derived d, State.ThermalEvent e, State.Baseline bl) {
        // 同一 (event, baseline) 的派生组一起重算，保持坐标一致
        deriveOne(st, e, bl);
    }
}
