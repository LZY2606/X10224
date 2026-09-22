package org.materials.dsc.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 事件重放到某个序号后的完整状态。纯数据，不触碰数据库。 */
public final class ProjectState {

    public String projectId;
    public String ruleVersion;
    public final Map<String, Sample> samples = new LinkedHashMap<>();
    public final Map<String, MassVersion> masses = new LinkedHashMap<>();
    public final Map<String, InstrumentRun> runs = new LinkedHashMap<>();
    public final Map<String, TemperatureCalibration> calibrations = new LinkedHashMap<>();
    public final Map<String, TangentSelection> tangents = new LinkedHashMap<>();
    public final Map<String, Window> windows = new LinkedHashMap<>();
    /** 段：由 run 的显式方向与连续点范围派生，也可由事件显式登记。 */
    public final Map<String, ScanSegment> explicitSegments = new LinkedHashMap<>();
    /** 事件载荷中已被否定的候选 id -> 诊断。 */
    public final Map<String, String> rejectedBaselines = new LinkedHashMap<>();
    /** windowId -> 当前被采纳的候选 id（revoke 后移除）。 */
    public final Map<String, String> acceptedBaselines = new LinkedHashMap<>();
    /** windowId -> 家庭提示与重叠标记（拖动事件携带）。 */
    public final Map<String, WindowMeta> windowMeta = new LinkedHashMap<>();

    public record WindowMeta(String familyHint, boolean overlapFlag) {
    }
    /** 依赖图：nodeId -> 当前可见节点版本。 */
    public final Map<String, DerivedNode> graph = new LinkedHashMap<>();

    public List<MassVersion> massesOf(String sampleId) {
        return masses.values().stream().filter(m -> m.sampleId().equals(sampleId))
                .sorted(java.util.Comparator.comparingInt(MassVersion::seq)).toList();
    }

    public List<TemperatureCalibration> calibrationsOf(String runId) {
        return calibrations.values().stream().filter(c -> c.runId().equals(runId))
                .sorted(java.util.Comparator.comparingInt(TemperatureCalibration::seq)).toList();
    }
}
