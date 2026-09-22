package dsc.domain;

import java.util.Map;

/**
 * A machine readable judgement attached to a source or a derived artifact.
 *
 * @param code     stable diagnostic code, e.g. NON_UNIFORM_SPACING
 * @param severity INFO / WARNING / ERROR
 * @param message  human readable explanation (English, stable wording)
 * @param rawValue the offending original representation (never normalized away)
 * @param ref      stable identity of the related object, if any
 */
public record Diagnostic(String code, Types.Severity severity, String message, String rawValue, String ref) {
    public Map<String, Object> toMap() {
        return Map.of(
                "code", code,
                "severity", severity.name(),
                "message", message,
                "rawValue", rawValue == null ? "" : rawValue,
                "ref", ref == null ? "" : ref);
    }

    public static Diagnostic of(String code, Types.Severity severity, String message, String rawValue, String ref) {
        return new Diagnostic(code, severity, message, rawValue, ref);
    }
}
