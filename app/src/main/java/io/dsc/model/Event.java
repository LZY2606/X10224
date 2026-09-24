package io.dsc.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Append-only, immutable fact. Events are the only write path into state.
 * A derivation event is committed in the same transaction as the command that
 * caused it; the "current" pointer only moves after full success.
 */
public record Event(
        String eventId,          // globally unique, stable across export/import
        long seq,                // per-branch order, 1-based
        String branchId,
        EventType type,
        long occurredAt,         // epoch millis (fixed during deterministic replay)
        String actor,
        ObjectNode payload,
        String prevHash,         // hash chain inside a branch
        String hash              // canonical hash over this event
) {
    public JsonNode payload(String field) {
        JsonNode node = payload.get(field);
        if (node == null) throw new IllegalStateException("missing payload field: " + field);
        return node;
    }

    public String stringField(String field) {
        return payload(field).asText();
    }

    public static final ObjectMapper MAPPER = new ObjectMapper();
}
