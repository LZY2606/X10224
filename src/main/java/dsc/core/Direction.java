package dsc.core;

/** 扫描方向。绝不从文件名猜测；必须来自仪器元数据显式字段。 */
public enum Direction {
    HEATING("heating", "加热"),
    COOLING("cooling", "冷却"),
    UNKNOWN("unknown", "未知");

    public final String code;
    public final String label;

    Direction(String code, String label) { this.code = code; this.label = label; }

    public static Direction fromMeta(String raw) {
        if (raw == null) return UNKNOWN;
        String s = raw.trim().toLowerCase();
        return switch (s) {
            case "heating", "heat", "up", "升温", "加热" -> HEATING;
            case "cooling", "cool", "down", "降温", "冷却" -> COOLING;
            default -> UNKNOWN;
        };
    }

    /** 依据温度序列实际单调性核验声明方向。 */
    public boolean agreesWith(double[] tempNormalized) {
        int up = 0, down = 0;
        for (int i = 1; i < tempNormalized.length; i++) {
            if (tempNormalized[i] > tempNormalized[i - 1]) up++;
            else if (tempNormalized[i] < tempNormalized[i - 1]) down++;
        }
        Direction actual = up >= down * 3 ? HEATING : down >= up * 3 ? COOLING : UNKNOWN;
        return actual == UNKNOWN ? false : actual == this;
    }
}
