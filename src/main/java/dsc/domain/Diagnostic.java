package dsc.domain;

/** A non-fatal problem attached to a source fragment or derived object. */
public record Diagnostic(String code, String message, String atCoordinate) {
    public static Diagnostic of(String code, String message) {
        return new Diagnostic(code, message, null);
    }
}
