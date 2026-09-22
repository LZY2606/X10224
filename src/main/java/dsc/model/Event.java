package dsc.model;

/**
 * Append-only event. Everything that changes application state is an event:
 * sources, derivations, and human decisions. Events are immutable once stored.
 */
public record Event(
        long seq,
        String id,
        String type,
        String objectId,
        String parentId,
        String basisId,
        String actor,
        String payloadJson,
        String at,
        String supersededBy) {
}
