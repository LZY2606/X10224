package org.dscreview.model;

/** A structured diagnostic attached to a source or derived object. */
public record Diagnostic(String code, String severity, String message, String atStableId) {
    public static Diagnostic of(String code, String severity, String message) {
        return new Diagnostic(code, severity, message, null);
    }
}
