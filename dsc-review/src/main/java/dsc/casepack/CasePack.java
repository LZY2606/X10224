package dsc.casepack;

import dsc.core.Ids;
import dsc.events.Event;
import dsc.events.HistoryState;
import dsc.model.*;
import dsc.service.*;
import dsc.storage.Database;
import dsc.storage.EventStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the offline case pack "热转折案：DSC 基线、相变积分与扫描历史审阅器".
 *
 * <p>One scripted history replays, in order:
 * separate archiving of the six source kinds → a parameter revision → a
 * local-only source correction → conflicting human decisions, and actually
 * traverses reversed heat flow, non-uniform grid, overlapping transitions,
 * cycle switching, mass version change, parallel baselines and an empty
 * interval. Everything is deterministic and fixture-only.
 */
public final class CasePack {

    public static final String HISTORY_ID = "thermal-turning-case";
    public static final String HISTORY_NAME = "热转折案：DSC 基线、相变积分与扫描历史审阅器";

    // stable segment ids so the narrative is reproducible after re-import
    public static final String SEG_HEAT_1 = "seg_heat_c1";
    public static final String SEG_COOL_1 = "seg_cool_c1";
    public static final String SEG_HEAT_2 = "seg_heat_c2";
    public static final String SEG_OVERLAP = "seg_overlap_c2";
    public static final String SEG_REVERSED = "seg_reversed_c1";

    private final DscService svc;
    private final DscServiceDerive derive;
    private final EventStore store;
    private HistoryStage checkpoints;
    public final List<StoryStep> story = new ArrayList<>();
    public String glassId, meltId, crystId, emptyId, revMeltId, melt2Id, overlapId;

    public CasePack(Database db) {
        this.store = new EventStore(db);
        this.svc = new DscService(store);
        this.derive = new DscServiceDerive(svc);
    }

    public HistoryStage buildBoundaryPhaseCheckpoints() { return checkpoints; }

    public DscService service() { return svc; }
    public DscServiceDerive deriveService() { return derive; }
    public EventStore store() { return store; }

    public HistoryState build() {
        svc.openHistory(new Commands.OpenHistory(HISTORY_ID, HISTORY_NAME, "materials-group"));
        story.add(new StoryStep("open", "建立同一样品履历：加热与冷却序列进入同一条历史", List.of()));

        // 1. initial version: six source kinds archived separately
        TraceImport heat1 = trace(SEG_HEAT_1, "instrument/DSC_run_heating.txt", 1,
                "exo-up", 10.0, SyntheticDsc.heatingCurve(40, 190, false, false));
        svc.importTrace(HISTORY_ID, new Commands.ImportTrace("instrument",
                "仪器输出逐点存档（加热段 cycle 1；方向由数据判定）", heat1, null));
        TraceImport cool1 = trace(SEG_COOL_1, "instrument/DSC_run_cooling.txt", 1,
                "exo-up", -10.0, SyntheticDsc.coolingCurve(190, 40, false));
        svc.importTrace(HISTORY_ID, new Commands.ImportTrace("instrument",
                "仪器输出逐点存档（冷却段 cycle 1）", cool1, null));
        svc.recordMass(HISTORY_ID, new Commands.RecordMass("operator", "样品质量首次存档",
                "8.42", "mg", 1, false, "initial weighing"));
        svc.recordAtmosphere(HISTORY_ID, new Commands.RecordAtmosphere("operator", "N2",
                "50", "mL/min", "purge gas archived separately"));
        svc.recordCalibration(HISTORY_ID, new Commands.RecordCalibration("operator", "In",
                0.0, "indium check, no offset"));
        svc.declareRate(HISTORY_ID, new Commands.DeclareRate("operator", SEG_HEAT_1, 10.0, "°C/min"));
        svc.declareRate(HISTORY_ID, new Commands.DeclareRate("operator", SEG_COOL_1, -10.0, "°C/min"));
        story.add(new StoryStep("sources",
                "仪器输出、样品质量、扫描速率、气氛、温度标定分别存档；切线将单独选择", List.of()));

        // glass + melt regions on heating; crystallization on cooling
        String glass = region("glass", SEG_HEAT_1, "glass", 60, 84, "玻璃化 Tg");
        String melt = region("melt", SEG_HEAT_1, "melt", 140, 170, "熔融峰");
        String cryst = region("cryst", SEG_COOL_1, "crystallization", 125, 155, "冷却结晶峰");
        svc.defineRegion(HISTORY_ID, new Commands.DefineRegion("reviewer", SEG_HEAT_1,
                "glass", 60, 84, "玻璃化 Tg"));
        svc.defineRegion(HISTORY_ID, new Commands.DefineRegion("reviewer", SEG_HEAT_1,
                "melt", 140, 170, "熔融峰"));
        svc.defineRegion(HISTORY_ID, new Commands.DefineRegion("reviewer", SEG_COOL_1,
                "crystallization", 125, 155, "冷却结晶峰"));
        svc.selectTangent(HISTORY_ID, new Commands.SelectTangent("reviewer", glass,
                60, 66, 80, 84, TangentSelection.MODE_TWO_SIDED, "Tg 两侧切线"));
        svc.selectTangent(HISTORY_ID, new Commands.SelectTangent("reviewer", melt,
                140, 146, 164, 170, TangentSelection.MODE_TWO_SIDED, "熔融两侧切线"));
        svc.selectTangent(HISTORY_ID, new Commands.SelectTangent("reviewer", cryst,
                125, 131, 149, 155, TangentSelection.MODE_TWO_SIDED, "结晶两侧切线"));
        derive.recompute(HISTORY_ID, glass);
        derive.recompute(HISTORY_ID, melt);
        derive.recompute(HISTORY_ID, cryst);
        story.add(new StoryStep("first-derivation",
                "每个区间产生并列基线候选与 onset/peak/endset、焓变误差来源",
                List.of(glass, melt, cryst)));

        // 2. empty interval (reviewer drags a handle into a data-free window)
        String empty = region("empty", SEG_HEAT_1, "generic", 185, 189.5, "空区间");
        svc.defineRegion(HISTORY_ID, new Commands.DefineRegion("reviewer", SEG_HEAT_1,
                "generic", 185, 189.5, "空区间"));
        svc.selectTangent(HISTORY_ID, new Commands.SelectTangent("reviewer", empty,
                185, 185.5, 189, 189.5, TangentSelection.MODE_TWO_SIDED, "无数据切线"));
        derive.recompute(HISTORY_ID, empty);
        story.add(new StoryStep("empty", "空区间：不删除、不伪造，保留 INDETERMINATE 与判定", List.of(empty)));

        glassId = glass; meltId = melt; crystId = cryst; emptyId = empty;
        return svc.state(HISTORY_ID);
    }

    /** Second part: boundary combinations + revision + amendment + conflict. */
    public HistoryStage buildBoundaryPhase() {
        HistoryState s0 = svc.state(HISTORY_ID);
        // reversed heat-flow with unknown convention
        TraceImport rev = trace(SEG_REVERSED, "instrument/reversed_run_01.txt", 2,
                "unknown", 10.0, SyntheticDsc.heatingCurve(40, 190, false, true));
        svc.importTrace(HISTORY_ID, new Commands.ImportTrace("instrument",
                "反向热流：仪器未声明 exo/endo，方向不靠文件名猜测", rev, null));
        svc.declareRate(HISTORY_ID, new Commands.DeclareRate("operator", SEG_REVERSED, 10.0, "°C/min"));
        revMeltId = region("revmelt", SEG_REVERSED, "melt", 140, 170, "反向熔融"); String revMelt = revMeltId;
        svc.defineRegion(HISTORY_ID, new Commands.DefineRegion("reviewer", SEG_REVERSED,
                "melt", 140, 170, "反向熔融"));
        svc.selectTangent(HISTORY_ID, new Commands.SelectTangent("reviewer", revMelt,
                140, 146, 164, 170, TangentSelection.MODE_TWO_SIDED, ""));
        derive.recompute(HISTORY_ID, revMelt);
        story.add(new StoryStep("reversed",
                "反向热流 + 约定未知：焓变符号为 AMBIGUOUS，大小仍给出", List.of(revMelt)));

        // non-uniform grid heating in cycle 2
        TraceImport heat2 = trace(SEG_HEAT_2, "instrument/rerun_heating_cycle2.txt", 2,
                "exo-up", 10.0, SyntheticDsc.heatingCurve(40, 190, true, false));
        svc.importTrace(HISTORY_ID, new Commands.ImportTrace("instrument",
                "循环段切换：cycle 2 加热，非等间隔温度（峰区加密）", heat2, null));
        svc.declareRate(HISTORY_ID, new Commands.DeclareRate("operator", SEG_HEAT_2, 10.0, "°C/min"));
        melt2Id = region("melt2", SEG_HEAT_2, "melt", 140, 170, "二次加热熔融"); String melt2 = melt2Id;
        svc.defineRegion(HISTORY_ID, new Commands.DefineRegion("reviewer", SEG_HEAT_2,
                "melt", 140, 170, "二次加热熔融"));
        svc.selectTangent(HISTORY_ID, new Commands.SelectTangent("reviewer", melt2,
                140, 146, 164, 170, TangentSelection.MODE_TWO_SIDED, ""));
        derive.recompute(HISTORY_ID, melt2);
        story.add(new StoryStep("nonuniform-cycle2",
                "非等间隔温度按各自步长积分；cycle 2 与 cycle 1 不混用", List.of(melt2)));

        // overlapping transitions on their own segment
        TraceImport ov = trace(SEG_OVERLAP, "instrument/overlap_run.txt", 2,
                "exo-up", 10.0, SyntheticDsc.overlappingCurve());
        svc.importTrace(HISTORY_ID, new Commands.ImportTrace("instrument",
                "重叠转折：两个相邻熔融峰共享切线候选", ov, null));
        svc.declareRate(HISTORY_ID, new Commands.DeclareRate("operator", SEG_OVERLAP, 10.0, "°C/min"));
        overlapId = region("overlap", SEG_OVERLAP, "melt", 140, 168, "重叠熔融双峰"); String overlap = overlapId;
        svc.defineRegion(HISTORY_ID, new Commands.DefineRegion("reviewer", SEG_OVERLAP,
                "melt", 140, 168, "重叠熔融双峰"));
        svc.selectTangent(HISTORY_ID, new Commands.SelectTangent("reviewer", overlap,
                140, 145, 163, 168, TangentSelection.MODE_TWO_SIDED, "双峰共用切线"));
        derive.recompute(HISTORY_ID, overlap);
        story.add(new StoryStep("overlap", "重叠转折：多个合理基线全部保留，不强行单解", List.of(overlap)));

        // 3. mass revision: every normalized derivation expires
        long seqBeforeMass = svc.headSeq(HISTORY_ID);
        svc.recordMass(HISTORY_ID, new Commands.RecordMass("operator",
                "天平复核：质量修订", "8.31", "mg", 2, true, "balance re-check"));
        derive.recompute(HISTORY_ID, meltId);
        derive.recompute(HISTORY_ID, melt2);
        story.add(new StoryStep("mass-revision",
                "质量版本变更：所有归一化派生过期并以新质量重算；旧版本保留",
                List.of(meltId, melt2), seqBeforeMass));

        // 4. local source amendment: one fragment only
        long seqBeforeAmend = svc.headSeq(HISTORY_ID);
        TraceImport fixed = new TraceImport();
        fixed.sourceFile = "instrument/rerun_heating_cycle2.txt";
        fixed.cycle = 2;
        fixed.conventionRaw = "exo-up";
        fixed.declaredRate = 10.0;
        SyntheticDsc.withBadCells(
        SyntheticDsc.heatingCurve(40, 190, true, false), 50, 51)
        .forEach(r -> fixed.rows.add(new TraceImport.Row(r.t, r.hf, null)));
        Commands.AmendSource amend = new Commands.AmendSource("instrument", SEG_HEAT_2, fixed,
                "局部源数据修正：仅替换 cycle2 加热片段，两个单元格无法转换并就地保留诊断");
        derive.amendSource(HISTORY_ID, amend, List.of(melt2));
        story.add(new StoryStep("local-amend",
                "只改动一个直接来源：仅 cycle2 下游受影响；冷却段与 cycle1 结论仍有效",
                List.of(melt2), seqBeforeAmend));

        // 5. conflicting human decisions: accept two different baselines over time
        long seqBeforeDecision = svc.headSeq(HISTORY_ID);
        var cands = svc.state(HISTORY_ID).baselinesForRegion(meltId);
        String firstChoice = cands.get(0).id();
        String secondChoice = cands.size() > 1 ? cands.get(1).id() : cands.get(0).id();
        svc.acceptCandidate(HISTORY_ID, new Commands.AcceptCandidate("reviewer-A", meltId,
                firstChoice, "审阅者 A 先接受双侧切线基线"));
        svc.acceptCandidate(HISTORY_ID, new Commands.AcceptCandidate("reviewer-B", meltId,
                secondChoice, "审阅者 B 比较两个分支后改选平行基线；未选候选继续保留"));
        story.add(new StoryStep("conflict-decision",
                "互相冲突的人工决定：两次接受都成为事件，可回到任一时点看当时结论",
                List.of(meltId), seqBeforeDecision));

        checkpoints = new HistoryStage(seqBeforeMass, seqBeforeAmend, seqBeforeDecision,
                List.of(glassId, meltId, crystId, emptyId, revMelt, melt2, overlap));
        return checkpoints;
    }

    private TraceImport trace(String id, String file, int cycle, String convention,
                              double rate, List<SyntheticDsc.Row> rows) {
        TraceImport ti = new TraceImport();
        ti.segmentId = id == null ? Ids.newDecisionId("seg") : id;
        ti.sourceFile = file;
        ti.cycle = cycle;
        ti.conventionRaw = convention;
        ti.declaredRate = rate;
        for (SyntheticDsc.Row r : rows) ti.rows.add(new TraceImport.Row(r.t, r.hf, null));
        return ti;
    }

    private String region(String tag, String segId, String kind, double lo, double hi, String label) {
        return Ids.derived("regdef", tag, segId, kind,
                dsc.core.Units.fmt(lo), dsc.core.Units.fmt(hi));
    }

    public record StoryStep(String key, String narration, List<String> subjects,
                            long checkpointSeq) {
        public StoryStep(String key, String narration, List<String> subjects) {
            this(key, narration, subjects, -1);
        }
    }

    public record HistoryStage(long massCheckpoint, long amendCheckpoint, long decisionCheckpoint,
                               List<String> regionIds) {}
}
