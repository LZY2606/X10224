package io.dsc.model;

import java.util.List;

/**
 * A named line of decisions. The initial branch is "main". When two reviewers
 * make conflicting legitimate choices (e.g. adopt different baseline families)
 * each choice lives on its own branch; both are retained after resolution.
 */
public record Branch(
        String branchId,
        String caseId,
        String name,
        String parentBranchId,
        Long forkAfterSeq,          // seq of parent event forked from, null for main
        String resolution,          // null / CONTINUES / SUPERSEDED
        String resolutionEventId,
        List<String> tags
) {
    public boolean isResolved() { return resolution != null; }
}
