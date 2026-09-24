package dsc.domain;

import java.util.List;
import java.util.Map;

/** Data carriers shared by store, rules and web layers. */
public final class Records {
    private Records() {}

    public record Source(String id, String sampleId, String kind, int version,
                         String raw, String mediaType, String hash,
                         int createdEvent, String createdAt) {}

    public record RawPoint(int segIndex, int seq, Double timeRaw, String timeUnit,
                           Double tempRaw, String tempUnit, Double hfRaw, String hfUnit) {}

    public record Diagnostic(String code, String message,
                             Double tempC, String basis, Map<String, Object> detail) {
        public Diagnostic(String code, String message) {
            this(code, message, null, null, Map.of());
        }
        public Diagnostic(String code, String message, Double tempC, String basis) {
            this(code, message, tempC, basis, Map.of());
        }
    }

    public record Dependency(String kind, String id, int version) {}

    public record Derivation(String id, String sampleId, String kind, int version, String slot,
                             String status, boolean accepted,
                             String params, String result, List<Diagnostic> diagnostics,
                             List<Dependency> deps, int createdEvent, String createdAt,
                             boolean complete) {}

    public record Conflict(String id, String sampleId, String slot, String baseDerivationId,
                           String branchAId, String branchAReviewer,
                           String branchBId, String branchBReviewer,
                           String status, String keptBranch,
                           int createdEvent, int resolvedEvent, String createdAt) {}
}
