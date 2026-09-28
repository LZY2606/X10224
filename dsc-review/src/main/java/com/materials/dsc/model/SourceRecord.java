package com.materials.dsc.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Archived direct source.  {@code version} increments per revision; revising a
 * source never overwrites the previous version.
 */
public final class SourceRecord {
    public final String id;
    public final SourceKind kind;
    public final int version;
    public final String scopeSegmentId;
    public final String label;
    public final String rawRepresentation;
    public final String unit;
    public final JsonNode payload;
    public final String supersedesId;
    public final String originEventSeq;

    public SourceRecord(String id, SourceKind kind, int version, String scopeSegmentId,
                        String label, String rawRepresentation, String unit,
                        JsonNode payload, String supersedesId, String originEventSeq) {
        this.id = id;
        this.kind = kind;
        this.version = version;
        this.scopeSegmentId = scopeSegmentId;
        this.label = label;
        this.rawRepresentation = rawRepresentation;
        this.unit = unit;
        this.payload = payload;
        this.supersedesId = supersedesId;
        this.originEventSeq = originEventSeq;
    }
}
