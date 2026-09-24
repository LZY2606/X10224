package io.dsc.model;

/**
 * Machine-readable event vocabulary. Each semantic judgment in the archive is
 * one of these codes with a structured payload, so reviewers (and tests) can
 * replay decisions without parsing prose.
 */
public enum EventType {
    CASE_CREATED,
    SOURCE_ARCHIVED,        // instrument output / mass / rate / atmosphere / calibration / tangent stored separately
    SOURCE_AMENDED,         // localised source change, scope limited by dependency graph
    INTERVAL_DRAWN,         // reviewer dragged an interval on the curve page
    BASELINES_COMPUTED,     // derivation: one or more parallel baseline candidates
    BASELINE_ADOPTED,       // reviewer accepted a candidate (others remain, not deleted)
    BASELINE_REJECTED,
    TRANSITION_COMPUTED,    // derivation: onset/peak/endset + enthalpy + error sources
    TRANSITION_ACCEPTED,
    TRANSITION_REJECTED,
    MASS_REVISED,           // mass version changes -> all normalised derivations expire
    PARAMS_REVISED,         // e.g. analysis parameters, scope limited
    BRANCH_CREATED,         // conflicting human decision / comparison branch
    CONFLICT_RESOLVED,      // choose which branch continues; losing branch retained
    REPLAYED,               // emitted by deterministic case replay
    NOTE
}
