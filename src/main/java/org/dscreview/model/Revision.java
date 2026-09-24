package org.dscreview.model;

import java.util.List;

/**
 * An immutable, content-addressed version of one domain object.
 * Derived revisions record their inputs (provenance edges) and a validity verdict.
 */
public record Revision<T>(
        String id,
        String type,
        long version,
        String contentHash,
        String createdAt,
        String createdBy,
        String ruleVersion,
        T payload,
        List<InputRef> inputs,
        String supersedes,
        Validity validity) {

    public Revision<T> withValidity(Validity v) {
        return new Revision<>(id, type, version, contentHash, createdAt, createdBy, ruleVersion,
                payload, inputs, supersedes, v);
    }
}
