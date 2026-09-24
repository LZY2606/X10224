package dsc.store;

/**
 * Raised when two users append from the same observed basis version. The loser's command is not
 * silently overwritten: callers record a {@code CONFLICT_DETECTED} event and the conflict can be
 * resolved (merge or explicit supersedure), keeping both versions.
 */
public class OptimisticConflictException extends RuntimeException {
    private final String conflictType;

    public OptimisticConflictException(String conflictType, String message) {
        super(message);
        this.conflictType = conflictType;
    }

    public String conflictType() {
        return conflictType;
    }
}
