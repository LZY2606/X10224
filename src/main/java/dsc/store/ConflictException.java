package dsc.store;

/** 两人从同一版本编辑且触及相同对象时抛出；冲突双方都被保留，可解决。 */
public class ConflictException extends RuntimeException {
    public final String conflictId;
    public ConflictException(String conflictId, String message) {
        super(message);
        this.conflictId = conflictId;
    }
}
