package dsc.domain;

/** Canonical machine-readable event types and payload keys. */
public final class EventTypes {
    private EventTypes() {}

    // direct sources archived separately
    public static final String INSTRUMENT_IMPORTED   = "source.instrument.imported";
    public static final String MASS_RECORDED         = "source.mass.recorded";
    public static final String RATE_RECORDED         = "source.rate.recorded";
    public static final String ATMOSPHERE_RECORDED   = "source.atmosphere.recorded";
    public static final String CALIBRATION_RECORDED  = "source.calibration.recorded";
    public static final String TANGENT_RECORDED      = "source.tangent.recorded";

    // derivation lifecycle
    public static final String CURVE_NORMALIZED      = "derive.curve.normalized";
    public static final String BASELINE_CANDIDATES   = "derive.baseline.candidates";
    public static final String TRANSITION_INTEGRATED = "derive.transition.integrated";

    // user review path
    public static final String INTERVAL_DRAWN        = "review.interval.drawn";
    public static final String INTERVAL_DRAGGED      = "review.interval.dragged";
    public static final String CANDIDATE_ACCEPTED    = "review.candidate.accepted";
    public static final String CANDIDATE_REJECTED    = "review.candidate.rejected";
    public static final String PARAM_ADJUSTED        = "review.parameter.adjusted";

    // revisions / invalidation
    public static final String MASS_REVISED          = "source.mass.revised";
    public static final String SOURCE_PATCHED        = "source.patched";
    public static final String DERIVED_INVALIDATED   = "derive.invalidated";

    // boundary diagnostics (the counter-example dossier)
    public static final String REVERSED_HEATFLOW     = "boundary.reversed-heatflow";
    public static final String NONUNIFORM_TEMPERATURE= "boundary.nonuniform-temperature";
    public static final String OVERLAPPING_TURNS     = "boundary.overlapping-turns";
    public static final String CYCLE_SEGMENT_SWITCH  = "boundary.cycle-segment-switch";
    public static final String MASS_VERSION_CHANGE   = "boundary.mass-version-change";
    public static final String PARALLEL_BASELINES    = "boundary.parallel-baselines";
    public static final String EMPTY_INTERVAL        = "boundary.empty-interval";

    // branching / conflicts / dossier
    public static final String BRANCH_DIVERGED       = "dossier.branch.diverged";
    public static final String BRANCH_CHOSEN         = "dossier.branch.chosen";
    public static final String CONCURRENT_CONFLICT   = "dossier.concurrent-conflict";
    public static final String CASE_REPLAY_STEP      = "dossier.case.replay-step";
}
