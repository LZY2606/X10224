package org.materials.dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个 case 重放到某事件序号时的完整可见状态。
 * 所有集合保持插入顺序（顺序本身是身份关系的一部分）。
 */
public class CaseState {
    public CaseInfo info;
    public long seq = 0;
    public long revision = 0;
    public final Map<String, InstrumentOutput> outputs = new LinkedHashMap<>();
    public final List<String> outputOrder = new ArrayList<>();
    public final Map<String, MassRecord> masses = new LinkedHashMap<>();
    public final List<String> massOrder = new ArrayList<>();
    public final Map<String, RateRecord> rates = new LinkedHashMap<>();
    public final Map<String, AtmosphereRecord> atmospheres = new LinkedHashMap<>();
    public final Map<String, TemperatureCalibration> calibrations = new LinkedHashMap<>();
    public final Map<String, TangentSelection> tangents = new LinkedHashMap<>();
    public final Map<String, Segment> segments = new LinkedHashMap<>();
    public final Map<String, Transition> transitions = new LinkedHashMap<>();
    public final Map<String, BaselineCandidate> baselines = new LinkedHashMap<>();
    public final Map<String, EnthalpyResult> analyses = new LinkedHashMap<>();
    public final Map<String, DependencyNode> nodes = new LinkedHashMap<>();
    public final Map<String, List<String>> edges = new LinkedHashMap<>();
    public final List<Diagnostic> diagnostics = new ArrayList<>();
    public final List<Event> events = new ArrayList<>();

    public String currentMassId() {
        return massOrder.isEmpty() ? null : massOrder.get(massOrder.size() - 1);
    }

    public MassRecord currentMass() {
        String id = currentMassId();
        return id == null ? null : masses.get(id);
    }

    public List<BaselineCandidate> baselinesOf(String transitionId) {
        List<BaselineCandidate> out = new ArrayList<>();
        for (BaselineCandidate b : baselines.values()) {
            if (b.transitionId().equals(transitionId)) {
                out.add(b);
            }
        }
        return out;
    }
}
