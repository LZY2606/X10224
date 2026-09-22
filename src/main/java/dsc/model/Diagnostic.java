package dsc.model;

import java.util.Map;

/** A diagnostic attached to a derived result; severity is never silently dropped. */
public record Diagnostic(String code, String severity, String message, Map<String, Object> detail) {
    public Map<String, Object> toMap() {
        return Map.of("code", code, "severity", severity, "message", message, "detail", detail);
    }
}
