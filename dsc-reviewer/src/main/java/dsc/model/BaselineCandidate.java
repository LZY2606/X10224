package dsc.model;

import java.util.List;

/**
 * One admissible baseline for a window. Multiple candidates can coexist: a
 * reasonable alternative is never deleted by adopting another one.
 */
public record BaselineCandidate(
        String candidateId,
        String method,
        String windowId,
        String traceId,
        double slope,
        double intercept,
        double heatFlowUnitFactor,
        boolean needsTangent,
        String tangentId,
        List<Double> fitRange,
        double residualRms,
        String status,
        String adoptedByEvent,
        String rejectedByEvent) {

    public static final String CANDIDATE = "CANDIDATE";
    public static final String ADOPTED = "ADOPTED";
    public static final String REJECTED = "REJECTED";
}
