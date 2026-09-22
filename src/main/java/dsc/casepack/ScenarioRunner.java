package dsc.casepack;

import dsc.core.ProjectService;
import dsc.domain.AtmosphereRecord;
import dsc.domain.BaselineResult;
import dsc.domain.CalibrationRecord;
import dsc.domain.EnthalpyResult;
import dsc.domain.InstrumentOutput;
import dsc.domain.TangentChoice;
import dsc.domain.TransitionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 可重复案例引导：以完整叙事（不是彼此无关的按钮）把材料组的
 * 加热/冷却 DSC 序列放入同一样品履历，并依次触发全部边界情形。
 *
 * 阶段：
 *  A 初始导入：六类来源分别存档；加热/冷却各成段；玻璃化+熔融+结晶基线与积分。
 *  B 参数修订：质量 12.40→12.10 mg，全部归一化派生过期并出现第二循环新版本。
 *  C 局部源改动：温度标定偏移 +0.3 K，只影响温度坐标派生（焓不失效）。
 *  D 边界组合：反向热流、非等间隔、重叠转折、循环段切换、并列基线、空区间、方向未决。
 *  E 互相冲突的人工决定：同一段先接受候选A、再否定A，两个决定都保留。
 */
public final class ScenarioRunner {

    private final ProjectService svc;
    public final List<String> steps = new ArrayList<>();

    public ScenarioRunner(ProjectService svc) {
        this.svc = svc;
    }

    public void runAll() {
        stageA_initialImport();
        stageB_massRevision();
        stageC_localCalibrationChange();
        stageD_boundaries();
        stageE_conflictingDecision();
    }

    private void log(String s) {
        steps.add(s);
    }

    private void stageA_initialImport() {
        log("A 初始导入：仪器输出/质量/速率/气氛/标定/切线分别存档");
        svc.archiveMass("mass_sample", 12.40, "12.40 mg", "初次称量", "reviewer.lin");
        svc.archiveAtmosphere(new AtmosphereRecord("atm_n2", "N2", 50.0, "50 mL/min",
                "2026-09-20T09:00:00Z"), "reviewer.lin");
        svc.archiveCalibration(new CalibrationRecord("cal_in", "In 156.5985 °C", 0.0,
                "indium melting point check", "2026-09-19T08:00:00Z"), "reviewer.lin");
        svc.archiveRate("rate_10", 10.0, "10 K/min", "reviewer.lin");

        InstrumentOutput heat = CasePack.instrument("out_heat1", "cycle1_heating_endo.csv");
        InstrumentOutput cool = CasePack.instrument("out_cool1", "cycle1_cooling_exo.csv");
        InstrumentOutput mw = CasePack.instrument("out_heat1_mw", "cycle1_heating_mW.csv");
        svc.archiveInstrument(heat, "reviewer.lin");
        svc.archiveInstrument(cool, "reviewer.lin");
        svc.archiveInstrument(mw, "reviewer.lin");

        svc.buildSegment("seg_heat1", "out_heat1", 1, "HEATING", "mass_sample", "cal_in",
                "rate_10", "atm_n2", "reviewer.lin");
        svc.buildSegment("seg_cool1", "out_cool1", 1, "COOLING", "mass_sample", "cal_in",
                "rate_10", "atm_n2", "reviewer.lin");
        svc.buildSegment("seg_heat1_mw", "out_heat1_mw", 1, "HEATING", "mass_sample", "cal_in",
                "rate_10", "atm_n2", "reviewer.lin");

        // 玻璃化台阶切线
        svc.archiveTangent(new TangentChoice("tg_glass1", "seg_heat1",
                318.15, 328.15, 338.15, 348.15, "玻璃化前后平直区", "reviewer.lin"), "reviewer.lin");
        BaselineResult glassBase = svc.proposeBaseline("seg_heat1", "tg_glass1",
                TransitionType.GLASS, "reviewer.lin");
        svc.analyzeCandidate("seg_heat1", glassBase, 0.01, 0.005, "reviewer.lin");

        // 熔融基线（两个并列候选）
        svc.archiveTangent(new TangentChoice("tg_melt_a", "seg_heat1",
                400.15, 415.15, 430.15, 445.15, "熔前/熔后平直区（窄窗）", "reviewer.lin"),
                "reviewer.lin");
        svc.archiveTangent(new TangentChoice("tg_melt_b", "seg_heat1",
                390.15, 418.15, 427.15, 450.15, "熔前/熔后平直区（宽窗）", "reviewer.lin"),
                "reviewer.lin");
        BaselineResult meltA = svc.proposeBaseline("seg_heat1", "tg_melt_a",
                TransitionType.MELTING, "reviewer.lin");
        BaselineResult meltB = svc.proposeBaseline("seg_heat1", "tg_melt_b",
                TransitionType.MELTING, "reviewer.lin");
        svc.analyzeCandidate("seg_heat1", meltA, 0.01, 0.005, "reviewer.lin");
        svc.analyzeCandidate("seg_heat1", meltB, 0.012, 0.005, "reviewer.lin");
        svc.acceptCandidate("seg_heat1", meltA.candidateId, "reviewer.lin",
                "窄窗切线更贴合熔前基线；候选B保留以备复核");

        // 冷却段结晶（exo-up 反向热流；加热冷却不混用）
        svc.archiveTangent(new TangentChoice("tg_crys1", "seg_cool1",
                405.15, 380.15, 370.15, 345.15, "结晶前后平台（冷却温度递减）", "reviewer.lin"),
                "reviewer.lin");
        BaselineResult crys = svc.proposeBaseline("seg_cool1", "tg_crys1",
                TransitionType.CRYSTALLIZATION, "reviewer.lin");
        svc.analyzeCandidate("seg_cool1", crys, 0.01, 0.005, "reviewer.lin");

        // mW 原始热流段的熔融（含一个坏点行）
        svc.archiveTangent(new TangentChoice("tg_melt_mw", "seg_heat1_mw",
                400.15, 415.15, 430.15, 445.15, "mW 段熔前/后平台", "reviewer.lin"),
                "reviewer.lin");
        BaselineResult meltMw = svc.proposeBaseline("seg_heat1_mw", "tg_melt_mw",
                TransitionType.MELTING, "reviewer.lin");
        svc.analyzeCandidate("seg_heat1_mw", meltMw, 0.01, 0.005, "reviewer.lin");
    }

    private void stageB_massRevision() {
        log("B 参数修订：质量 12.40→12.10 mg，所有归一化派生过期");
        svc.archiveMass("mass_sample", 12.10, "12.10 mg", "天平复核修订（旧值保留为 v1）",
                "reviewer.lin");
        InstrumentOutput heat2 = CasePack.instrument("out_heat2", "cycle2_heating_endo.csv");
        svc.archiveInstrument(heat2, "reviewer.lin");
        svc.buildSegment("seg_heat2", "out_heat2", 2, "HEATING", "mass_sample", "cal_in",
                "rate_10", "atm_n2", "reviewer.lin");
        svc.archiveTangent(new TangentChoice("tg_melt2", "seg_heat2",
                400.15, 415.15, 430.15, 445.15, "第二循环熔前/后平台", "reviewer.lin"),
                "reviewer.lin");
        BaselineResult b = svc.proposeBaseline("seg_heat2", "tg_melt2",
                TransitionType.MELTING, "reviewer.lin");
        svc.analyzeCandidate("seg_heat2", b, 0.01, 0.003, "reviewer.lin");
    }

    private void stageC_localCalibrationChange() {
        log("C 局部源改动：温度标定 +0.3 K，仅温度坐标派生受影响");
        svc.archiveCalibration(new CalibrationRecord("cal_in", "In 156.5985 °C", 0.3,
                "indium check recalibrated +0.3 K", "2026-09-21T08:00:00Z"),
                "reviewer.zhao");
    }

    private void stageD_boundaries() {
        log("D 边界组合：非等间隔/重叠/循环段切换/空区间/方向未决");
        InstrumentOutput nu = CasePack.instrument("out_nonuniform", "nonuniform_heating.csv");
        InstrumentOutput ov = CasePack.instrument("out_overlap", "overlap_heating.csv");
        InstrumentOutput mystery = CasePack.instrument("out_mystery", "mystery_heating.csv");
        svc.archiveInstrument(nu, "reviewer.lin");
        svc.archiveInstrument(ov, "reviewer.lin");
        svc.archiveInstrument(mystery, "reviewer.lin");

        svc.buildSegment("seg_nonuniform", "out_nonuniform", 3, "HEATING", "mass_sample",
                "cal_in", "rate_10", "atm_n2", "reviewer.lin");
        svc.archiveTangent(new TangentChoice("tg_nu", "seg_nonuniform",
                380.15, 395.15, 415.15, 430.15, "非等间隔段平台", "reviewer.lin"),
                "reviewer.lin");
        BaselineResult nuBase = svc.proposeBaseline("seg_nonuniform", "tg_nu",
                TransitionType.MELTING, "reviewer.lin");
        EnthalpyResult nuEn = svc.analyzeCandidate("seg_nonuniform", nuBase, 0.01, 0.005,
                "reviewer.lin");

        svc.buildSegment("seg_overlap", "out_overlap", 3, "HEATING", "mass_sample", "cal_in",
                "rate_10", "atm_n2", "reviewer.lin");
        // 重叠转折：两种合理切法 → 并列候选，全部保留
        svc.archiveTangent(new TangentChoice("tg_ov_a", "seg_overlap",
                405.15, 420.15, 442.15, 452.15, "整体包络基线（把肩峰并入主峰）",
                "reviewer.lin"), "reviewer.lin");
        svc.archiveTangent(new TangentChoice("tg_ov_b", "seg_overlap",
                405.15, 420.15, 430.15, 438.15, "主峰单独基线（肩峰另案）",
                "reviewer.lin"), "reviewer.lin");
        BaselineResult ovA = svc.proposeBaseline("seg_overlap", "tg_ov_a",
                TransitionType.MELTING, "reviewer.lin");
        BaselineResult ovB = svc.proposeBaseline("seg_overlap", "tg_ov_b",
                TransitionType.MELTING, "reviewer.lin");
        svc.analyzeCandidate("seg_overlap", ovA, 0.015, 0.005, "reviewer.lin");
        svc.analyzeCandidate("seg_overlap", ovB, 0.015, 0.005, "reviewer.lin");
        svc.acceptCandidate("seg_overlap", ovA.candidateId, "reviewer.lin",
                "包络焓用于热平衡；候选B保留显示解卷积差异");

        // 循环段切换（加热→冷却→加热），跨段积分被禁止
        svc.recordSegmentSwitch("seg_heat2", "seg_cool1", "reviewer.lin");
        svc.recordSegmentSwitch("seg_cool1", "seg_nonuniform", "reviewer.lin");
        svc.recordBoundary("CROSSES_SEGMENTS", "seg_cool1", "reviewer.lin",
                Map.of("requestedStartK", 430.15, "requestedEndK", 390.15,
                        "otherSegmentId", "seg_heat2"),
                "REJECTED", List.of("enthalpy:pseudo_cross_segment"));

        // 空区间：切线窗口落在无数据区
        svc.archiveTangent(new TangentChoice("tg_empty", "seg_heat2",
                500.15, 520.15, 530.15, 550.15, "误拖到段外的窗口", "reviewer.lin"),
                "reviewer.lin");
        BaselineResult empty = svc.proposeBaseline("seg_heat2", "tg_empty",
                TransitionType.MELTING, "reviewer.lin");

        // 方向未决：文件头缺失，禁止按 mystery 文件名猜测
        svc.buildSegment("seg_mystery", "out_mystery", 4, "HEATING", "mass_sample", "cal_in",
                "rate_10", "atm_n2", "reviewer.lin");
        svc.archiveTangent(new TangentChoice("tg_mystery", "seg_mystery",
                390.15, 405.15, 420.15, 435.15, "方向缺失段平台", "reviewer.lin"),
                "reviewer.lin");
        BaselineResult mys = svc.proposeBaseline("seg_mystery", "tg_mystery",
                TransitionType.UNKNOWN, "reviewer.lin");
        svc.analyzeCandidate("seg_mystery", mys, 0.01, 0.005, "reviewer.lin");
    }

    private void stageE_conflictingDecision() {
        log("E 互相冲突的人工决定：先接受再否定同一候选，两者都保留");
        List<BaselineResult> heats = svc.baselines("seg_heat1");
        BaselineResult meltA = heats.stream()
                .filter(b -> b.type == TransitionType.MELTING && "OK".equals(b.status))
                .findFirst().orElseThrow();
        svc.acceptCandidate("seg_heat1", meltA.candidateId, "reviewer.lin",
                "第一次复核：采纳窄窗基线");
        svc.rejectCandidate("seg_heat1", meltA.candidateId, "reviewer.zhao",
                "第二位审阅者认为窄窗受肩状拖尾影响，要求改用宽窗");
    }
}
