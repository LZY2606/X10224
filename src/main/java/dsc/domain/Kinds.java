package dsc.domain;

/** String constants for artifact kinds, group keys and event types. */
public final class Kinds {
    private Kinds() {
    }

    // imported source artifacts (each stored separately)
    public static final String INSTRUMENT_OUT = "INSTRUMENT_OUT";
    public static final String MASS = "MASS";
    public static final String RATE = "RATE";
    public static final String ATMOSPHERE = "ATMOSPHERE";
    public static final String CALIBRATION = "CALIBRATION";
    public static final String TANGENT = "TANGENT";

    // derived artifacts
    public static final String CURVE = "CURVE";
    public static final String BASELINE_SET = "BASELINE_SET";
    public static final String TRANSITION_SET = "TRANSITION_SET";
    public static final String ENTHALPY_SET = "ENTHALPY_SET";

    // events
    public static final String EV_IMPORTED = "SOURCE_IMPORTED";
    public static final String EV_CURVE = "CURVE_NORMALIZED";
    public static final String EV_BASELINES = "BASELINES_PROPOSED";
    public static final String EV_TRANSITIONS = "TRANSITIONS_DETECTED";
    public static final String EV_ENTHALPY = "ENTHALPY_INTEGRATED";
    public static final String EV_DECIDED = "BASELINE_DECIDED";
    public static final String EV_REJECTED = "CANDIDATE_REJECTED";
    public static final String EV_CONFLICT = "EDIT_CONFLICT";
    public static final String EV_CORRECTED = "DIRECTION_CORRECTED";
    public static final String EV_MASS_REVISED = "MASS_REVISED";
    public static final String EV_SOURCE_PATCHED = "SOURCE_PATCHED";
    public static final String EV_STALE = "DERIVED_EXPIRED";
    public static final String EV_RESOLVED = "CONFLICT_RESOLVED";
    public static final String EV_EMPTY_REGION = "EMPTY_REGION_CHECKED";
}
