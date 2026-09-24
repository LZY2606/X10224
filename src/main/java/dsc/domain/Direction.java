package dsc.domain;

/** 方向只能由扫描速率/温度轴决定，禁止从文件名猜测。 */
public enum Direction {
    HEATING("加热"),
    COOLING("冷却"),
    UNKNOWN("方向未定");

    private final String label;
    Direction(String label) { this.label = label; }
    public String label() { return label; }

    public static Direction fromRate(double rateKPerMin) {
        if (Double.isNaN(rateKPerMin)) return UNKNOWN;
        if (rateKPerMin > 0) return HEATING;
        if (rateKPerMin < 0) return COOLING;
        return UNKNOWN;
    }
}
