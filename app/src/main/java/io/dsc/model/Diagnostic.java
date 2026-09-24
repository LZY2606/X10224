package io.dsc.model;

/**
 * A machine-readable diagnostic attached to an input fragment, interval or
 * derivation. Diagnostics never silently drop data.
 */
public record Diagnostic(
        String id,             // stable within case, e.g. diag:heat1:reverse-sign
        String code,
        Severity severity,
        String targetKind,     // interval / segment / baseline / transition
        String targetId,
        String message,
        String producedByEventId
) {
    public enum Severity { INFO, WARNING, ERROR }
}
