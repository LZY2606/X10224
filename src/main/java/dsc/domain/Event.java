package dsc.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Append-only history entry. Every accepted/rejected/adjusted review action is
 * an event; derivations reference the exact event+version inputs they used.
 */
public record Event(long seq,
                    String id,
                    EventType type,
                    String actor,
                    long atEpochMs,
                    String basis,
                    Map<String, Object> payload,
                    List<String> deps) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seq", seq);
        m.put("id", id);
        m.put("type", type.name());
        m.put("actor", actor);
        m.put("at", atEpochMs);
        m.put("basis", basis);
        m.put("payload", payload);
        m.put("deps", deps);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Event fromMap(Map<String, Object> m) {
        return new Event(
            ((Number) m.get("seq")).longValue(),
            (String) m.get("id"),
            EventType.valueOf((String) m.get("type")),
            (String) m.getOrDefault("actor", "system"),
            m.get("at") == null ? 0L : ((Number) m.get("at")).longValue(),
            (String) m.get("basis"),
            (Map<String, Object>) m.getOrDefault("payload", new LinkedHashMap<>()),
            m.get("deps") == null ? List.of() : ((List<?>) m.get("deps")).stream().map(String::valueOf).toList()
        );
    }
}
