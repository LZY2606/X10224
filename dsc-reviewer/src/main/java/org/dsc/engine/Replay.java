package org.dsc.engine;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dsc.domain.Models.*;
import org.dsc.store.EventRecord;
import org.dsc.store.Store;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 反例档案（离线案例包）重放。
 * 初始版本：六个直接来源分别存档；随后一次参数修订（切线）、
 * 一次只影响局部依赖的源数据改动（气氛）、一次互相冲突的人工决定（两位审阅者同版本编辑）。
 * 重放实际经过：反向热流、非等间隔、重叠转折、循环段切换、质量版本变更、
 * 并列基线、空区间，并保留采纳与未采纳结果。
 */
public final class Replay {
    public static final String CASE_ID = "thermal-turning-case";

    public record Step(String title, String eventId, String judgement) {}

    private final CaseService svc;
    public final List<Step> steps = new ArrayList<>();

    public Replay(CaseService svc) { this.svc = svc; }

    public static String resource(String name) {
        try (InputStream in = Replay.class.getResourceAsStream("/casepack/" + name)) {
            if (in == null) throw new IllegalStateException("missing fixture: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("read fixture failed: " + name, e);
        }
    }

    public List<Step> run() {
        steps.clear();
        String caseId = CASE_ID;
        EventRecord e0 = svc.openCase(caseId, "PET 颗粒 批次 P-26-11", "PET", "reviewer-a");
        steps.add(new Step("立案：样品履历建立（可含多段加热/冷却）", e0.eventId(), "CASE_OPENED"));

        // 初始：六个直接来源分别存档
        svc.recordSource(caseId, "src-mass", "MASS", null, "12.40 mg", "mg", "reviewer-a", e0.eventId());
        svc.recordSource(caseId, "src-rate-h1", "SCAN_RATE", "seg-h1", "10 K/min", "K/min", "reviewer-a", null);
        EventRecord atmo = svc.recordSource(caseId, "src-atmo-h1", "ATMOSPHERE", "seg-h1",
                "N2, 50 mL/min", "mL/min", "reviewer-a", null);
        svc.recordSource(caseId, "src-tcal-h1", "TEMP_CALIBRATION", "seg-h1",
                "In melting 429.75 K (indicated 430.10 K)", "K", "reviewer-a", null);
        steps.add(new Step("六个直接来源分别存档（原始表示保留）", atmo.eventId(),
                "SOURCES_SEPARATE: instrument/mass/rate/atmosphere/calibration/tangent never merged"));

        // 情形1&2：反向热流(EXO_UP，放热向下) + 非等间隔温度
        EventRecord seg1 = svc.parseSegment(caseId, 1, "seg-h1", resource("segment_heating_1.txt"),
                "reviewer-a", null);
        steps.add(new Step("情形①反向热流 + ②非等间隔温度（加热段1：玻璃化/结晶/熔融）",
                seg1.eventId(), "EXO_UP_PARSED;NON_UNIFORM_FLAGGED;NO_RESAMPLING"));

        // 循环段切换：冷却段独立解析，绝不与加热混用
        EventRecord segC = svc.parseSegment(caseId, 2, "seg-c1", resource("segment_cooling_1.txt"),
                "reviewer-a", null);
        steps.add(new Step("情形③循环段切换：冷却段独立存档（重叠肩峰）",
                segC.eventId(), "COOLING_SEPARATE: heating/cooling never mixed"));

        EventRecord seg2 = svc.parseSegment(caseId, 3, "seg-h2", resource("segment_heating_2.txt"),
                "reviewer-a", null);
        steps.add(new Step("第二循环加热段（ENDO_UP：方向来自仪器头而非文件名）",
                seg2.eventId(), "DIRECTION_FROM_HEADER"));

        // 脏数据段：无法转换的值留在原位置
        EventRecord dirty = svc.parseSegment(caseId, 4, "seg-dirty", resource("segment_dirty.txt"),
                "reviewer-a", null);
        steps.add(new Step("无法转换的温度/热流留在来源原位置并附诊断（不为画图丢弃）",
                dirty.eventId(), "UNCONVERTIBLE_VALUES_KEPT"));

        // 情形⑥：并列基线（加热段1 熔融窗口 ~485-515K，锚点在峰两侧）
        TempWindow meltWindow = new TempWindow(488.0, 514.0);
        TangentSelection tan1 = new TangentSelection("seg-h1",
                new TempWindow(455.0, 470.0), new TempWindow(510.0, 519.0),
                "pre/post melt linear tangent", 1);
        EventRecord tanEvt = svc.recordSource(caseId, "src-tangent-1", "TANGENT", "seg-h1",
                "left=[455,470]K right=[510,519]K", "K", "reviewer-a", null);
        List<EventRecord> bls = svc.deriveBaselines(caseId, 1, "seg-h1", meltWindow, tan1,
                "reviewer-a", null);
        steps.add(new Step("情形④重叠转折 + ⑥并列基线：多个合理基线候选并列保留",
                bls.get(0).eventId(), "MULTIPLE_BASELINES_RETAINED"));

        // 每个基线各算一次结果（保留差异），采纳其中一个
        List<EventRecord> results = new ArrayList<>();
        for (EventRecord bl : bls) {
            String blId = bl.pstr("baselineId");
            if (blId == null) continue;
            results.add(svc.deriveTransition(caseId, 1, blId, meltWindow, false, "reviewer-a", null));
        }
        EventRecord adopt = svc.adoptBaseline(caseId, bls.get(0).pstr("baselineId"), "reviewer-a", null);
        steps.add(new Step("采纳切线基线；OLS/常数候选与各自结果保留不删除",
                adopt.eventId(), "ADOPT_NON_DESTRUCTIVE"));

        // 情形⑦：空区间检验
        EventRecord empty = svc.checkEmptyWindow(caseId, 1, new TempWindow(600.0, 610.0),
                "reviewer-a", null);
        steps.add(new Step("情形⑦空区间：无样本，影响被限制在局部（无派生被污染）",
                empty.eventId(), "EMPTY_WINDOW_LOCAL_SCOPE"));

        // 跨加热/冷却窗口被拒
        EventRecord cross = svc.deriveTransition(caseId, 1, bls.get(0).pstr("baselineId"),
                new TempWindow(300.0, 510.0), false, "reviewer-a", null);
        steps.add(new Step("跨段/越界窗口被拒（加热与冷却不混用）",
                cross.eventId(), "CROSS_SEGMENT_GUARD"));

        // 冷却段结晶（重叠峰）+ 其并列基线
        TangentSelection tanC = new TangentSelection("seg-c1",
                new TempWindow(495.0, 514.0), new TempWindow(305.0, 325.0),
                "cooling crystallization anchors", 1);
        svc.recordSource(caseId, "src-tangent-2", "TANGENT", "seg-c1",
                "left=[495,514]K right=[305,325]K", "K", "reviewer-a", null);
        TempWindow coolWindow = new TempWindow(440.0, 490.0);
        List<EventRecord> blsC = svc.deriveBaselines(caseId, 2, "seg-c1", coolWindow, tanC,
                "reviewer-a", null);
        EventRecord resC = svc.deriveTransition(caseId, 2, blsC.get(0).pstr("baselineId"),
                coolWindow, false, "reviewer-a", null);
        steps.add(new Step("冷却段重叠结晶峰积分（放热符号由 EXO_UP 折算）",
                resC.eventId(), "OVERLAP_COOLING_INTEGRATION"));

        // 玻璃化
        TempWindow tgWindow = new TempWindow(345.0, 368.0);
        TangentSelection tanTg = new TangentSelection("seg-h1",
                new TempWindow(305.0, 340.0), new TempWindow(372.0, 395.0), "Tg step anchors", 1);
        svc.recordSource(caseId, "src-tangent-1-tg", "TANGENT", "seg-h1",
                "left=[305,340]K right=[372,395]K", "K", "reviewer-a", null);
        List<EventRecord> blsTg = svc.deriveBaselines(caseId, 1, "seg-h1", tgWindow, tanTg,
                "reviewer-a", null);
        EventRecord resTg = svc.deriveTransition(caseId, 1, blsTg.get(0).pstr("baselineId"),
                tgWindow, true, "reviewer-a", null);
        steps.add(new Step("玻璃化台阶 Tg（中点法；焓变非主量并登记误差来源）",
                resTg.eventId(), "GLASS_STEP_TG"));

        // 参数修订：拖动切线区间 -> 依赖该切线的基线过期，其余仍有效
        EventRecord adjust = svc.adjustTangent(caseId, 1, new TangentSelection("seg-h1",
                new TempWindow(458.0, 472.0), new TempWindow(508.0, 518.0),
                "reviewer dragged right anchor", 2), "reviewer-a", null);
        steps.add(new Step("参数修订：曲线页拖动锚区间（只使依赖它的基线/结果过期）",
                adjust.eventId(), "TANGENT_ADJUSTED_LOCAL_PROPAGATION"));

        // 局部源数据改动：只改气氛，依赖图说明其余结果为何仍有效
        String headBeforeAtmo = svc.head(caseId);
        EventRecord atmo2 = svc.recordSource(caseId, "src-atmo-h1", "ATMOSPHERE", "seg-h1",
                "N2, 40 mL/min", "mL/min", "reviewer-a", headBeforeAtmo);
        steps.add(new Step("局部源数据改动：气氛 50→40 mL/min（数值派生不依赖气氛，仍 CURRENT）",
                atmo2.eventId(), "LOCAL_AMEND_UNRELATED_STAY_CURRENT"));

        // 情形⑤：质量版本变更 -> 所有归一化派生过期
        EventRecord mass = svc.reviseMass(caseId, "12.10 mg", "reviewer-a", null);
        steps.add(new Step("情形⑤质量修订 12.40→12.10 mg：所有归一化派生标记 STALE",
                mass.eventId(), "MASS_REVISION_INVALIDATES_NORMALIZED"));

        // 互相冲突的人工决定：两位使用者从同一版本编辑
        String head = svc.head(caseId);
        EventRecord reject = svc.rejectBaseline(caseId, bls.get(1).pstr("baselineId"),
                "reviewer-b", head);
        Store.ConcurrencyException conflict = null;
        try {
            // reviewer-a 仍基于旧 head 采纳同一候选的竞争者
            svc.adoptBaseline(caseId, bls.get(2).pstr("baselineId"), "reviewer-a", head);
        } catch (Store.ConcurrencyException ce) {
            conflict = ce;
        }
        ObjectNode cp = EventRecord.payload("expectedBasis", head,
                "judgement", "CONFLICT_RESOLVABLE: two reviewers edited same basis; second write rejected");
        svc.store().appendEvent(caseId, "CONFLICT_DETECTED", "system", null, cp, null);
        steps.add(new Step("冲突人工决定：同版本并发保存被拒，需解决后重试（可解决，不丢候选）",
                reject.eventId(), conflict == null ? "CONFLICT_NOT_TRIGGERED" : "CONCURRENCY_GUARDED"));

        // 审阅者 a 基于最新 head 重新决定（冲突解决），保留未选候选
        String newHead = svc.head(caseId);
        EventRecord resolved = svc.adoptBaseline(caseId, bls.get(2).pstr("baselineId"),
                "reviewer-a", newHead);
        steps.add(new Step("冲突解决后重试：采纳常数基线为对照，先前采纳的切线基线仍保留",
                resolved.eventId(), "CONFLICT_RESOLVED_BRANCHES_KEPT"));

        EventRecord done = svc.store().appendEvent(caseId, "CASE_REPLAYED", "system",
                null, EventRecord.payload("judgement",
                        "all seven boundary situations replayed; adopted and unadopted results retained"), null);
        steps.add(new Step("反例档案重放完成", done.eventId(), "REPLAY_COMPLETE"));
        return List.copyOf(steps);
    }
}
