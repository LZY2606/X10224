package dsc.store;

/**
 * Hook used by tests to simulate a process crash in the middle of a commit.
 * When {@code afterEventInsert} throws, the store leaves the transaction uncommitted
 * (as a killed process would); a fresh store on the same file must see the old version.
 */
public interface WriteBarrier {
    WriteBarrier NOOP = phase -> { };

    void onPhase(String phase) throws Exception;

    default WriteBarrier andThen(WriteBarrier next) {
        return phase -> {
            this.onPhase(phase);
            next.onPhase(phase);
        };
    }
}
