package dsc.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/**
 * One immutable fact in a sample history. Events are append-only; the sequence
 * number, history id and identity fields are assigned by the store under the
 * write lock, never by the caller.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Event {
    private long seq;
    private String historyId;
    private EventType type;
    private String entityId;
    private int schemaVersion;
    private Instant at;
    private String actor;
    private String comment;
    private JsonNode payload;
    private List<Judgment> judgments;
    /** Optimistic-concurrency token: seq of the last event this edit was based on. */
    private Long baseSeq;
    /** Derivations only: ids of dependency versions this result was computed from. */
    private List<String> dependencies;
    /** Derivations only: id of the result this one replaces (old row is kept). */
    private String supersedes;

    public Event() {}

    public Event(EventType type, String entityId, Instant at, String actor, String comment,
                 JsonNode payload, List<Judgment> judgments, Long baseSeq,
                 List<String> dependencies, String supersedes) {
        this.type = type;
        this.entityId = entityId;
        this.at = at;
        this.actor = actor;
        this.comment = comment;
        this.payload = payload;
        this.judgments = judgments;
        this.baseSeq = baseSeq;
        this.dependencies = dependencies;
        this.supersedes = supersedes;
    }

    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }
    public String getHistoryId() { return historyId; }
    public void setHistoryId(String historyId) { this.historyId = historyId; }
    public EventType getType() { return type; }
    public void setType(EventType type) { this.type = type; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public JsonNode getPayload() { return payload; }
    public void setPayload(JsonNode payload) { this.payload = payload; }
    public List<Judgment> getJudgments() { return judgments; }
    public void setJudgments(List<Judgment> judgments) { this.judgments = judgments; }
    public Long getBaseSeq() { return baseSeq; }
    public void setBaseSeq(Long baseSeq) { this.baseSeq = baseSeq; }
    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }
    public String getSupersedes() { return supersedes; }
    public void setSupersedes(String supersedes) { this.supersedes = supersedes; }
}
