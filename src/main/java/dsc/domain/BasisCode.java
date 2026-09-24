package dsc.domain;

/** Machine-readable justification codes attached to events (the "judgement basis"). */
public final class BasisCode {
    private BasisCode() {}
    public static final String SIGN_NOT_FROM_NAME   = "sign:not-from-filename";
    public static final String SIGN_HEATFLOW_CONVENTION = "sign:resolved-with-heatflow-convention";
    public static final String SEGMENTS_NOT_MIXED   = "segment:heating-cooling-never-mixed";
    public static final String MULTI_BASELINE_KEPT  = "baseline:multiple-plausible-candidates-kept";
    public static final String MASS_INVALIDATES     = "mass:revision-expires-all-normalized";
    public static final String AMBIGUITY_KEPT       = "decision:non-unique-all-legal-candidates-kept";
    public static final String EMPTY_RANGE          = "range:empty-nothing-integrated";
    public static final String NONUNIFORM_GRID      = "grid:non-uniform-temperature-detected";
    public static final String OVERLAP_DETECTED     = "transition:overlapping-events";
    public static final String CYCLE_BOUNDARY       = "segment:cycle-switch-boundary-respected";
    public static final String REVERSED_HEATFLOW    = "heatflow:reversed-sign-series";
    public static final String UNCONVERTIBLE_KEPT   = "unit:unconvertible-value-kept-in-place";
    public static final String LOCAL_PROPAGATION    = "graph:only-affected-dependents-recomputed";
    public static final String RESULT_STILL_VALID   = "graph:dependency-unchanged-result-still-valid";
    public static final String ATOMIC_SWITCH        = "store:pointer-switched-only-after-complete-commit";
    public static final String CONFLICT_RESOLVABLE  = "concurrency:same-revision-conflict-resolvable";
    public static final String HISTORY_NOT_OVERWRITTEN = "history:old-events-restore-their-own-conclusions";
}
