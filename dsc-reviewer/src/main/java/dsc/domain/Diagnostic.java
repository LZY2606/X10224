package dsc.domain;

import java.util.Map;

/**
 * A machine-readable finding produced while parsing or deriving. Diagnostics
 * never silently erase data: non-convertible points and ambiguous decisions
 * remain visible and block nothing beyond what the {@code severity} declares.
 */
public record Diagnostic(String code, String severity, String message,
                         String subjectRef, Map<String, Object> context) {
    public static final String INFO = "INFO";
    public static final String WARN = "WARN";
    public static final String ERROR = "ERROR";

    public static Diagnostic of(String code, String severity, String message,
                                String subjectRef, Map<String, Object> context) {
        return new Diagnostic(code, severity, message, subjectRef,
                context == null ? Map.of() : Map.copyOf(context));
    }
}
