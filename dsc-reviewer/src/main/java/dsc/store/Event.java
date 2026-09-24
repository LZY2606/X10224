package dsc.store;

import java.util.List;

/**
 * One append-only fact. {@code seq} is the global replay order; {@code parentSeq}
 * is the version the editor was looking at (optimistic-concurrency base);
 * {@code payload} is JSON; {@code dependsOn} lists the stable ids whose
 * versions this fact was computed from, which is what the dependency graph
 * walks for "why is this still valid / why must it recompute".
 */
public record Event(
        long seq,
        long parentSeq,
        String author,
        EventType type,
        String subjectId,
        String payload,
        List<String> dependsOn,
        String createdAt
) {
}
