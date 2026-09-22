package org.materials.dsc.domain;

import java.util.List;

/**
 * 机器可读的事件。任何接受/否定/参数调整都追加新事件，旧事件不可覆盖。
 * payload 携带领域对象的完整 JSON；affectedObjects/inputFragment/diagnostics
 * 让审阅者能反查“哪条材料、哪版规则、哪次决定”造成差异。
 */
public record DscEvent(
        long seq,
        String id,
        String type,
        String actor,
        String timestampIso,
        String summary,
        String inputFragment,
        List<String> affectedObjects,
        List<String> diagnostics,
        /** 乐观并发基线：追加该事件前状态中已有事件数。 */
        long baseRevision,
        /** 规则版本（写入时快照）。 */
        String ruleVersion,
        Object payload) {

    public static final String PROJECT_CREATED = "project-created";
    public static final String RUN_IMPORTED = "run-imported";
    public static final String SOURCE_PATCHED = "source-patched";
    public static final String MASS_REVISED = "mass-revised";
    public static final String CALIBRATION_REVISED = "calibration-revised";
    public static final String SEGMENT_REGISTERED = "segment-registered";
    public static final String TANGENT_SELECTED = "tangent-selected";
    public static final String WINDOW_DRAGGED = "window-dragged";
    public static final String BASELINE_ADJUDICATED = "baseline-adjudicated";
    public static final String REPLAY_STEP = "replay-step";
}
