package dsc.model;

/**
 * 温度程序方向。方向来自仪器元数据/温度标定记录，
 * 绝不能从文件名猜测（见 replay 的 reverse-heatflow 案例）。
 */
public enum ScanDirection {
    HEATING("加热", +1),
    COOLING("冷却", -1),
    ISOTHERMAL("等温", 0);

    private final String label;
    private final int sign;

    ScanDirection(String label, int sign) {
        this.label = label;
        this.sign = sign;
    }

    public String label() {
        return label;
    }

    public int sign() {
        return sign;
    }

    public static ScanDirection fromMetadata(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("scan direction metadata missing");
        }
        return switch (raw.trim().toLowerCase()) {
            case "heating", "heat", "up", "加热" -> HEATING;
            case "cooling", "cool", "down", "冷却" -> COOLING;
            case "isothermal", "iso", "等温" -> ISOTHERMAL;
            default -> throw new IllegalArgumentException("unknown scan direction: " + raw);
        };
    }
}
