package dsc.service;

/** Two users edited the same versioned object from the same base version. */
public class ConflictException extends RuntimeException {
    public final String objectId;
    public final int expectedVersion;
    public final int actualVersion;
    public ConflictException(String objectId, int expected, int actual) {
        super("conflict on " + objectId + ": expected v" + expected + " but found v" + actual);
        this.objectId = objectId; this.expectedVersion = expected; this.actualVersion = actual;
    }
}
