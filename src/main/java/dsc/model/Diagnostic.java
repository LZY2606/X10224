package dsc.model;

/**
 * A machine-readable note attached to a value that could not be normalized
 * or to a rule violation. The raw value stays in place; nothing is dropped
 * just to make plotting easier.
 */
public final class Diagnostic {
    public final String code;
    public final String message;
    public final String location;

    public Diagnostic(String code, String message, String location) {
        this.code = code;
        this.message = message;
        this.location = location;
    }

    @Override
    public String toString() {
        return code + "@" + location + ": " + message;
    }
}
