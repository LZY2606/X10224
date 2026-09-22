package dsc.store;

/** 测试用：模拟写入中断。 */
public class SimulatedCrashException extends RuntimeException {
    public SimulatedCrashException(String message) {
        super(message);
    }
}
