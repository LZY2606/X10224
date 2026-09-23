package dsc.model;

/** A machine-readable diagnostic attached to a source or derivation. */
public record Diagnostic(
        String code,
        DiagnosticLevel level,
        String message,
        String detail
) {
    public static Diagnostic error(String code, String message) {
        return new Diagnostic(code, DiagnosticLevel.ERROR, message, null);
    }
    public static Diagnostic warn(String code, String message) {
        return new Diagnostic(code, DiagnosticLevel.WARNING, message, null);
    }
    public static Diagnostic info(String code, String message) {
        return new Diagnostic(code, DiagnosticLevel.INFO, message, null);
    }
}
