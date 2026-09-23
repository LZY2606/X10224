package org.matlab.dsc.domain;

import java.util.List;
import java.util.Map;

/**
 * One immutable, independently archived direct source.
 *
 * @param id        stable identity, stable across revisions
 * @param sampleId  sample history the source belongs to
 * @param kind      which direct source category this is
 * @param revision  1-based revision number; revising creates a new row
 * @param supersedesId id of the previous revision, or null
 * @param rawHeader original header representation (string key -> raw string)
 * @param rawPoints raw instrument rows (instrument kind only)
 * @param payload   parsed payload; values keep units/frame or stay raw+diagnostic
 * @param origin    human-readable file / entry name for provenance
 */
public record SourceRecord(String id, String sampleId, SourceKind kind, int revision,
                           String supersedesId, Map<String, String> rawHeader,
                           List<RawPoint> rawPoints, Map<String, Object> payload,
                           String origin) {

    public boolean isInstrument() {
        return kind == SourceKind.INSTRUMENT;
    }

    public SourceRecord withRevision(String newId, String supersedes) {
        return new SourceRecord(newId, sampleId, kind, revision + 1, supersedes,
                rawHeader, rawPoints, payload, origin);
    }
}
