package dsc.model;

import java.util.List;

public record BaselineCandidate(
        String candidateId,
        String intervalId,
        String method,
        String rationale,
        List<double[]> polylineK_W,
        double rank,
        String status,
        String decisionId,
        String producedAtEvent,
        List<String> evidenceDiagIds
) {
    public static final String ACTIVE = "active";
    public static final String SUPERSEDED = "superseded";
    public static final String REJECTED = "rejected";
    public static final String ADOPTED = "adopted";
    public static final String RETAINED = "retained";

    public BaselineCandidate withStatus(String newStatus) {
        return new BaselineCandidate(candidateId, intervalId, method, rationale, polylineK_W, rank, newStatus,
                decisionId, producedAtEvent, evidenceDiagIds);
    }
}
