package dsc.store;

/** Optimistic-concurrency failure: two users edited from the same version. */
public class ConflictException extends RuntimeException {
    public ConflictException(String msg) { super(msg); }
}
