package dsc.domain;

import java.util.List;

/**
 * A derivation set produced from an immutable input snapshot. The current
 * pointer moves to a new version only after the whole set committed;
 * interrupted writes never produce half-visible versions.
 */
public record DerivedVersion(
        String id,
        String scopeKey,
        int number,
        String ruleVersion,
        String producedByEventId,
        String status,
        String parentVersionId,
        List<String> inputHashIds,
        String createdAt) {

    public boolean committed() {
        return "COMMITTED".equals(status);
    }
}
