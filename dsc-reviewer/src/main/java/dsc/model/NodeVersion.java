package dsc.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One immutable version of a derivation node. The current pointer only moves to
 * a new version after the whole derivation transaction committed.
 */
public record NodeVersion(
        String nodeId,
        String kind,
        int version,
        String fingerprint,
        String depsFingerprint,
        String producedByEvent,
        String status,
        JsonNode result,
        java.util.List<Diagnostic> diagnostics,
        String createdAt) {

    public static final String VALID = "VALID";
    public static final String STALE = "STALE";
    public static final String INDETERMINATE = "INDETERMINATE";
    public static final String ERROR = "ERROR";
}
