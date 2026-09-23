package dsc;

import java.util.List;
import java.util.Map;

public record EventRecord(
        long seq,
        String eventId,
        String eventType,
        String actor,
        long baseRevision,
        Map<String, Object> payload
) {
}

record SourceRecord(
        long seq,
        String eventId,
        String sourceId,
        String sourceType,
        int version,
        String segmentId,
        Map<String, Object> payload
) {
}

record DerivationRecord(
        long seq,
        String derivationId,
        String stableId,
        String kind,
        String segmentId,
        String subject,
        int version,
        String status,
        Map<String, Object> dependsOn,
        Map<String, Object> result,
        List<Map<String, Object>> diagnostics
) {
}

record ConflictRecord(String eventId, long atRevision, Map<String, Object> attempted, String retainedDerivationId) {
}
