package dsc.store;

/**
 * 两位使用者从同一版本编辑时抛出的乐观并发冲突，必须由调用方解决
 * （重载最新事件、rebase 后重新提交），不允许静默后写覆盖。
 */
public class ConflictException extends RuntimeException {

    private final int expectedEventId;
    private final int actualEventId;
    private final String competingEditor;

    public ConflictException(String historyId, int expected, int actual, String competingEditor) {
        super("history " + historyId + " changed since edit fence " + expected
                + " (now " + actual + " by " + competingEditor + ")");
        this.expectedEventId = expected;
        this.actualEventId = actual;
        this.competingEditor = competingEditor;
    }

    public int expectedEventId() {
        return expectedEventId;
    }

    public int actualEventId() {
        return actualEventId;
    }

    public String competingEditor() {
        return competingEditor;
    }
}
