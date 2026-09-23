package dsc.model;

import java.util.List;

/**
 * Append-only journal entry. Every user action, automated derivation and
 * decision (including rejected ones) is recorded here. parentSeq links events
 * within a branch; basis lists the exact input revisions consumed; outputs
 * lists produced/affected object ids. Time-travel reconstructs state by
 * replaying up to any seq.
 */
public record JournalEvent(
        long seq,
        String branchId,
        long parentSeq,
        EventKind kind,
        DecisionState state,
        String actor,
        long timestampMs,
        String summary,
        String payloadJson,
        List<RevisionRef> basis,
        List<String> outputs,
        String baseRevision  // workspace revision this action started from (optimistic lock)
) {}
