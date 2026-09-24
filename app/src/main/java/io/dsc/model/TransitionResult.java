package io.dsc.model;

import java.util.List;

/**
 * Characterised thermal event: glass transition, crystallisation or melt.
 * Every reported number is a DerivedValue with its own stable identity and a
 * provenance chain back to source versions, rule version and decision event.
 */
public record TransitionResult(
        StableId id,
        String caseId,
        String slot,
        String transitionType,   // glass / crystallisation / melting
        ScanDirection direction,
        String adoptedBaselineId,
        DerivedValue onset,
        DerivedValue peak,
        DerivedValue endset,
        DerivedValue enthalpy,          // J, unnormalised
        DerivedValue specificEnthalpy,  // J/g, expired when mass revision lands
        DerivedValue glassStep,         // delta Cp for glass, null otherwise
        String allocationKey,           // which legal candidate when overlap is ambiguous, null if unique
        Status status,
        List<ErrorContribution> errorSources,
        List<String> diagnosticIds,
        Derivation derivation
) {
    public enum Status { CURRENT, SUPERSEDED, EXPIRED, REJECTED, AMBIGUOUS }
}
