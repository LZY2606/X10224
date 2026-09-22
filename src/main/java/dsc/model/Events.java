package dsc.model;

/** Machine-readable event type tags emitted by rules and decisions. */
public final class Events {
    private Events() {}

    public static final String DIRECTION_DETERMINED = "direction.determined";
    public static final String DIRECTION_UNDETERMINED = "direction.undetermined";
    public static final String FILENAME_HINT_IGNORED = "direction.filename_hint_ignored";
    public static final String HEATFLOW_REVERSED = "heatflow.reversed";
    public static final String TEMPERATURE_NONUNIFORM = "temperature.nonuniform";
    public static final String TRANSITION_OVERLAP = "transition.overlap";
    public static final String CYCLE_SWITCH = "cycle.switch";
    public static final String SEGMENT_MIX_REJECTED = "segment.mix.rejected";
    public static final String MASS_REVISED = "mass.revised";
    public static final String NORMALIZED_STALE = "normalized.stale";
    public static final String BASELINE_PARALLEL_RETAINED = "baseline.parallel.retained";
    public static final String INTERVAL_EMPTY = "interval.empty";
    public static final String UNIT_UNCONVERTIBLE = "unit.unconvertible";
    public static final String PARAM_REVISED = "param.revised";
    public static final String SOURCE_LOCAL_CHANGE = "source.local.change";
    public static final String DECISION_ACCEPT = "decision.accept";
    public static final String DECISION_REJECT = "decision.reject";
    public static final String DECISION_CONFLICT = "decision.conflict";
    public static final String DERIVATION_STALE = "derivation.stale";
    public static final String DERIVATION_RECOMPUTED = "derivation.recomputed";
    public static final String POINTER_SWITCH = "pointer.switch";
    public static final String CASE_STAGE = "case.stage";
}
