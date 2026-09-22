package dsc.domain;

import java.util.List;
import java.util.Map;

/**
 * An append-only, machine readable event. Replaying the event stream from zero
 * rebuilds every historical view, so an old event restores the exact
 * dependency versions and visible conclusions at that time.
 */
public record EventRecord(
        long seq,
        String id,
        Types.EventKind kind,
        String scopeId,
        String actor,
        String branch,
        String parentEventId,
        long wallTime,
        Map<String, Object> payload,
        List<String> producedIds,
        String baseRevision,
        String contentHash,
        String prevHash) {
}
