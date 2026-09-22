package dsc.domain;

/** Domain enumerations shared by storage, rules and the web layer. */
public final class Types {
    private Types() {
    }

    /** Thermal direction of a scan segment. Never inferred from a file name. */
    public enum Direction {
        HEATING, COOLING, UNKNOWN
    }

    /** Instrument heat-flow sign convention. UNKNOWN keeps both legal sign hypotheses. */
    public enum SignConvention {
        EXO_UP, EXO_DOWN, UNKNOWN
    }

    /** The six separately archived direct sources, plus rejected proposals. */
    public enum SourceKind {
        INSTRUMENT_OUTPUT,
        SAMPLE_MASS,
        SCAN_RATE,
        ATMOSPHERE,
        TEMPERATURE_CALIBRATION,
        TANGENT,
        REJECTED_PROPOSAL
    }

    public enum Severity {
        INFO, WARNING, ERROR
    }

    public enum DerivedKind {
        CANONICAL_SERIES,
        WINDOW,
        BASELINE,
        TRANSITION,
        ENTHALPY_REPORT
    }

    public enum EventKind {
        SAMPLE_REGISTERED,
        SEGMENT_REGISTERED,
        SOURCE_ARCHIVED,
        SOURCE_REVISED,
        SOURCE_PROPOSAL_REJECTED,
        DERIVED_RECOMPUTED,
        WINDOW_DRAWN,
        WINDOW_ADJUSTED,
        MIXED_SEGMENT_WINDOW_REJECTED,
        BASELINE_ADOPTED,
        BASELINE_REJECTED,
        DECISION_CONFLICTED,
        DECISION_RESOLVED,
        EMPTY_INTERVAL_MARKED,
        OVERLAP_DETECTED,
        REVERSE_SIGN_FLAGGED,
        NONUNIFORM_SPACING_FLAGGED,
        CASE_OPENED,
        CASE_RESET
    }

    public enum BaselineMethod {
        CHORD,
        LEADING_TANGENT,
        TRAILING_TANGENT,
        TANGENT_INTERSECTION
    }

    public enum TransitionShape {
        ENDO_PEAK,
        EXO_PEAK,
        STEP_GLASS,
        AMBIGUOUS_PEAK
    }
}
