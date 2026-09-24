package dsc.store;

/**
 * Raised when two editors append from the same parent version and their facts
 * touch the same subject. The store never silently picks one; the second
 * commit must rebase or explicitly resolve, producing a DECISION_CONFLICT /
 * CONFLICT_RESOLVED pair.
 */
public class ConflictException extends RuntimeException {
    public final long expectedParent;
    public final long actualHead;
    public final String subjectId;

    public ConflictException(long expectedParent, long actualHead, String subjectId) {
        super("concurrent edit conflict on '" + subjectId + "': expected parent "
                + expectedParent + ", head is now " + actualHead);
        this.expectedParent = expectedParent;
        this.actualHead = actualHead;
        this.subjectId = subjectId;
    }
}
