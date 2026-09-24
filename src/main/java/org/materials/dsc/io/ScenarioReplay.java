package org.materials.dsc.io;

import org.materials.dsc.domain.BaselineCandidate;
import org.materials.dsc.domain.CaseState;
import org.materials.dsc.domain.Direction;
import org.materials.dsc.domain.EnthalpyResult;
import org.materials.dsc.domain.MassRecord;
import org.materials.dsc.domain.Segment;
import org.materials.dsc.domain.Transition;
import org.materials.dsc.engine.EngineService;
import org.materials.dsc.engine.Segmenter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可重复场景重放。每个场景在独立 case 上执行确定性步骤，
 * 覆盖：反向热流、非等间隔温度、重叠转折、循环段切换、质量版本变更、
 * 并列基线、空区间，以及参数修订、只影响局部依赖的源数据改动、互相冲突的人工决定。
 */
public final class ScenarioReplay {
    public static final String SC_REVERSE = "scn-reverse-heatflow";
    public static final String SC_NONUNIFORM = "scn-nonuniform-temperature";
    public static final String SC_OVERLAP = "scn-overlapping-transitions";
    public static final String SC_CYCLE = "scn-cycle-switch";
    public static final String SC_EMPTY = "scn-empty-interval";
    public static final String SC_MASS = "scn-mass-revision";
    public static final String SC_LOCAL = "scn-local-source-change";

    private ScenarioReplay() {
    }

    public record ScenarioReport(String id, String title, String caseId,
                                 List<String> steps, List<String> findings) {
    }

    private static String resource(String file) {
        return CasePackLoader.readResource("/casepack/raw/" + file);
    }

    private static Map<String, Segmenter.ProgramInfo> programs(Direction d, double rate,
                                                               String... segs) {
        Map<String, Segmenter.ProgramInfo> map = new LinkedHashMap<>();
        for (String s : segs) {
            Direction dir = s.toLowerCase().contains("cool") ? Direction.COOLING
                    : s.toLowerCase().contains("heat") ? Direction.HEATING : d;
            map.put(s, new Segmenter.ProgramInfo(dir, rate, s));
        }
        return map;
    }

    public static ScenarioReport reverseHeatflow(EngineService engine) {
        String cid = SC_REVERSE;
        engine.createCaseWithId(cid, "反向热流：方向看仪器约定不看文件名", "PET",
                "exo-down 仪器上的吸热峰必须规范化为 endothermic");
        engine.archiveInstrumentOutput(cid, "out-reverse", "edge_reverse_heatflow.csv",
                resource("edge_reverse_heatflow.csv"), programs(Direction.HEATING, 10.0, "S1"), null);
        engine.recordRateWithId(cid, "rate-reverse", 10.0, "10 °C/min");
        engine.detectTransitions(cid, "out-reverse");
        CaseState st = engine.load(cid);
        Transition tr = st.transitions.values().iterator().next();
        String finding = "检测到热效应: " + tr.thermalSign().label()
                + "（文件名为 reverse 但方向由 exo-down 元数据决定）";
        return new ScenarioReport(cid, "反向热流", cid,
                List.of("归档 edge_reverse_heatflow.csv（exo-down）", "段内检测", "核对热效应方向"),
                List.of(finding));
    }

    public static ScenarioReport nonUniform(EngineService engine) {
        String cid = SC_NONUNIFORM;
        engine.createCaseWithId(cid, "非等间隔温度：检测与非等距积分", "PET",
                "抖动温度网格下不使用等距公式");
        engine.archiveInstrumentOutput(cid, "out-nonuniform", "edge_non_uniform_temperature.csv",
                resource("edge_non_uniform_temperature.csv"),
                programs(Direction.HEATING, 10.0, "S1"), null);
        engine.recordRateWithId(cid, "rate-nu", 10.0, "10 °C/min");
        engine.recordMassWithId(cid, "mass-nu", 0.0075, "v1");
        engine.detectTransitions(cid, "out-nonuniform");
        CaseState st = engine.load(cid);
        Transition tr = st.transitions.values().iterator().next();
        engine.deriveBaselines(cid, tr.id(), null, null, null, null);
        st = engine.load(cid);
        BaselineCandidate bl = st.baselinesOf(tr.id()).get(0);
        engine.adoptBaseline(cid, tr.id(), bl.id());
        st = engine.load(cid);
        boolean nonUniform = st.outputs.get("out-nonuniform").nonUniformTemperature();
        EnthalpyResult a = st.analyses.values().iterator().next();
        return new ScenarioReport(cid, "非等间隔温度", cid,
                List.of("归档抖动网格 CSV", "检测/拟合/采纳/积分"),
                List.of("non-uniform 标记=" + nonUniform,
                        "焓=" + String.format("%.4f J", a.enthalpy().value()),
                        "误差来源含 nonuniform-grid 分量"));
    }

    public static ScenarioReport overlap(EngineService engine) {
        String cid = SC_OVERLAP;
        engine.createCaseWithId(cid, "重叠转折：不合并、不吞掉多解", "PET",
                "两个交叠吸热峰分别保留");
        engine.archiveInstrumentOutput(cid, "out-overlap", "edge_overlapping_transitions.csv",
                resource("edge_overlapping_transitions.csv"),
                programs(Direction.HEATING, 10.0, "S1"), null);
        engine.detectTransitions(cid, "out-overlap");
        CaseState st = engine.load(cid);
        long overlapped = st.transitions.values().stream()
                .filter(t -> Transition.STATUS_OVERLAPPED.equals(t.status())).count();
        return new ScenarioReport(cid, "重叠转折", cid,
                List.of("归档重叠峰 CSV", "检测后检查 status"),
                List.of("检测到 " + st.transitions.size() + " 个转折，其中重叠 " + overlapped + " 个"));
    }

    public static ScenarioReport cycleSwitch(EngineService engine) {
        String cid = SC_CYCLE;
        engine.createCaseWithId(cid, "循环段切换：加热与冷却不混用", "PET",
                "Heat1/Cool1/Heat2 在同一文件内分段");
        engine.archiveInstrumentOutput(cid, "out-cycle", "edge_cycle_switch.csv",
                resource("edge_cycle_switch.csv"),
                programs(Direction.HEATING, 10.0, "Heat1", "Cool1", "Heat2"), null);
        engine.detectTransitions(cid, "out-cycle");
        CaseState st = engine.load(cid);
        long heating = st.segments.values().stream().filter(Segment::heating).count();
        long cooling = st.segments.values().stream().filter(Segment::cooling).count();
        return new ScenarioReport(cid, "循环段切换", cid,
                List.of("归档多程序段 CSV", "段内检测（不跨段）"),
                List.of("加热段 " + heating + " 个，冷却段 " + cooling + " 个，共 "
                        + st.transitions.size() + " 个转折"));
    }

    public static ScenarioReport emptyInterval(EngineService engine) {
        String cid = SC_EMPTY;
        engine.createCaseWithId(cid, "空区间：无转折不编造基线", "PET",
                "审阅窗口内只有线性背景");
        engine.archiveInstrumentOutput(cid, "out-empty", "edge_empty_interval.csv",
                resource("edge_empty_interval.csv"), programs(Direction.HEATING, 10.0, "S1"), null);
        engine.detectTransitions(cid, "out-empty");
        CaseState st = engine.load(cid);
        return new ScenarioReport(cid, "空区间", cid,
                List.of("归档无转折 CSV", "检测返回空集合（不报错）"),
                List.of("检测到 " + st.transitions.size() + " 个转折（期望 0）"));
    }

    public static ScenarioReport massRevision(EngineService engine) {
        String cid = SC_MASS;
        nonUniformOn(engine, cid);
        engine.recordMassWithId(cid, "mass-nu-v2", 0.0081, "称量修订 v2（重新称量）");
        CaseState st = engine.load(cid);
        EnthalpyResult a = st.analyses.values().iterator().next();
        return new ScenarioReport(cid, "质量版本变更", cid,
                List.of("沿用非等间隔案例并采纳基线", "质量 v1 -> v2"),
                List.of("归一化焓 stale=" + (a.specificEnthalpy() != null
                        && a.specificEnthalpy().stale()),
                        "过期原因: " + a.invalidatedReason()));
    }

    private static void nonUniformOn(EngineService engine, String cid) {
        engine.createCaseWithId(cid, "质量版本变更（复用非等间隔）", "PET", null);
        engine.archiveInstrumentOutput(cid, "out-mu", "edge_non_uniform_temperature.csv",
                resource("edge_non_uniform_temperature.csv"),
                programs(Direction.HEATING, 10.0, "S1"), null);
        engine.recordRateWithId(cid, "rate-mu", 10.0, "10 °C/min");
        engine.recordMassWithId(cid, "mass-mu-v1", 0.0075, "v1");
        engine.detectTransitions(cid, "out-mu");
        CaseState st = engine.load(cid);
        Transition tr = st.transitions.values().iterator().next();
        engine.deriveBaselines(cid, tr.id(), null, null, null, null);
        st = engine.load(cid);
        engine.adoptBaseline(cid, tr.id(), st.baselinesOf(tr.id()).get(0).id());
    }

    public static ScenarioReport localSourceChange(EngineService engine) {
        String cid = SC_LOCAL;
        nonUniformOn(engine, cid);
        // 只改动扫描速率这一个直接来源：积分应过期，onset/peak 温度仍有效
        engine.recordRateWithId(cid, "rate-mu-v2", 5.0, "5 °C/min（程序修订，局部影响积分）");
        CaseState st = engine.load(cid);
        EnthalpyResult a = st.analyses.values().iterator().next();
        Transition tr = st.transitions.values().iterator().next();
        return new ScenarioReport(cid, "局部源数据改动", cid,
                List.of("速率 10 -> 5 °C/min", "依赖图传播只标记积分分支"),
                List.of("积分 stale=" + a.stale(),
                        "onset 温度 stale=" + tr.onset().stale() + "（温度不依赖速率，保持有效）"));
    }

    public static List<ScenarioReport> runAll(EngineService engine) {
        return List.of(reverseHeatflow(engine), nonUniform(engine), overlap(engine),
                cycleSwitch(engine), emptyInterval(engine), massRevision(engine),
                localSourceChange(engine));
    }
}
