package dsc.domain;

/** Machine-readable event taxonomy of the review history. */
public enum EventType {
    SOURCE_INGESTED,      // a direct source was archived (instrument output, mass, rate, atmosphere, calibration, tangent)
    SOURCE_PATCHED,       // a new version of one direct source (only local dependents go stale)
    SEGMENT_REGISTERED,   // heating/cooling/isothermal segment declared from instrument metadata
    BASELINE_DERIVED,     // a baseline candidate version produced
    TRANSITION_DERIVED,   // onset/peak/endset + enthalpy produced
    MASS_REVISED,         // mass version changed -> all normalized derivations expire
    DECISION_MADE,        // accept / reject / continue-version on a candidate
    DECISION_CONFLICT,    // two users edited from the same revision; conflict recorded, resolvable
    DIAGNOSTIC_RAISED,    // empty range, unconvertible value, sign ambiguity, non-uniform grid, overlap ...
    REPLAY_MARKER,        // deterministic scenario replay bookkeeping
    EXPORT, IMPORT        // provenance boundaries
}
