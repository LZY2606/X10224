package dsc.core;

/**
 * A non-fatal or fatal data-quality finding. Diagnostics stay attached to the
 * object they belong to; an unconvertible value is retained in place rather
 * than silently dropped for plotting.
 */
public record Diagnostic(String code, String severity, String message, String ref) {
    public static final String INFO = "info";
    public static final String WARN = "warn";
    public static final String ERROR = "error";

    public static Diagnostic info(String code, String msg, String ref) {
        return new Diagnostic(code, INFO, msg, ref);
    }
    public static Diagnostic warn(String code, String msg, String ref) {
        return new Diagnostic(code, WARN, msg, ref);
    }
    public static Diagnostic error(String code, String msg, String ref) {
        return new Diagnostic(code, ERROR, msg, ref);
    }
}
