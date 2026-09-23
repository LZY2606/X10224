package dsc.event;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Machine-readable event in the append-only journal. Replaying events in (seq) order from a
 * baseline version reproduces every past state; old events are never overwritten by newer
 * computations.
 */
public final class Event {
    public final long seq;
    public final String id;
    public final String type;
    public final String sampleId;
    public final String subjectId;
    /** JSON payload: inputs, decision, affected object ids and status. */
    public final String payloadJson;
    public final String parentEventId;
    public final String actor;
    public final long createdAtEpochMs;

    public Event(long seq, String id, String type, String sampleId, String subjectId,
                 String payloadJson, String parentEventId, String actor, long createdAtEpochMs) {
        this.seq = seq;
        this.id = id;
        this.type = type;
        this.sampleId = sampleId;
        this.subjectId = subjectId;
        this.payloadJson = payloadJson;
        this.parentEventId = parentEventId;
        this.actor = actor;
        this.createdAtEpochMs = createdAtEpochMs;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seq", seq);
        m.put("id", id);
        m.put("type", type);
        m.put("sampleId", sampleId);
        m.put("subjectId", subjectId);
        m.put("payload", payloadJson);
        m.put("parentEventId", parentEventId);
        m.put("actor", actor);
        m.put("createdAtEpochMs", createdAtEpochMs);
        return m;
    }
}
