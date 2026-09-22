package dsc.store;

/** 两位使用者从同一版本编辑：baseSeq 已落后，调用方必须先解决冲突再提交。 */
public class ConcurrentEditException extends RuntimeException {

    private final long expected;
    private final long actual;

    public ConcurrentEditException(String branch, long expected, long actual) {
        super("并发编辑冲突（分支 " + branch + "）：基于 #" + expected
                + " 的修改无法直接应用，当前已到 #" + actual + "，请先解决冲突");
        this.expected = expected;
        this.actual = actual;
    }

    public long expected() {
        return expected;
    }

    public long actual() {
        return actual;
    }
}
