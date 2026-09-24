package dsc.domain;

/** 机器可读的判定依据事件类别。 */
public enum EventType {
    CASE_OPENED,
    SEGMENT_IMPORTED,
    MASS_RECORDED,
    ATMOSPHERE_RECORDED,
    TEMP_CALIBRATION_RECORDED,
    HF_CALIBRATION_RECORDED,
    TANGENT_SELECTED,
    WINDOW_CREATED,
    WINDOW_MOVED,
    BASELINE_PROPOSED,
    DECISION_MADE,            // 采纳 / 否定 / 参数调整
    SOURCE_REVISED,           // 只影响局部依赖的源数据改动
    CONFLICT_RESOLVED,
    RULE_VIOLATION,           // 语义判定依据
    ANOMALY_FLAGGED,
    REPLAY_STEP,
    NOTE
}
