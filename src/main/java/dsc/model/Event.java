package dsc.model;

import com.google.gson.JsonObject;

/**
 * An immutable event in the append-only review journal.
 *
 * <p>Every acceptance, rejection, parameter change or automated derivation is an event.
 * Events carry {@code expectedVersion} for optimistic concurrency and the event {@code seq}
 * defines replay order. A replayed journal reproduces every doc version byte-for-byte.
 */
public final class Event {
    public final long seq;
    public final String id;
    public final String caseId;
    public final String type;
    public final String actor;
    public final String expectedVersion;
    public final JsonObject payload;
    public final String ts;

    public Event(long seq, String id, String caseId, String type, String actor,
                 String expectedVersion, JsonObject payload, String ts) {
        this.seq = seq;
        this.id = id;
        this.caseId = caseId;
        this.type = type;
        this.actor = actor;
        this.expectedVersion = expectedVersion == null ? "" : expectedVersion;
        this.payload = payload;
        this.ts = ts;
    }

    public JsonObject toJson() {
        JsonObject o = J.obj();
        J.put(o, "seq", seq);
        J.put(o, "id", id);
        J.put(o, "caseId", caseId);
        J.put(o, "type", type);
        J.put(o, "actor", actor);
        J.put(o, "expectedVersion", expectedVersion);
        J.put(o, "payload", payload);
        J.put(o, "ts", ts);
        return o;
    }

    public static Event fromJson(JsonObject o) {
        return new Event(
                o.get("seq").getAsLong(),
                o.get("id").getAsString(),
                o.get("caseId").getAsString(),
                o.get("type").getAsString(),
                J.str(o, "actor", "user"),
                J.str(o, "expectedVersion", ""),
                o.getAsJsonObject("payload"),
                J.str(o, "ts", ""));
    }
}
