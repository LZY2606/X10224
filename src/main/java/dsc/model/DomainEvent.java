package dsc.model;

import java.util.Map;

public record DomainEvent(
        String eventId,
        String branch,
        long seq,
        String type,
        String actor,
        String clientVersion,
        String at,
        Map<String, Object> payload,
        String derivedFromEventId
) {
    public static final String PROJECT_OPENED = "project-opened";
    public static final String BLOB_ARCHIVED = "blob-archived";
    public static final String SEGMENT_IMPORTED = "segment-imported";
    public static final String MASS_SET = "mass-set";
    public static final String CALIBRATION_SET = "calibration-set";
    public static final String ATMOSPHERE_SET = "atmosphere-set";
    public static final String TANGENT_PICKED = "tangent-picked";
    public static final String INTERVAL_DRAWN = "interval-drawn";
    public static final String INTERVAL_ADJUSTED = "interval-adjusted";
    public static final String BASELINE_DERIVED = "baseline-derived";
    public static final String EVENT_DERIVED = "event-derived";
    public static final String INTEGRATION_DERIVED = "integration-derived";
    public static final String DIAGNOSTIC_RAISED = "diagnostic-raised";
    public static final String DIAGNOSTIC_RESOLVED = "diagnostic-resolved";
    public static final String JUDGMENT_APPENDED = "judgment-appended";
    public static final String DECISION_MADE = "decision-made";
    public static final String BRANCH_CREATED = "branch-created";
    public static final String CURRENT_POINTER_SET = "current-pointer-set";
    public static final String REPLAY_MARKER = "replay-marker";
}
