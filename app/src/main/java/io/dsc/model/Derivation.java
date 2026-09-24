package io.dsc.model;

import java.util.List;

/**
 * Provenance of a derived object: which event produced it, which rule version
 * computed it, and which source/derivation versions it depends on.
 */
public record Derivation(
        String producedByEventId,
        String ruleVersion,
        String branchId,
        List<String> sourceVersionIds,
        List<String> derivationVersionIds
) {
    public boolean dependsOnAny(List<String> changedVersionIds) {
        for (String dep : sourceVersionIds) if (changedVersionIds.contains(dep)) return true;
        for (String dep : derivationVersionIds) if (changedVersionIds.contains(dep)) return true;
        return false;
    }
}
