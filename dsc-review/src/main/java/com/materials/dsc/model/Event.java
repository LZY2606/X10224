package com.materials.dsc.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Append-only, machine-readable event.  Events are the only mutation path of
 * the system: importing a source, revising it, computing a derivation,
 * accepting/rejecting a candidate or adjusting a parameter all append here.
 */
public final class Event {
    public final long seq;
    public final String id;
    public final String type;
    public final String actor;
    public final long createdAt;
    public final String parentVersion;
    public final JsonNode detail;

    public Event(long seq, String id, String type, String actor, long createdAt,
                 String parentVersion, JsonNode detail) {
        this.seq = seq;
        this.id = id;
        this.type = type;
        this.actor = actor;
        this.createdAt = createdAt;
        this.parentVersion = parentVersion;
        this.detail = detail;
    }
}
