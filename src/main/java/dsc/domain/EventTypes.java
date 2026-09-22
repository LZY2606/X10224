package dsc.domain;

/**
 * Event types in the append-only log.
 *
 * <p>Sources are archived separately: instrument output, sample mass, scan rate, atmosphere,
 * temperature calibration and user-selected tangents each have their own event and version chain.
 */
public final class EventTypes {
    public static final String HISTORY_CREATED = "history.created";
    public static final String SEGMENT_ARCHIVED = "source.segment_archived";
    public static final String MASS_ARCHIVED = "source.mass_archived";
    public static final String SCAN_RATE_ARCHIVED = "source.scan_rate_archived";
    public static final String ATMOSPHERE_ARCHIVED = "source.atmosphere_archived";
    public static final String TEMP_CALIBRATION_ARCHIVED = "source.temp_calibration_archived";
    public static final String TANGENTS_ARCHIVED = "source.tangents_archived";
    public static final String INTERVAL_DRAWN = "analysis.interval_drawn";
    public static final String DERIVATION_RECORDED = "analysis.derivation_recorded";
    public static final String BASELINE_DECIDED = "analysis.baseline_decided";
    public static final String BRANCH_OPENED = "branch.opened";
    public static final String BRANCH_RESOLVED = "branch.resolved";

    private EventTypes() {
    }

    public static boolean isSourceEvent(String type) {
        return type.startsWith("source.");
    }
}
