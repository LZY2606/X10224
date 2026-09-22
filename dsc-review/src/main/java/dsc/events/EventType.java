package dsc.events;

/**
 * Event-sourcing command/event vocabulary. The wording of each constant is
 * part of the on-disk contract: old databases must keep replaying after rules
 * move to newer versions, so event names are never renamed.
 */
public enum EventType {
    HISTORY_OPENED,
    TRACE_IMPORTED,
    MASS_RECORDED,
    ATMOSPHERE_RECORDED,
    CALIBRATION_RECORDED,
    RATE_DECLARED,
    REGION_DEFINED,
    TANGENT_SELECTED,
    BASELINE_DERIVED,
    FEATURES_DERIVED,
    CANDIDATE_ACCEPTED,
    CANDIDATE_REJECTED,
    PARAMETER_ADJUSTED,
    SOURCE_AMENDED,
    JUDGMENT_RECORDED
}
