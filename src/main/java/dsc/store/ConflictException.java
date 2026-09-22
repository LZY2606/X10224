package dsc.store;

/**
 * Raised when two users edit from the same base version. The caller can resolve it by
 * rebasing onto the returned current version and reapplying the decision — the losing
 * write is rejected outright rather than silently merged.
 */
public final class ConflictException extends RuntimeException {
    public final String entity;
    public final long expectedVersion;
    public final long actualVersion;

    public ConflictException(String entity, long expected, long actual) {
        super("optimistic-lock conflict on " + entity + ": expected seq " + expected
                + ", actual " + actual);
        this.entity = entity;
        this.expectedVersion = expected;
        this.actualVersion = actual;
    }
}
