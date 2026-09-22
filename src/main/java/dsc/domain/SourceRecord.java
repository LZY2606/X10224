package dsc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * An independently archived, versioned direct source.
 *
 * Sources are immutable: a mass revision or a rate correction inserts a new record with a
 * fresh {@link #version} and {@link #supersedes} link; the old record remains readable.
 * Payload shape depends on {@link #kind} (see case pack / parsing code).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SourceRecord {
    public String id;
    public SourceKind kind;
    public String segmentId;
    public int version = 1;
    public String supersedes;
    public String label;
    public String origin;
    public String filenameHint;
    public JsonNode payload;
    public Instant archivedAt;
    public String note;

    public SourceRecord() {
    }

    public SourceRecord(String id, SourceKind kind, String segmentId, int version,
                        String supersedes, String label, String origin,
                        String filenameHint, JsonNode payload, String note) {
        this.id = id;
        this.kind = kind;
        this.segmentId = segmentId;
        this.version = version;
        this.supersedes = supersedes;
        this.label = label;
        this.origin = origin;
        this.filenameHint = filenameHint;
        this.payload = payload;
        this.archivedAt = Instant.now();
        this.note = note;
    }

    public String ref() {
        return kind.code + "#" + id + "@v" + version;
    }
}
