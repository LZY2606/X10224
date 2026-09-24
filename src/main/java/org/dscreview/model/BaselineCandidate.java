package org.dscreview.model;

import java.util.List;
import static org.dscreview.model.DomainEnums.CandidateStatus;

/**
 * A proposed baseline inside an interval. Multiple legal baselines coexist and
 * keep their differences; at most one is ADOPTED, the rest remain ALTERNATE.
 *
 * @param kind       LINEAR (single tangent), STEP (glass-transition stepped),
 *                   SPLIT (overlapping transitions, per-event tangents)
 * @param status     PROPOSED / ADOPTED / REJECTED / ALTERNATE
 * @param yAt        baseline heat flow (mW) sampled at each integration point index
 * @param residualEnthalpyMj integrated (signal - baseline) area, raw mJ
 */
public record BaselineCandidate(
        String candidateId,
        String intervalId,
        String segmentId,
        String kind,
        String tangentId,
        CandidateStatus status,
        double fromDegC,
        double toDegC,
        int[] pointIndexes,
        double[] yAt,
        double residualEnthalpyMj,
        double uncertaintyMj,
        String rationale,
        List<Diagnostic> diagnostics) {

    public static final String KIND_LINEAR = "LINEAR";
    public static final String KIND_STEP = "STEP";
    public static final String KIND_SPLIT = "SPLIT";
}
