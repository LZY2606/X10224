package io.dsc.model;

/**
 * Canonical diagnostic codes used by rules, archive and UI. Keeping them in one
 * place makes replay assertions and machine-readable events unambiguous.
 */
public final class DiagnosticCodes {
    private DiagnosticCodes() {}

    // instrument output vs file name
    public static final String REVERSE_HEATFLOW_SIGN = "reverse-heatflow-sign";
    public static final String FILENAME_DIRECTION_MISMATCH = "filename-direction-mismatch";

    // sampling
    public static final String NONUNIFORM_TEMPERATURE_GRID = "nonuniform-temperature-grid";
    public static final String UNCONVERTIBLE_POINT = "unconvertible-point";

    // interval geometry
    public static final String EMPTY_INTERVAL = "empty-interval";
    public static final String CROSSES_SEGMENT_BOUNDARY = "crosses-segment-boundary";
    public static final String CYCLE_SWITCH_NOT_MIXED = "cycle-switch-not-mixed";

    // transitions
    public static final String OVERLAPPING_TRANSITIONS = "overlapping-transitions";
    public static final String PARALLEL_BASELINES = "parallel-baselines";

    // validity
    public static final String MASS_NORMALISATION_EXPIRED = "mass-normalisation-expired";
    public static final String UNRESOLVED_AMBIGUITY = "unresolved-ambiguity";
    public static final String UNCONVERTIBLE_QUANTITY = "unconvertible-quantity";
}
