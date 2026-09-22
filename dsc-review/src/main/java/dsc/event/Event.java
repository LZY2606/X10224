package dsc.event;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Append-only event. Identity is content-addressed so an export/import round
 * trip keeps the same id; the same client-key on a branch may not be recorded
 * twice (idempotent append / optimistic concurrency).
 */
public record Event(
        String id,
        String parentId,
        String branch,
        long seq,
        String actor,
        String type,
        long createdAtMillis,
        String clientKey,
        String expectsParentId,
        Map<String, Object> payload
) {

    public static final String TRUNK = "trunk";

    public Map<String, Object> bodyForHash() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("parentId", parentId == null ? "" : parentId);
        m.put("branch", branch);
        m.put("seq", seq);
        m.put("actor", actor);
        m.put("type", type);
        m.put("createdAtMillis", createdAtMillis);
        m.put("clientKey", clientKey == null ? "" : clientKey);
        m.put("payload", payload);
        return m;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("parentId", parentId);
        m.put("branch", branch);
        m.put("seq", seq);
        m.put("actor", actor);
        m.put("type", type);
        m.put("createdAtMillis", createdAtMillis);
        m.put("clientKey", clientKey);
        m.put("expectsParentId", expectsParentId);
        m.put("payload", payload);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Event fromMap(Map<String, Object> m) {
        return new Event(
                (String) m.get("id"),
                (String) m.get("parentId"),
                (String) m.get("branch"),
                ((Number) m.get("seq")).longValue(),
                (String) m.get("actor"),
                (String) m.get("type"),
                ((Number) m.get("createdAtMillis")).longValue(),
                (String) m.get("clientKey"),
                (String) m.get("expectsParentId"),
                (Map<String, Object>) m.get("payload")
        );
    }
}
