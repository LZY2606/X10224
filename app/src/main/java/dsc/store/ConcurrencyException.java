package dsc.store;

/** Two users edited the same version: expected seq/head no longer matches. */
public final class ConcurrencyException extends RuntimeException {
    private final String expectedHead;
    private final String actualHead;

    public ConcurrencyException(String projectId, String expectedHead, String actualHead) {
        super("Optimistic-lock conflict on " + projectId + ": expected head "
                + expectedHead + ", actual " + actualHead);
        this.expectedHead = expectedHead;
        this.actualHead = actualHead;
    }

    public String expectedHead() { return expectedHead; }
    public String actualHead() { return actualHead; }
}
