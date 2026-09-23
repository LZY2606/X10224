package dsc.model;

import java.util.List;
import java.util.Map;

/**
 * A machine-readable entry in the audit ledger. Every imported source,
 * derivation, reviewer accept/reject and automated diagnostic produces an
 * event. The sequence is monotonic per study and is what "time travel"
 * restores: rewinding to an old event reconstructs the dependency versions
 * and visible conclusions that held at that moment.
 */
public record EventRecord(
        long seq,
        String studyId,
        String type,
        String actor,
        String subjectId,
        String ruleCode,
        String rationale,
        List<String> affectedIds,
        Map<String, Object> payload,
        String at
) {
    // Event type constants
    public static final String IMPORT = "source.imported";
    public static final String PARSE_DIAGNOSTIC = "source.diagnostic";
    public static final String SEGMENT_DECIDED = "segment.direction";
    public static final String BASELINE_PROPOSED = "baseline.proposed";
    public static final String BASELINE_ADOPTED = "baseline.adopted";
    public static final String BASELINE_REJECTED = "baseline.rejected";
    public static final String WINDOW_MOVED = "window.moved";
    public static final String TRANSITION_DERIVED = "transition.derived";
    public static final String TRANSITION_STALE = "transition.stale";
    public static final String SOURCE_REVISED = "source.revised";
    public static final String MASS_REVISED = "mass.revised";
    public static final String DERIVED_INVALIDATED = "derived.invalidated";
    public static final String DECISION_CONFLICT = "decision.conflict";
    public static final String DECISION_RESOLVED = "decision.resolved";
    public static final String OVERLAP_DETECTED = "transition.overlap";
    public static final String EMPTY_WINDOW = "window.empty";
    public static final String REVERSE_POLARITY = "trace.reversePolarity";
    public static final String NONUNIFORM_GRID = "trace.nonUniformGrid";
    public static final String CYCLE_SWITCH = "segment.cycleSwitch";
    public static final String MULTI_CANDIDATE = "baseline.multiCandidate";
}
