package io.dsc.model;

/**
 * An independently archived direct source. Amendments create a new version
 * (versionNo increments); derivation validity is tracked against versions.
 *
 * @param id          stable identity, e.g. source:mass-v1
 * @param caseId      owning case
 * @param kind        which direct source family this is
 * @param segmentId   segment for per-segment sources, null for case-wide ones
 * @param versionNo   1-based; every amendment appends a new version
 * @param supersedesId id of the previous version, or null
 * @param summary     short human description
 * @param bodyRef     reference into the raw archive (file name / text key)
 * @param storedAt    archive timestamp
 */
public record SourceRecord(
        StableId id,
        String caseId,
        SourceKind kind,
        String segmentId,
        int versionNo,
        StableId supersedesId,
        String summary,
        String bodyRef,
        long storedAt
) {
}
