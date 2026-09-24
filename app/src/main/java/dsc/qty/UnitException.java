package dsc.qty;

/** Thrown when an unconvertible value is asked to convert; original is retained by the caller. */
public class UnitException extends RuntimeException {
    public UnitException(String message) {
        super(message);
    }
}
