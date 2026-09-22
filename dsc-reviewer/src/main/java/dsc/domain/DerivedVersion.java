package dsc.domain;

import java.util.List;
import java.util.Map;

/**
 * One immutable version of a derived artifact (normalized series, analysis
 * window, baseline candidate, transition or enthalpy report).
 *
 * <p>Versions are written inside a single transaction with the event that
 * produces them; the current pointer only moves after the whole commit
 * succeeds.
 */
public record DerivedVersion(
        String id,
        Types.DerivedKind kind,
        String scopeId,
        int version,
        String replacedBy,
        List<Dep> deps,
        Map<String, Object> payload,
        List<Diagnostic> diagnostics,
        String producedByEvent,
        String contentHash) {

    /** Edge in the recomputation dependency graph. */
    public record Dep(String source, Types.SourceKind sourceKind, int sourceVersion) {
    }
}
