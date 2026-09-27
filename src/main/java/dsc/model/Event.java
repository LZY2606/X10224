package dsc.model;

/**
 * Immutable, machine-readable audit event. Events form a parent-linked tree (branches).
 * The current view is the root-to-tip path of the currently selected branch tip.
 * Payload is JSON so the judgment basis is machine readable, not prose only.
 */
public record Event(
        long seq,
        String eventId,
        String projectId,
        String parentEventId,
        String branchRoot,
        String type,
        String actor,
        long createdAtMs,
        long expectedBaseSeq,
        String payloadJson
) {
}
