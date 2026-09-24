package org.dscreview.model;

/** Domain enums for the DSC review application. */
public final class DomainEnums {
    private DomainEnums() {}

    /** Heating / cooling program direction. Never inferred from file names. */
    public enum ScanDirection { HEATING, COOLING, UNKNOWN }

    /** Instrument heat-flow plotting/sign convention as declared by instrument metadata. */
    public enum SignConvention { ENDO_UP, EXO_UP, UNKNOWN }

    public enum EventType { GLASS, CRYSTALLIZATION, MELTING, UNRESOLVED }

    public enum ThermalSense { ENDOTHERMIC, EXOTHERMIC, INDETERMINATE }

    public enum CandidateStatus { PROPOSED, ADOPTED, REJECTED, ALTERNATE }

    public enum ValidityCode {
        VALID,
        STALE,
        EMPTY_INTERVAL,
        INDETERMINATE_SIGN,
        DIRECTION_CONFLICT,
        OVERLAP_PRESENT,
        UNCONVERTIBLE_POINT
    }

    /** Machine-readable event kinds written to the provenance log. */
    public enum EventKind {
        CASE_LOADED,
        SOURCE_ARCHIVED,
        INTERVAL_DRAGGED,
        TANGENT_SELECTED,
        BASELINES_PROPOSED,
        CANDIDATE_ADOPTED,
        CANDIDATE_REJECTED,
        MASS_REVISED,
        SOURCE_PATCHED,
        RECOMPUTE_REPORT,
        CONFLICT_DETECTED,
        CONFLICT_RESOLVED,
        EMPTY_INTERVAL_RECORDED,
        SIGN_JUDGED,
        DIRECTION_JUDGED,
        ARCHIVE_EXPORTED,
        ARCHIVE_IMPORTED
    }
}
