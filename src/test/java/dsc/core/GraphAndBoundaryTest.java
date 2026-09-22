package dsc.core;

import dsc.casepack.ScenarioRunner;
import dsc.domain.BaselineResult;
import dsc.domain.EnthalpyResult;
import dsc.domain.TransitionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 边界语义：空区间、循环段切换、质量版本过期、并列基线、冲突裁定、
 * 依赖图局部传播与时间旅行。
 */
class GraphAndBoundaryTest {

    private ProjectService seeded() {
        ProjectService svc = new ProjectService();
        new ScenarioRunner(svc).runAll();
        return svc;
    }

    @Test
    void emptyIntervalProducesRetainedErrorCandidate() {
        ProjectService svc = seeded();
        boolean hasEmpty = svc.baselines("seg_heat2").stream()
                .anyMatch(b -> "ERROR".equals(b.status)
                        && b.diagnostic != null && b.diagnostic.contains("EMPTY_INTERVAL"));
        assertTrue(hasEmpty, "empty interval candidate must be retained with status, not dropped");
        boolean eventRecorded = svc.eventLog().stream()
                .anyMatch(e -> ProjectEvent.BOUNDARY_TRIGGERED.equals(e.type)
                        && "EMPTY_INTERVAL".equals(e.payload.get("scenario")));
        assertTrue(eventRecorded, "input fragment/status/affected objects must be recorded");
    }

    @Test
    void crossSegmentIntegrationIsRejected() {
        // 直接构造跨段区间：段范围之外的切线 → CROSSES_SEGMENTS 不可能通过基线，
        // 但焓积分对超界边界返回 CROSSES_SEGMENTS（这里用段切换事件验证记录路径）。
        ProjectService svc = seeded();
        boolean switchRecorded = svc.eventLog().stream()
                .anyMatch(e -> ProjectEvent.SEGMENT_SWITCH.equals(e.type)
                        && "seg_heat2".equals(e.payload.get("fromSegmentId"))
                        && "seg_cool1".equals(e.payload.get("toSegmentId")));
        boolean crossRejected = svc.eventLog().stream()
                .anyMatch(e -> ProjectEvent.BOUNDARY_TRIGGERED.equals(e.type)
                        && "CROSSES_SEGMENTS".equals(e.payload.get("scenario"))
                        && "REJECTED".equals(e.payload.get("status")));
        assertTrue(switchRecorded && crossRejected);
    }

    @Test
    void massRevisionExpiresNormalizedDeriveds() {
        ProjectService svc = seeded();
        // 质量修订事件后，STALE_PROPAGATED 必须列出曲线/焓等归一化派生
        List<ProjectEvent> stale = svc.eventLog().stream()
                .filter(e -> ProjectEvent.STALE_PROPAGATED.equals(e.type)
                        && "MASS_REVISED".equals(e.payload.get("reason"))).toList();
        assertFalse(stale.isEmpty());
        @SuppressWarnings("unchecked")
        List<String> affected = (List<String>) stale.get(0).payload.get("affectedNodes");
        assertTrue(affected.stream().anyMatch(n -> n.contains("segment_curve")));
        assertTrue(affected.stream().anyMatch(n -> n.contains("enthalpy")));

        // v2 质量版本存在且旧值仍可追溯
        assertEquals(2, svc.graph().getVersion(ProjectService.node(GraphNodeKind.SRC_MASS,
                "mass_sample"), 2).version);
    }

    @Test
    void calibrationChangeIsLocal() {
        ProjectService svc = seeded();
        List<ProjectEvent> stale = svc.eventLog().stream()
                .filter(e -> ProjectEvent.STALE_PROPAGATED.equals(e.type)
                        && "CALIBRATION_REVISED".equals(e.payload.get("reason"))).toList();
        assertFalse(stale.isEmpty());
        @SuppressWarnings("unchecked")
        List<String> affected = (List<String>) stale.get(0).payload.get("affectedNodes");
        assertTrue(affected.stream().anyMatch(n -> n.contains("baseline") || n.contains("transition")));
    }

    @Test
    void conflictingHumanDecisionsAreBothRetained() {
        ProjectService svc = seeded();
        long accepts = svc.eventLog().stream()
                .filter(e -> ProjectEvent.CANDIDATE_ACCEPTED.equals(e.type)).count();
        long conflicts = svc.eventLog().stream()
                .filter(e -> ProjectEvent.CONFLICTING_DECISION.equals(e.type)).count();
        assertTrue(accepts >= 2);
        assertEquals(1, conflicts, "rejecting an accepted candidate logs a conflict, never overwrites");
    }

    @Test
    void timeTravelRestoresPriorConclusions() {
        ProjectService svc = seeded();
        long total = svc.eventLog().size();
        // 找到质量修订之前的序号
        long massRevisedSeq = svc.eventLog().stream()
                .filter(e -> ProjectEvent.MASS_REVISED.equals(e.type)).findFirst().orElseThrow().seq;
        ProjectService oldView = svc.viewAt(massRevisedSeq - 1);
        assertNull(oldView.segments.get("seg_heat2"), "second cycle did not exist before revision");
        assertEquals(1, oldView.masses.get("mass_sample").massVersion);
        // 当前视图不受影响
        assertNotNull(seeded().segments.get("seg_heat2"));
        assertTrue(total > massRevisedSeq);
    }

    @Test
    void explainReportsValidityAndRecomputeReason() {
        ProjectService svc = seeded();
        DependencyGraph.Explanation valid = svc.graph()
                .explain(ProjectService.node(GraphNodeKind.SRC_INSTRUMENT, "out_nonuniform"));
        assertTrue(valid.valid);
        DependencyGraph.Explanation staleOrFresh = svc.graph()
                .explain(ProjectService.node(GraphNodeKind.DERIVED_ENTHALPY,
                        svc.enthalpies("seg_heat1").get(0).integrationId));
        assertNotNull(staleOrFresh.headline);
    }

    @Test
    void badRawPointIsKeptInPlace() {
        ProjectService svc = seeded();
        assertEquals(1, svc.segments.get("seg_heat1_mw").badPoints.size());
        assertEquals("NA_SENSOR_DROP", svc.segments.get("seg_heat1_mw").badPoints.get(0).rawHeatFlow);
    }

    @Test
    void reversedHeatFlowFlagIsRecorded() {
        ProjectService svc = seeded();
        assertTrue(svc.reports.get("seg_cool1").reversedHeatFlow);
        assertEquals("exo-up", svc.segments.get("seg_cool1").instrumentSign);
    }
}
