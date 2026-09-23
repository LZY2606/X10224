package dsc.model;

/** Severity and machine-readable code for a diagnostic attached to a node. */
public record Diagnostic(String code, String severity, String message, String nodeId, String sourceId) {

    public static final String REVERSED_HEATFLOW = "REVERSED_HEATFLOW";
    public static final String NON_UNIFORM_TEMPERATURE = "NON_UNIFORM_TEMPERATURE";
    public static final String OVERLAPPING_TRANSITIONS = "OVERLAPPING_TRANSITIONS";
    public static final String EMPTY_INTERVAL = "EMPTY_INTERVAL";
    public static final String CYCLE_BOUNDARY = "CYCLE_BOUNDARY";
    public static final String MULTIPLE_BASELINES = "MULTIPLE_BASELINES";
    public static final String DERIVED_STALE = "DERIVED_STALE";
    public static final String UNPARSEABLE_CELL = "UNPARSEABLE_CELL";
    public static final String UNKNOWN_UNIT = "UNKNOWN_UNIT";
    public static final String DIRECTION_MISMATCH = "DIRECTION_MISMATCH";
    public static final String CONFLICTING_DECISION = "CONFLICTING_DECISION";
    public static final String SIGN_CONVENTION_MISMATCH = "SIGN_CONVENTION_MISMATCH";
}
