package dsc.domain;

public final class EventTypes {
    private EventTypes() {}
    public static final String CASE_OPENED = "CASE_OPENED";
    public static final String TRACE_IMPORTED = "TRACE_IMPORTED";
    public static final String INSTRUMENT_HEADER_ARCHIVED = "INSTRUMENT_HEADER_ARCHIVED";
    public static final String MASS_RECORDED = "MASS_RECORDED";
    public static final String MASS_REVISED = "MASS_REVISED";
    public static final String RATE_RECORDED = "RATE_RECORDED";
    public static final String ATMOSPHERE_RECORDED = "ATMOSPHERE_RECORDED";
    public static final String CALIBRATION_RECORDED = "CALIBRATION_RECORDED";
    public static final String CALIBRATION_REVISED = "CALIBRATION_REVISED";
    public static final String INTERVAL_DRAWN = "INTERVAL_DRAWN";
    public static final String BASELINES_COMPUTED = "BASELINES_COMPUTED";
    public static final String TRANSITION_DETECTED = "TRANSITION_DETECTED";
    public static final String TANGENT_SELECTED = "TANGENT_SELECTED";
    public static final String CANDIDATE_ACCEPTED = "CANDIDATE_ACCEPTED";
    public static final String CANDIDATE_REJECTED = "CANDIDATE_REJECTED";
    public static final String PARAMETER_ADJUSTED = "PARAMETER_ADJUSTED";
    public static final String SOURCE_PATCHED = "SOURCE_PATCHED";
    public static final String BRANCH_CONTINUED = "BRANCH_CONTINUED";
    public static final String CONFLICTING_DECISION = "CONFLICTING_DECISION";
    public static final String REPLAY_STEP = "REPLAY_STEP";
    public static final String EXPORTED = "EXPORTED";
}
