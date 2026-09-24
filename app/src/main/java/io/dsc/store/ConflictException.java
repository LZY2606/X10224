package io.dsc.store;

/** Two users edited from the same branch version; caller must resolve. */
public class ConflictException extends RuntimeException {
    public final String branchId;
    public final long expectedSeq;
    public final long actualSeq;

    public ConflictException(String branchId, long expectedSeq, long actualSeq) {
        super("conflict on " + branchId + ": expected head " + expectedSeq + " but is " + actualSeq);
        this.branchId = branchId;
        this.expectedSeq = expectedSeq;
        this.actualSeq = actualSeq;
    }
}
