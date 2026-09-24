package dsc.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Immutable ledger record. {@code payload} is the machine-readable content; the envelope
 * carries the stable id, ordering, causation and branch information.
 */
public final class Event {
    private final String eventId;
    private final long seq;
    private final String projectId;
    private final EventType type;
    private final Instant at;
    private final String author;
    /** Event this one directly followed (causal parent along the active branch). */
    private final String parentEventId;
    /** Branch id the event belongs to. */
    private final String branchId;
    /** Explicit dependency versions this event was decided against ("name@id" entries). */
    private final java.util.List<String> basisRefs;
    private final JsonNode payload;

    public Event(String eventId, long seq, String projectId, EventType type, Instant at,
                 String author, String parentEventId, String branchId,
                 java.util.List<String> basisRefs, JsonNode payload) {
        this.eventId = eventId;
        this.seq = seq;
        this.projectId = projectId;
        this.type = type;
        this.at = at;
        this.author = author;
        this.parentEventId = parentEventId;
        this.branchId = branchId;
        this.basisRefs = basisRefs == null ? java.util.List.of() : java.util.List.copyOf(basisRefs);
        this.payload = payload;
    }

    public String eventId() { return eventId; }
    public long seq() { return seq; }
    public String projectId() { return projectId; }
    public EventType type() { return type; }
    public Instant at() { return at; }
    public String author() { return author; }
    public String parentEventId() { return parentEventId; }
    public String branchId() { return branchId; }
    public java.util.List<String> basisRefs() { return basisRefs; }
    public JsonNode payload() { return payload; }
}
