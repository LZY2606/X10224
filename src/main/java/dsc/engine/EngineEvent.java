package dsc.engine;

/** 机器可读事件类型代码（界面/测试据此断言判定依据）。 */
public final class EngineEvent {
    public static final String CASE_IMPORTED = "CASE_IMPORTED";
    public static final String SIGN_CONVENTION_RESOLVED = "SIGN_CONVENTION_RESOLVED";
    public static final String SEGMENT_SEPARATION_ENFORCED = "SEGMENT_SEPARATION_ENFORCED";
    public static final String NON_UNIFORM_GRID_DETECTED = "NON_UNIFORM_GRID_DETECTED";
    public static final String UNCONVERTIBLE_POINT_KEPT = "UNCONVERTIBLE_POINT_KEPT";
    public static final String MULTIPLE_BASELINES_RETAINED = "MULTIPLE_BASELINES_RETAINED";
    public static final String EMPTY_INTERVAL_REJECTED = "EMPTY_INTERVAL_REJECTED";
    public static final String OVERLAP_SPLIT_DETECTED = "OVERLAP_SPLIT_DETECTED";
    public static final String RULE_REVISION = "RULE_REVISION";
    public static final String MASS_REVISION = "MASS_REVISION";
    public static final String LOCAL_SOURCE_CHANGE = "LOCAL_SOURCE_CHANGE";
    public static final String DERIVED_STALE = "DERIVED_STALE";
    public static final String DERIVED_STILL_VALID = "DERIVED_STILL_VALID";
    public static final String BASELINE_ACCEPTED = "BASELINE_ACCEPTED";
    public static final String BASELINE_REJECTED = "BASELINE_REJECTED";
    public static final String INTERVAL_ADJUSTED = "INTERVAL_ADJUSTED";
    public static final String CONFLICTING_DECISION = "CONFLICTING_DECISION";
    public static final String CONFLICT_RESOLVED = "CONFLICT_RESOLVED";
    public static final String REPLAY_COMPLETED = "REPLAY_COMPLETED";

    private EngineEvent() {
    }
}
