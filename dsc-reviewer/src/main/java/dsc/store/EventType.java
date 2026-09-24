package dsc.store;

/**
 * Machine-readable event kinds. Every analyst action and every automated
 * verdict is one of these; the review UI reconstructs any historical view by
 * replaying the prefix ending at a chosen event.
 */
public enum EventType {
    SAMPLE_CREATED,
    RAW_ARCHIVED,        // instrument output fragment stored verbatim
    METADATA_ARCHIVED,   // mass / scan rate / atmosphere / temperature calibration
    TANGENT_ARCHIVED,    // analyst-selected tangents, as their own source record
    DIRECTION_VERDICT,   // heating/cooling & endo/exo judgement
    SEGMENT_SPLIT,       // non-monotonic fragment split at the reversal point
    BASELINE_DERIVED,    // one recomputed baseline candidate (multiplicity kept)
    TRANSITION_DERIVED,  // onset/peak/endset + enthalpy candidate
    MASS_REVISED,        // new mass version -> normalised derivations go stale
    SOURCE_PATCHED,      // one direct source changed (local dependency impact only)
    DECISION_ACCEPTED,   // candidate chosen by a reviewer
    DECISION_REJECTED,   // candidate kept but not current
    DECISION_CONFLICT,   // two reviewers from the same base version diverged
    CONFLICT_RESOLVED,
    INTERVAL_DRAGGED,    // curve-page window adjustment
    PARAM_ADJUSTED,      // tangent/type parameter tweak
    EMPTY_INTERVAL,
    POINTER_SWITCHED     // current-result pointer moved to a new complete version
}
