package dsc.core;

/**
 * A machine-readable judgment. {@code code} is stable, {@code basis} cites the
 * rule/version and inputs that produced it, {@code severity} distinguishes a
 * hard error from an information note.
 */
public record Diagnostic(
        DiagnosticCode code,
        DiagnosticSeverity severity,
        String message,
        String basis,
        java.util.List<String> affected
) {

    public static Diagnostic of(DiagnosticCode code, String message, String basis, String... affected) {
        return new Diagnostic(code, code.severity(), message, basis, java.util.List.of(affected));
    }

    public static Diagnostic of(DiagnosticCode code, String message, String basis,
                                java.util.List<String> affected) {
        return new Diagnostic(code, code.severity(), message, basis, affected);
    }
}
