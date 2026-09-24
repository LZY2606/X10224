package dsc.core;

/** 空区间：积分窗口内没有数据点或窗口退化。作为诊断保留，不静默吞掉。 */
public class EmptyIntervalException extends Exception {
    public EmptyIntervalException(String message) { super(message); }
}
