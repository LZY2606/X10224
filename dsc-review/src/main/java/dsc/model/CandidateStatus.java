package dsc.model;

/**
 * Lifecycle of a baseline candidate or derived result.
 * Adoption is explicit; alternatives are retained forever (never overwritten).
 */
public enum CandidateStatus {
    ALTERNATIVE, ADOPTED, REJECTED, STALE
}
