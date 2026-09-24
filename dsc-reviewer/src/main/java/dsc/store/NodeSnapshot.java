package dsc.store;

/** A cached derivation node at one state version: status is VALID, STALE or INDETERMINATE. */
public record NodeSnapshot(String nodeId, String payload, String status) {
}
