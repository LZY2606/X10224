package org.materials.dsc.domain;

import java.util.List;
import java.util.Map;

/** 事件载荷的领域类型。 */
public final class Payloads {

    private Payloads() {
    }

    public record ProjectCreated(String projectId, String ruleVersion) {
    }

    public record RunImported(Sample sample, InstrumentRun run) {
    }

    public record SourcePatched(String runId,
                                String reason,
                                List<SeriesPoint> replacedPoints,
                                String oldFingerprint,
                                String newFingerprint) {
    }

    public record MassRevised(MassVersion mass) {
    }

    public record CalibrationRevised(TemperatureCalibration calibration) {
    }

    public record SegmentRegistered(ScanSegment segment) {
    }

    public record TangentSelected(TangentSelection tangent) {
    }

    public record WindowDragged(Window window, String familyHint, boolean overlapFlag,
                                int dragSeq) {
    }

    /**
     * 人工裁定。action=accept 采纳一条候选；reject 明确否定；revoke 回到未裁定。
     * 冲突的人工决定各自产生事件，未选候选永远保留。
     */
    public record BaselineAdjudicated(String windowId,
                                      String candidateId,
                                      String action,
                                      String actor,
                                      String note) {
    }

    public record ReplayStep(String caseId, int stepIndex, String stepKey, Map<String, Object> detail) {
    }
}
