package dsc.core;

/** Thrown when a value cannot be normalized. Callers keep the original value and attach a diagnostic. */
public class UnconvertibleValueException extends RuntimeException {
    private final String unit;

    public UnconvertibleValueException(String message, String unit) {
        super(message);
        this.unit = unit;
    }

    public String unit() {
        return unit;
    }
}
