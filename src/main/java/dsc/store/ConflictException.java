package dsc.store;

/** 两位使用者从同一修订版本编辑时抛出，携带服务端当前版本以便解决冲突。 */
public class ConflictException extends RuntimeException {
    public final long serverRevision;

    public ConflictException(long expected, long actual) {
        super("并发修改冲突：基于修订 " + expected + " 的提交已过期，服务端为 " + actual);
        this.serverRevision = actual;
    }
}
