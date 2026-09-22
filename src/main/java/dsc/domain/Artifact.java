package dsc.domain;

import java.util.List;
import java.util.Map;

/**
 * Immutable versioned object. Both imported source data and derived results
 * are artifacts: every derivation creates a new row with its own deps, and
 * the current pointer only moves after a complete transaction.
 */
public record Artifact(
        String id,
        String historyId,
        String kind,
        String groupKey,
        int revision,
        String parentId,
        String status,
        List<String> deps,
        Map<String, Object> payload,
        String digest,
        long seq) {

    public static final String ACTIVE = "active";
    public static final String PENDING = "pending";
    public static final String SUPERSEDED = "superseded";
    public static final String REJECTED = "rejected";

    public boolean current() {
        return ACTIVE.equals(status) || PENDING.equals(status);
    }
}
