package dsc.model;

/**
 * An independently versioned derived object (parsed curve, segment map, baseline
 * candidate, transition fit, raw integral, normalized enthalpy...). Every candidate
 * produced by any branch is retained; acceptance is an event-level decision, never a
 * destructive overwrite.
 */
public record Derivation(
        String derivationId,
        String projectId,
        String kind,
        String intervalId,
        String status,
        String ruleVersion,
        String inputsJson,
        String payloadJson,
        String errorCode,
        String producingEventId,
        long producingSeq,
        String branchRoot,
        String actor,
        long createdAtMs
) {
}
