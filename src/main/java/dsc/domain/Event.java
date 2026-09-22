package dsc.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * One immutable, append-only log entry.
 *
 * <p>{@code seq} is the global per-history order; {@code branch} is the branch id ("main" for the
 * trunk). {@code baseSeq} is the fork point for a non-main branch (all branches see trunk events up
 * to that point). Payload is canonical JSON; {@code ruleVersion} tags derivation-producing events
 * so historical replay never silently reinterprets them with newer rules.
 */
public final class Event {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final long seq;
    private final String id;
    private final String historyId;
    private final String branch;
    private final long baseSeq;
    private final String type;
    private final String author;
    private final String ruleVersion;
    private final String caseRef;
    private final JsonNode payload;
    private final String createdAt;

    public Event(long seq, String id, String historyId, String branch, long baseSeq, String type,
                 String author, String ruleVersion, String caseRef, JsonNode payload, String createdAt) {
        this.seq = seq;
        this.id = id;
        this.historyId = historyId;
        this.branch = branch;
        this.baseSeq = baseSeq;
        this.type = type;
        this.author = author;
        this.ruleVersion = ruleVersion;
        this.caseRef = caseRef;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public long seq() {
        return seq;
    }

    public String id() {
        return id;
    }

    public String historyId() {
        return historyId;
    }

    public String branch() {
        return branch;
    }

    public long baseSeq() {
        return baseSeq;
    }

    public String type() {
        return type;
    }

    public String author() {
        return author;
    }

    public String ruleVersion() {
        return ruleVersion;
    }

    public String caseRef() {
        return caseRef;
    }

    public JsonNode payload() {
        return payload;
    }

    public String createdAt() {
        return createdAt;
    }

    public ObjectNode toJson() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("seq", seq);
        node.put("id", id);
        node.put("historyId", historyId);
        node.put("branch", branch);
        node.put("baseSeq", baseSeq);
        node.put("type", type);
        node.put("author", author);
        if (ruleVersion != null) {
            node.put("ruleVersion", ruleVersion);
        }
        if (caseRef != null) {
            node.put("caseRef", caseRef);
        }
        node.set("payload", payload);
        node.put("createdAt", createdAt);
        return node;
    }

    public static Event fromJson(JsonNode node) {
        return new Event(
                node.get("seq").asLong(),
                node.get("id").asText(),
                node.get("historyId").asText(),
                node.get("branch").asText("main"),
                node.path("baseSeq").asLong(0),
                node.get("type").asText(),
                node.path("author").asText("unknown"),
                node.hasNonNull("ruleVersion") ? node.get("ruleVersion").asText() : null,
                node.hasNonNull("caseRef") ? node.get("caseRef").asText() : null,
                node.get("payload"),
                node.path("createdAt").asText(""));
    }
}
