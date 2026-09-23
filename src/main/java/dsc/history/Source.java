package dsc.history;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * One version of one direct input. Versions are immutable and append-only;
 * {@code logicalId} is stable across revisions, {@code id} identifies the version.
 * {@code payload} retains the original representation (including values that cannot
 * be normalized) and is only interpreted by the derived layer.
 */
public record Source(
        String id,
        String logicalId,
        SourceKind kind,
        int version,
        String label,
        String unit,
        String rawText,
        JsonNode payload,
        List<String> seriesIds,
        String supersedes,
        long createdAt,
        String author
) {

    public boolean appliesTo(String seriesId) {
        return seriesIds == null || seriesIds.isEmpty() || seriesIds.contains(seriesId);
    }
}
