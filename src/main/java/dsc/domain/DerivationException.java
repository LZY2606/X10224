package dsc.domain;

/** A requested derivation that cannot be performed (for example an empty interval). */
public final class DerivationException extends RuntimeException {
    public final String code;

    public DerivationException(String code, String message) {
        super(message);
        this.code = code;
    }
}
