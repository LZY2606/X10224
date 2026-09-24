package dsc.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Append-only archive event. Events are the only thing that changes the state;
 * derivations are recorded as events too, so rewinding to any event restores
 * the exact dependency versions and the visible conclusions of that moment.
 */
public record Event(
        String id,
        long seq,
        String type,
        String branch,
        String parentId,       // previous event on this branch (null for root)
        long baseSeq,          // seq this branch forked from (-1 for main)
        String actor,
        long createdAt,
        String targetId,       // object this event primarily concerns (stable id, for display provenance)
        JsonNode payload) {

    // ---- Source ingest ----
    public static final String SAMPLE_CREATED = "SAMPLE_CREATED";
    public static final String CURVE_INGESTED = "CURVE_INGESTED";
    public static final String MASS_SET = "MASS_SET";
    public static final String RATE_RECORDED = "RATE_RECORDED";
    public static final String ATMOSPHERE_RECORDED = "ATMOSPHERE_RECORDED";
    public static final String CALIBRATION_SET = "CALIBRATION_SET";
    public static final String TANGENT_PICKED = "TANGENT_PICKED";
    public static final String SOURCE_PATCHED = "SOURCE_PATCHED"; // one direct source edited -> local propagation only

    // ---- Review actions ----
    public static final String INTERVAL_DRAWN = "INTERVAL_DRAWN";   // curve page drag
    public static final String INTERVAL_REFIT = "INTERVAL_REFIT";   // parameters adjusted
    public static final String BASELINE_ADOPTED = "BASELINE_ADOPTED";
    public static final String BASELINE_REJECTED = "BASELINE_REJECTED";
    public static final String FEATURE_ACCEPTED = "FEATURE_ACCEPTED";
    public static final String FEATURE_REJECTED = "FEATURE_REJECTED";

    // ---- Derivations (generated, deterministic) ----
    public static final String DERIVED = "DERIVED";

    // ---- Machine-readable judgments (the semantic rules) ----
    public static final String JUDGMENT = "JUDGMENT";

    // ---- Branching / history ----
    public static final String BRANCH_FORKED = "BRANCH_FORKED";
    public static final String BRANCH_CHOSEN = "BRANCH_CHOSEN";
    public static final String BRANCH_REJECTED = "BRANCH_REJECTED";

    public static String judgeCode(ObjectNode p) {
        JsonNode n = p.get("code");
        return n == null ? null : n.asText();
    }
}
