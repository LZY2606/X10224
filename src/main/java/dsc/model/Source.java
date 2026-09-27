package dsc.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One archived input document. Original text and original key/value strings are kept
 * verbatim; conversion happens only when derivations read them.
 */
public record Source(
        String sourceId,
        String projectId,
        SourceKind kind,
        String filename,
        String rawPayload,
        Map<String, String> rawFields,
        String revisesSourceId,
        String producingEventId,
        long ingestSeq,
        String branchRoot,
        String actor,
        long createdAtMs
) {
    public Source {
        rawFields = rawFields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(rawFields);
    }
}
