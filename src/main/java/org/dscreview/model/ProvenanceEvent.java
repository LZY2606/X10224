package org.dscreview.model;

import java.util.List;
import java.util.Map;
import static org.dscreview.model.DomainEnums.EventKind;

/**
 * A machine-readable judgment/action in the review history.
 * The {@code judgments} map carries rules such as "sign.not-guessed-from-filename".
 * {@code accepted} and {@code rejected} keep both adopted and un-adopted results.
 */
public record ProvenanceEvent(
        long seq,
        String eventId,
        EventKind kind,
        String actor,
        String timestamp,
        String ruleVersion,
        Map<String, Object> detail,
        List<String> accepted,
        List<String> rejected,
        List<String> affected) {
}
