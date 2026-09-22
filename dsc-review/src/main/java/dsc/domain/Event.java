package dsc.domain;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * An immutable audit entry. Every import, derivation, recompute, decision and boundary
 * detection is an event. Events are append-only; going back to an old event replays the
 * prefix and restores the then-current dependency versions, never overwriting history.
 */
public final class Event {
    public final long seq;                 // global monotonic order
    public final String id;                // stable identity: ev_<sha1(seq-stable content + branch)>>
    public final String branch;            // branch name; "" == main
    public final String type;              // machine-readable type
    public final String actor;             // user or "system"
    public final Instant at;
    public final ObjectNode payload;       // machine-readable judgment basis
    public final String basisSeq;          // seq of the state this event was based on (optimistic concurrency)
    public final String parentEventId;     // previous event id on this branch (chain)

    public Event(long seq, String branch, String type, String actor, Instant at,
                 ObjectNode payload, String basisSeq, String parentEventId) {
        this.seq = seq;
        this.branch = branch == null ? "" : branch;
        this.type = type;
        this.actor = actor;
        this.at = at;
        this.payload = payload;
        this.basisSeq = basisSeq;
        this.parentEventId = parentEventId;
        this.id = Id.of("ev", this.branch + "|" + seq + "|" + type + "|" + Json.string(payload));
    }

    public ObjectNode node() {
        ObjectNode n = Json.obj();
        n.put("seq", seq);
        n.put("id", id);
        n.put("branch", branch);
        n.put("type", type);
        n.put("actor", actor);
        n.put("at", at.toString());
        n.set("payload", payload);
        n.put("basisSeq", basisSeq);
        n.put("parentEventId", parentEventId);
        return n;
    }
}
