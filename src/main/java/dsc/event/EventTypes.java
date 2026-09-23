package dsc.event;

/** Canonical machine-readable event types produced by the application and the case replay. */
public final class EventTypes {
    private EventTypes() {}

    public static final String IMPORT = "IMPORT";
    public static final String SOURCE_REVISED = "SOURCE_REVISED";
    public static final String INTERVAL_DRAGGED = "INTERVAL_DRAGGED";
    public static final String TANGENT_SELECTED = "TANGENT_SELECTED";
    public static final String BASELINES_DERIVED = "BASELINES_DERIVED";
    public static final String BASELINE_ACCEPTED = "BASELINE_ACCEPTED";
    public static final String BASELINE_REJECTED = "BASELINE_REJECTED";
    public static final String DERIVED_EXPIRED = "DERIVED_EXPIRED";
    public static final String DERIVED_STILL_VALID = "DERIVED_STILL_VALID";
    public static final String REVERSE_HEATFLOW = "REVERSE_HEATFLOW";
    public static final String NONUNIFORM_TEMPERATURE = "NONUNIFORM_TEMPERATURE";
    public static final String OVERLAP_TRANSITIONS = "OVERLAP_TRANSITIONS";
    public static final String CYCLE_SWITCH = "CYCLE_SWITCH";
    public static final String MASS_REVISED = "MASS_REVISED";
    public static final String PARALLEL_BASELINES = "PARALLEL_BASELINES";
    public static final String EMPTY_INTERVAL = "EMPTY_INTERVAL";
    public static final String DIAGNOSTIC = "DIAGNOSTIC";
    public static final String CONFLICT = "CONFLICT";
    public static final String CONFLICT_RESOLVED = "CONFLICT_RESOLVED";
    public static final String REPLAY_MARKER = "REPLAY_MARKER";
    public static final String ARCHIVE_IMPORTED = "ARCHIVE_IMPORTED";
}
