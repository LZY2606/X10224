package dsc.domain;

/**
 * A machine-readable explanation attached to an input fragment, a derivation,
 * or a review decision. {@code code} is a stable identifier used by tests and
 * by the UI to link an anomaly back to its source chain; {@code severity}
 * distinguishes hard refusal from review warnings.
 */
public record Diagnostic(Severity severity, Code code, String message, String subjectId) {

    public enum Severity { INFO, WARNING, ERROR }

    public enum Code {
        UNPARSEABLE_CELL,
        UNKNOWN_UNIT,
        UNKNOWN_HEATFLOW_CONVENTION,
        NON_MONOTONIC_PROGRESSION,
        DIRECTION_MISMATCH,
        NON_UNIFORM_SPACING,
        EMPTY_INTERVAL,
        WINDOW_OUTSIDE_SEGMENT,
        WINDOW_TOO_SPARSE,
        OVERLAPPING_TRANSITIONS,
        MIXED_SEGMENT_DIRECTION,
        MASS_NOT_NORMALIZABLE,
        MASS_VERSION_STALE,
        OVERLAPPING_CANDIDATES,
        NO_LEGAL_RESULT,
        CONFLICTING_DECISIONS,
        CYCLE_SWITCH
    }
}
