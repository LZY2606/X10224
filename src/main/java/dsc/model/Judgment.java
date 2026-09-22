package dsc.model;

import java.util.List;

public record Judgment(
        String judgmentId,
        String ruleCode,
        String status,
        String subjectType,
        String subjectId,
        String message,
        List<String> affectedObjectIds,
        List<String> evidenceDiagIds,
        String inputFragmentRef,
        String eventId,
        long seq
) {
    public static final String HOLD = "hold";
    public static final String VIOLATED = "violated";
    public static final String SATISFIED = "satisfied";
    public static final String AMBIGUOUS = "ambiguous";
}
