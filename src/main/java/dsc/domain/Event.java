package dsc.domain;

import java.util.Map;

/**
 * An immutable fact in the event log. Events are the only way state changes;
 * every derivation records the exact event/version references it depends on.
 */
public record Event(
        long seq,
        String id,
        EventType type,
        String actor,
        String timestampIso,
        String branch,
        Map<String, String> payload,
        String parentEventId) {

    public String payload(String key) {
        return payload == null ? null : payload.get(key);
    }
}
