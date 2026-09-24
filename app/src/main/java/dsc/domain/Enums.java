package dsc.domain;

public final class Enums {
    private Enums() {}

    /** Which way the curve runs with respect to temperature. Never inferred from a file name. */
    public enum Direction { HEATING, COOLING, UNKNOWN }

    /** Declared instrument sign convention. */
    public enum SignConvention { EXO_UP, EXO_DOWN, UNKNOWN }

    public enum SourceKind { INSTRUMENT_OUTPUT, MASS, SCAN_RATE, ATMOSPHERE, TEMPERATURE_CALIBRATION, TANGENT }

    public enum SegmentRole { HEAT, COOL, HOLD, UNKNOWN }

    public enum EventKind {
        SAMPLE_CREATED, SOURCE_ARCHIVED, DERIVED, BASELINE_CANDIDATE,
        DECISION, PARAM_REVISION, SOURCE_PATCH, BRANCH_FORK, BRANCH_SELECTED,
        POLICY_VERDICT, REPLAY_CHECKPOINT, EXPORT, IMPORT, EMPTY_TRASH, CONFLICT_RESOLVED
    }

    public enum DerivKind { NORMALIZED_CURVE, BASELINE, TRANSITION, ANOMALY }

    public enum CandidateStatus { CANDIDATE, ACCEPTED, REJECTED, SUPERSEDED }

    /** Machine-readable policy verdicts (the "判断依据" events). */
    public enum PolicyRule {
        SIGN_NOT_FROM_FILENAME,
        HEATING_COOLING_NOT_MIXED,
        KEEP_ALL_BASELINE_CANDIDATES,
        MASS_REVISION_INVALIDATES_NORMALIZED,
        UNRESOLVED_KEEP_ALL,
        NO_MIXED_SEGMENT_FOR_INTERVAL,
        EMPTY_INTERVAL,
        REVERSED_HEATFLOW_SUSPECT,
        NONUNIFORM_TEMPERATURE
    }

    public enum Verdict { PASS, VIOLATION, SUSPECT, INDETERMINATE }

    public enum Severity { INFO, WARN, ERROR }
}
