package dsc.store;

/** 事件类型常量。所有领域判断都以机器可读事件落库。 */
public final class EventTypes {
    private EventTypes() {}

    public static final String CASEPACK_LOADED = "casepack.loaded";
    public static final String SAMPLE_CREATED = "sample.created";
    public static final String SEGMENT_IMPORTED = "segment.imported";
    public static final String MASS_REVISED = "mass.revised";
    public static final String CALIBRATION_RECORDED = "calibration.recorded";
    public static final String TANGENT_SELECTED = "tangent.selected";
    public static final String BASELINE_PROPOSED = "baseline.proposed";
    public static final String BASELINE_ADOPTED = "baseline.adopted";
    public static final String ANALYSIS_PUBLISHED = "analysis.published";
    public static final String ANALYSIS_INVALIDATED = "analysis.invalidated";
    public static final String DECISION_MADE = "decision.made";
    public static final String DIAGNOSTIC_RAISED = "diagnostic.raised";
    public static final String CONFLICT_RAISED = "conflict.raised";
    public static final String CONFLICT_RESOLVED = "conflict.resolved";
}
