package dsc.storage;

/**
 * Two editors based their edit on the same history version. The second writer
 * receives this resumable conflict instead of silently overwriting the first.
 */
public class ConflictException extends StorageException {
    private final long expected;
    private final long actual;

    public ConflictException(long expected, long actual) {
        super("optimistic lock conflict: base " + expected + " != current " + actual);
        this.expected = expected;
        this.actual = actual;
    }

    public long expected() { return expected; }
    public long actual() { return actual; }
}
