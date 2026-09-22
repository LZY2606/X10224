package dsc.model;

import java.util.List;

public record Decision(
        String decisionId,
        String kind,
        String subjectId,
        String chosenValue,
        List<String> retainedAlternativeIds,
        String rationale,
        String actor,
        String eventId,
        long baseSeq,
        long seq
) {
    public static final String ADOPT_BASELINE = "adopt-baseline";
    public static final String REJECT_BASELINE = "reject-baseline";
    public static final String CONFLICT_CHOICE = "conflict-choice";
    public static final String ADJUST_INTERVAL = "adjust-interval";
}
