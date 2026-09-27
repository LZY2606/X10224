package dsc.domain;

public enum EventType {
    // direct sources, each archived independently
    SAMPLE_REGISTERED,
    RAW_BATCH_IMPORTED,       // instrument output (segment + points)
    MASS_SET,                 // sample mass revision
    RATE_SET,                 // scan rate revision
    ATMOSPHERE_SET,           // atmosphere revision
    CALIBRATION_SET,          // temperature calibration revision
    TANGENT_SET,              // user-selected tangent revision
    SEGMENT_DIRECTION_DETERMINED, // direction derived from data, not filename

    // analysis workflow
    INTERVAL_DRAWN,
    CROSS_SEGMENT_REJECTED,
    EMPTY_INTERVAL_CHECKED,
    BASELINES_COMPUTED,
    AMBIGUOUS_BASELINES,
    TRANSITION_COMPUTED,
    OVERLAP_DETECTED,

    // rulings
    BASELINE_ADOPTED,
    BASELINE_REJECTED,
    PARAMETER_ADJUSTED,
    CONFLICTING_DECISION,

    // provenance / lifecycle
    DERIVATION_EXPIRED,
    CONFLICT_DETECTED,
    CASE_NOTE
}
