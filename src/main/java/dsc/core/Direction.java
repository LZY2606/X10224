package dsc.core;

/** 扫描方向。绝不允许从文件名猜测；只能来自仪器元数据或扫描速率符号。 */
public enum Direction {
    HEATING, COOLING, UNKNOWN;

    public static Direction fromRate(Double rateKPerMin) {
        if (rateKPerMin == null) return UNKNOWN;
        if (rateKPerMin > 0) return HEATING;
        if (rateKPerMin < 0) return COOLING;
        return UNKNOWN;
    }
}
