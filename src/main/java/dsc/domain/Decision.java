package dsc.domain;

/**
 * A human adjudication over baseline candidates / analysis branches.
 * Conflicting decisions are stored as separate rows carrying the version they
 * are based on; the review UI forces explicit resolution instead of last-write.
 */
public record Decision(
        String id,
        String transitionId,
        String eventId,
        String chosenCandidateId,
        String rejectedCandidateIds,
        String action,
        String author,
        String basedOnDerivedVersionId,
        String conflictsWithDecisionId,
        String resolution,
        String comment) {
}
