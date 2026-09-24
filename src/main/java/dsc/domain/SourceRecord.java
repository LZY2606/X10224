package dsc.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One version of a direct source. The six archival kinds are kept separate:
 * instrument output, sample mass, scan rate, atmosphere, temperature
 * calibration and the user-selected tangents. Each new version is a new row;
 * nothing is mutated in place.
 */
public record SourceRecord(String id,
                           String historyId,
                           SourceKind kind,
                           int version,
                           long eventSeq,
                           String origin,
                           String rawText,
                           Map<String, Object> attrs) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("historyId", historyId);
        m.put("kind", kind.code);
        m.put("version", version);
        m.put("eventSeq", eventSeq);
        m.put("origin", origin);
        m.put("rawText", rawText);
        m.put("attrs", attrs);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static SourceRecord fromMap(Map<String, Object> m) {
        return new SourceRecord(
            (String) m.get("id"),
            (String) m.get("historyId"),
            SourceKind.fromCode((String) m.get("kind")),
            ((Number) m.get("version")).intValue(),
            m.get("eventSeq") == null ? 0 : ((Number) m.get("eventSeq")).longValue(),
            (String) m.getOrDefault("origin", ""),
            (String) m.getOrDefault("rawText", ""),
            (Map<String, Object>) m.getOrDefault("attrs", new LinkedHashMap<>())
        );
    }
}
