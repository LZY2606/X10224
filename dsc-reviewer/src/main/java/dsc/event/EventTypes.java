package dsc.event;

/** Machine-readable fact types and diagnostic codes shared by storage, engine and UI. */
public final class EventTypes {
    public static final String CASE_OPENED = "CASE_OPENED";
    public static final String SOURCE_ARCHIVED = "SOURCE_ARCHIVED";
    public static final String SEGMENT_IMPORTED = "SEGMENT_IMPORTED";
    public static final String SOURCE_PATCHED = "SOURCE_PATCHED";
    public static final String MASS_SET = "MASS_SET";
    public static final String RATE_REVISED = "RATE_REVISED";
    public static final String ATMOSPHERE_SET = "ATMOSPHERE_SET";
    public static final String CALIBRATION_SET = "CALIBRATION_SET";
    public static final String INTERVAL_DRAGGED = "INTERVAL_DRAGGED";
    public static final String TANGENT_PICKED = "TANGENT_PICKED";
    public static final String BASELINE_DECIDED = "BASELINE_DECIDED";
    public static final String CONFLICT_DETECTED = "CONFLICT_DETECTED";
    public static final String CONFLICT_RESOLVED = "CONFLICT_RESOLVED";
    public static final String DIAGNOSTIC_RAISED = "DIAGNOSTIC_RAISED";
    public static final String REVIEW_NOTE = "REVIEW_NOTE";

    // diagnostic codes (the seven boundary situations plus conversion/order problems)
    public static final String D_REVERSED_HEATFLOW = "REVERSED_HEATFLOW";
    public static final String D_NONUNIFORM_TEMPERATURE = "NONUNIFORM_TEMPERATURE";
    public static final String D_OVERLAPPING_TRANSITIONS = "OVERLAPPING_TRANSITIONS";
    public static final String D_CYCLE_MISMATCH = "CYCLE_MISMATCH";
    public static final String D_MASS_STALE = "MASS_STALE";
    public static final String D_MULTIPLE_BASELINES = "MULTIPLE_BASELINES";
    public static final String D_EMPTY_INTERVAL = "EMPTY_INTERVAL";
    public static final String D_UNCONVERTED_VALUE = "UNCONVERTED_VALUE";
    public static final String D_DIRECTION_AMBIGUOUS = "DIRECTION_AMBIGUOUS";
    public static final String D_TEMPERATURE_ORDER = "TEMPERATURE_ORDER";

    private EventTypes() {
    }
}
