package dsc.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An append-only, machine-readable fact in the review ledger. Every input
 * import, derivation, acceptance, rejection and parameter edit is an event;
 * historical views replay the ledger up to a chosen event.
 */
public record LedgerEvent(
        String id,
        long seq,
        String type,
        String actor,
        String createdAt,
        String caseRef,
        String ruleVersion,
        String derivedVersionId,
        Map<String, String> refs,
        String payloadJson) {

    public LedgerEvent {
        refs = refs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(refs);
    }

    public String ref(String key) {
        return refs.get(key);
    }
}
