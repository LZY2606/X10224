package dsc.domain;

import java.util.List;
import java.util.Map;

/**
 * One immutable, versioned direct source.
 *
 * <p>Each of instrument output, mass, scan rate, atmosphere, temperature
 * calibration and user-selected tangent is archived separately with its own
 * version chain, so a change to one propagates only through its own dependents.
 */
public record SourceRecord(
        String id,
        String scopeId,
        Types.SourceKind kind,
        int version,
        String replacedBy,
        String effectiveEventId,
        String supersededEventId,
        String rawText,
        String unit,
        Map<String, Object> parsed,
        List<Diagnostic> diagnostics,
        String contentHash,
        String parentHash) {

    public boolean isCurrent(String pointerId) {
        return replacedBy == null && pointerId.equals(id);
    }
}
