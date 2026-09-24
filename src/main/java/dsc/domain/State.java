package dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 完整领域状态：每个版本一次整体快照。所有实体带稳定 id 与 rev，
 * 派生实体通过 inputs(role -> "id@rev") 记录依赖坐标。
 */
public class State {
    public String sampleId = "sample-A";
    public Map<String, Curve> curves = new LinkedHashMap<>();
    public Map<String, Segment> segments = new LinkedHashMap<>();
    public Map<String, MassVersion> masses = new LinkedHashMap<>();
    public String currentMassId;
    public Map<String, Calibration> calibrations = new LinkedHashMap<>();
    public Map<String, Tangent> tangents = new LinkedHashMap<>();
    public Map<String, ThermalEvent> events = new LinkedHashMap<>();
    public Map<String, Baseline> baselines = new LinkedHashMap<>();
    public Map<String, Derived> derived = new LinkedHashMap<>();
    public List<Judgment> judgments = new ArrayList<>();

    /** 仪器原始曲线：保留原始表示（表头原文、原始单位），派生层才规范化。 */
    public static class Curve {
        public String id;
        public int rev;
        public String sourceFile;
        public List<String> rawHeader = new ArrayList<>();
        /** 来自仪器表头的热流方向约定: exo-up | endo-up | unknown。绝不由文件名推断。 */
        public String directionMeta = "unknown";
        /** 文件名中出现的方向词（仅作证据记录，不参与判定）。 */
        public String filenameHint;
        public String tempUnit = "degC";
        public String heatFlowUnit = "mW";
        /** [temperature, heatFlow]，原始坐标顺序保持不变。 */
        public List<double[]> points = new ArrayList<>();
    }

    /** 扫描段：升温/降温不混用。 */
    public static class Segment {
        public String id;
        public int rev;
        public String curveId;
        public String kind; // HEATING | COOLING
        public double rateKPerMin;
        public String atmosphere;
        public String calibrationId;
        public int cycleIndex;
    }

    public static class MassVersion {
        public String id;
        public int rev;
        public double mg;
        public String note;
        public long ts;
    }

    public static class Calibration {
        public String id;
        public int rev;
        public double offsetK;
        public double slope;
        public String reference;
    }

    /** 用户选定的切线，与仪器输出分别存档。 */
    public static class Tangent {
        public String id;
        public int rev;
        public String eventId;
        public String author;
        public String side; // left | right
        public double t1;
        public double t2;
    }

    public static class ThermalEvent {
        public String id;
        public int rev;
        public String segmentId;
        public String type; // GLASS_TRANSITION | CRYSTALLIZATION | MELTING
        public double tStart;
        public double tEnd;
        public String status = "open"; // open | reviewed
    }

    /** 基线候选：多个合理基线并存，差异保留。 */
    public static class Baseline {
        public String id;
        public int rev;
        public String eventId;
        public String method; // linear-chord | left-tangent | sigmoidal
        public double slope;
        public double intercept;
        public String status = "candidate"; // candidate | accepted | rejected
        public String originEventId;
    }

    /** 派生结果：身份稳定，inputs 记录依赖坐标，status 标记 valid/stale/empty/undetermined。 */
    public static class Derived {
        public String id;
        public int rev;
        public String kind; // ONSET | PEAK | ENDSET | ENTHALPY | ENTHALPY_NORMALIZED
        public String eventId;
        public String baselineId;
        public Map<String, String> inputs = new LinkedHashMap<>();
        public String status = "valid";
        public Map<String, Object> value = new LinkedHashMap<>();
        public List<String> errorSources = new ArrayList<>();
        public String note;
    }

    /** 机器可读的判定事件。 */
    public static class Judgment {
        public String id;
        public String rule;
        public String status; // ok | flag | undetermined | rejected
        public String subject;
        public Map<String, Object> detail = new LinkedHashMap<>();
        public long ts;
        public int seq;
    }

    public Curve curveOf(Segment s) { return curves.get(s.curveId); }

    public MassVersion currentMass() { return masses.get(currentMassId); }

    public List<Baseline> baselinesOf(String eventId) {
        List<Baseline> out = new ArrayList<>();
        for (Baseline b : baselines.values()) if (b.eventId.equals(eventId)) out.add(b);
        return out;
    }

    public List<Derived> derivedOf(String eventId) {
        List<Derived> out = new ArrayList<>();
        for (Derived d : derived.values()) if (d.eventId.equals(eventId)) out.add(d);
        return out;
    }

    public State copy() {
        return dsc.Json.convert(dsc.Json.convert(this, Map.class), State.class);
    }
}
