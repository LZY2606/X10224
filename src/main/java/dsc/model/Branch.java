package dsc.model;

/** A lineage in the event tree. Unchosen branches are retained, never overwritten. */
public record Branch(
        String branchRoot,
        String projectId,
        String label,
        String tipEventId,
        long tipSeq,
        String status,
        String forkedFromEventId
) {
}
