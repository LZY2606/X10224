package io.dscreview.domain;

/**
 * A machine-readable judgement attached to a derivation. Codes are stable so
 * the review UI and replay tests can assert on them.
 */
public record Diagnostic(
        Severity severity,
        String code,
        String message,
        String objectRef,
        String detail) {

    public static Diagnostic error(String code, String message) {
        return new Diagnostic(Severity.ERROR, code, message, null, null);
    }

    public static Diagnostic warning(String code, String message) {
        return new Diagnostic(Severity.WARNING, code, message, null, null);
    }

    public static Diagnostic info(String code, String message) {
        return new Diagnostic(Severity.INFO, code, message, null, null);
    }

    public Diagnostic withRef(String ref) {
        return new Diagnostic(severity, code, message, ref, detail);
    }

    public Diagnostic withDetail(String d) {
        return new Diagnostic(severity, code, message, objectRef,
                detail == null ? d : detail + "; " + d);
    }

    public String key() {
        return String.format(Locale.ROOT, "%s|%s|%s",
                code, objectRef == null ? "" : objectRef, detail == null ? "" : detail);
    }
}
