package org.materials.dsc.domain;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 把单个事件应用到 {@link ProjectState}。纯函数：不写库、不产生随机值。
 * 导入/回放/时间旅行都走这里，保证“回到旧事件”恢复的是当时依赖版本与结论。
 */
public final class ProjectApplier {

    private final ObjectMapper mapper;

    public ProjectApplier(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void apply(ProjectState state, DscEvent event) {
        switch (event.type()) {
            case DscEvent.PROJECT_CREATED -> {
                Payloads.ProjectCreated p = convert(event, Payloads.ProjectCreated.class);
                state.projectId = p.projectId();
                state.ruleVersion = p.ruleVersion();
            }
            case DscEvent.RUN_IMPORTED -> {
                Payloads.RunImported p = convert(event, Payloads.RunImported.class);
                state.samples.putIfAbsent(p.sample().id(), p.sample());
                state.runs.put(p.run().id(), p.run());
            }
            case DscEvent.SOURCE_PATCHED -> {
                Payloads.SourcePatched p = convert(event, Payloads.SourcePatched.class);
                InstrumentRun old = requireRun(state, p.runId());
                List<String> diag = new ArrayList<>(old.diagnostics());
                String note = "source-patched@event" + event.seq() + ": " + p.reason()
                        + " [" + p.oldFingerprint().substring(0, Math.min(10, p.oldFingerprint().length()))
                        + " -> " + p.newFingerprint().substring(0, Math.min(10, p.newFingerprint().length())) + "]";
                diag.add(note);
                InstrumentRun patched = new InstrumentRun(old.id(), old.sampleId(), old.fileName(),
                        List.copyOf(p.replacedPoints()), old.scanRate(), old.atmosphere(), old.exoUp(),
                        old.directionSource(), old.createdAtIso(), List.copyOf(diag));
                state.runs.put(old.id(), patched);
            }
            case DscEvent.MASS_REVISED -> {
                Payloads.MassRevised p = convert(event, Payloads.MassRevised.class);
                state.masses.put(p.mass().id(), p.mass());
            }
            case DscEvent.CALIBRATION_REVISED -> {
                Payloads.CalibrationRevised p = convert(event, Payloads.CalibrationRevised.class);
                state.calibrations.put(p.calibration().id(), p.calibration());
            }
            case DscEvent.SEGMENT_REGISTERED -> {
                Payloads.SegmentRegistered p = convert(event, Payloads.SegmentRegistered.class);
                state.explicitSegments.put(p.segment().id(), p.segment());
            }
            case DscEvent.TANGENT_SELECTED -> {
                Payloads.TangentSelected p = convert(event, Payloads.TangentSelected.class);
                state.tangents.put(p.tangent().id(), p.tangent());
            }
            case DscEvent.WINDOW_DRAGGED -> {
                Payloads.WindowDragged p = convert(event, Payloads.WindowDragged.class);
                state.windows.put(p.window().id(), p.window());
                state.windowMeta.put(p.window().id(),
                        new ProjectState.WindowMeta(p.familyHint(), p.overlapFlag()));
            }
            case DscEvent.BASELINE_ADJUDICATED -> {
                Payloads.BaselineAdjudicated p = convert(event, Payloads.BaselineAdjudicated.class);
                if ("accept".equals(p.action())) {
                    state.acceptedBaselines.put(p.windowId(), p.candidateId());
                    state.rejectedBaselines.remove(p.candidateId());
                } else if ("reject".equals(p.action())) {
                    state.rejectedBaselines.put(p.candidateId(),
                            "rejected@event" + event.seq() + " by " + p.actor() + ": " + p.note());
                } else if ("revoke".equals(p.action())) {
                    state.acceptedBaselines.remove(p.windowId());
                }
            }
            case DscEvent.REPLAY_STEP -> {
                // 案例重放标记事件：实际改动在其同批的其他事件里。
            }
            default -> throw new IllegalArgumentException("unknown event type: " + event.type());
        }
    }

    private InstrumentRun requireRun(ProjectState state, String runId) {
        InstrumentRun run = state.runs.get(runId);
        if (run == null) {
            throw new IllegalStateException("patch on unknown run: " + runId);
        }
        return run;
    }

    private <T> T convert(DscEvent event, Class<T> type) {
        return mapper.convertValue(event.payload(), type);
    }
}
