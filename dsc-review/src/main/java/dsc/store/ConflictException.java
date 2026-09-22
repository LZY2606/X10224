package dsc.store;

/**
 * Raised when two users edit from the same parent. The caller must resolve by
 * rebasing onto {@code actualParentId} or by creating a branch.
 */
public class ConflictException extends RuntimeException {

    private final String actualParentId;

    public ConflictException(String message, String actualParentId) {
        super(message);
        this.actualParentId = actualParentId;
    }

    public String actualParentId() {
        return actualParentId;
    }
}
