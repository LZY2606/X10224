package dsc.model;

import java.util.Objects;

/** A non-fatal diagnostic attached to an input or derived object. */
public final class Diagnostic {
    public enum Severity { ERROR, WARNING, INFO }

    private final String code;
    private final String message;
    private final Severity severity;

    public Diagnostic(String code, String message, Severity severity) {
        this.code = Objects.requireNonNull(code);
        this.message = Objects.requireNonNull(message);
        this.severity = Objects.requireNonNull(severity);
    }

    public static Diagnostic error(String code, String message) {
        return new Diagnostic(code, message, Severity.ERROR);
    }

    public static Diagnostic warning(String code, String message) {
        return new Diagnostic(code, message, Severity.WARNING);
    }

    public static Diagnostic info(String code, String message) {
        return new Diagnostic(code, message, Severity.INFO);
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public Severity severity() {
        return severity;
    }

    @Override
    public String toString() {
        return "[" + severity + ":" + code + "] " + message;
    }
}
