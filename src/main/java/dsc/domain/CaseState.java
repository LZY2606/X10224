package dsc.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 重放到某个事件序号后的事实快照（只含来源层事实，不含派生）。
 * 引擎在此之上做规范化与积分。
 */
public final class CaseState {
    public String caseId;
    public String name;
    public long seq;
    public final Map<String, Segment> segments = new LinkedHashMap<>();
    public final Map<String, String> segmentRawData = new LinkedHashMap<>();
    public final Map<String, MassVersion> masses = new LinkedHashMap<>();
    public String currentMassId;
    public final Map<String, Atmosphere> atmospheres = new LinkedHashMap<>();
    public final Map<String, TempCalibration> tempCals = new LinkedHashMap<>();
    public String currentTempCalId;
    public final Map<String, HeatFlowCalibration> hfCals = new LinkedHashMap<>();
    public String currentHfCalId;
    public final Map<String, TangentSelection> tangents = new LinkedHashMap<>();
    public final Map<String, AnalysisWindow> windows = new LinkedHashMap<>();
    /** windowId -> 已采纳基线候选 id（最近一次 accept）。 */
    public final Map<String, String> acceptedBaseline = new LinkedHashMap<>();
    /** 被明确否定的候选。 */
    public final java.util.Set<String> rejectedBaselines = new java.util.LinkedHashSet<>();

    public MassVersion currentMass() { return masses.get(currentMassId); }
    public TempCalibration tempCal() { return tempCals.get(currentTempCalId); }
    public HeatFlowCalibration hfCal() { return hfCals.get(currentHfCalId); }
}
