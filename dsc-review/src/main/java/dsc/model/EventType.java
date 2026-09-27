package dsc.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** 机器可读取的事件类型；每个事件即一次输入片段或一次判定。 */
public enum EventType {
    BUNDLE_IMPORT("bundle_import"),
    SEGMENT_DATA("segment_data"),
    MASS_SET("mass_set"),
    MASS_REVISED("mass_revised"),
    RATE_SET("rate_set"),
    ATMOSPHERE_SET("atmosphere_set"),
    CALIBRATION_SET("calibration_set"),
    CALIBRATION_REVISED("calibration_revised"),
    TANGENT_PICKED("tangent_picked"),
    SOURCE_PATCHED("source_patched"),
    BASELINES_PROPOSED("baselines_proposed"),
    INTERVAL_DRAGGED("interval_dragged"),
    CANDIDATE_ACCEPTED("candidate_accepted"),
    CANDIDATE_REJECTED("candidate_rejected"),
    PARAMS_ADJUSTED("params_adjusted"),
    DECISION_CONFLICT("decision_conflict"),
    DECISION_RESOLVED("decision_resolved"),
    EMPTY_INTERVAL_CHECKED("empty_interval_checked");

    private final String code;

    EventType(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public static EventType fromCode(String s) {
        for (EventType t : values()) if (t.code.equals(s)) return t;
        throw new IllegalArgumentException("unknown event type: " + s);
    }
}
