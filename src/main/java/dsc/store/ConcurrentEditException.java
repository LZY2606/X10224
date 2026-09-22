package dsc.store;

/** 两位使用者从同一基准编辑且事件序号冲突时抛出，必须由调用方解决（不能静默覆盖）。 */
public class ConcurrentEditException extends RuntimeException {
    public final long expectedSeq;
    public final long actualSeq;

    public ConcurrentEditException(long expectedSeq, long actualSeq) {
        super("concurrent edit: expected base seq " + expectedSeq + " but db is at " + actualSeq);
        this.expectedSeq = expectedSeq;
        this.actualSeq = actualSeq;
    }
}
