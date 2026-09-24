package dsc.domain;

import java.util.List;

/** 单个分析窗口的全部并列产物：候选基线、每个候选对应的转折结果。 */
public record WindowResult(
        String windowId,
        String segmentId,
        double fromC,
        double toC,
        List<BaselineCandidate> candidates,
        List<Transition> transitions,
        List<String> diagnostics,
        List<Anomaly> anomalies,
        String acceptedCandidateId
) {
    public BaselineCandidate candidate(String id) {
        for (BaselineCandidate c : candidates) if (c.id().equals(id)) return c;
        return null;
    }
    public Transition transitionFor(String baselineId) {
        for (Transition t : transitions) if (t.baselineId().equals(baselineId)) return t;
        return null;
    }
}
