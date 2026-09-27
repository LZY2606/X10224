package dsc.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** 扫描方向：只允许来自仪器元数据声明，绝不允许来自文件名猜测。 */
public enum Direction {
    HEATING("heating"),
    COOLING("cooling"),
    UNKNOWN("unknown");

    private final String code;

    Direction(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public static Direction fromCode(String s) {
        if (s == null) return UNKNOWN;
        return switch (s.trim().toLowerCase()) {
            case "heating", "heat", "up", "升温", "加热" -> HEATING;
            case "cooling", "cool", "down", "降温", "冷却" -> COOLING;
            default -> UNKNOWN;
        };
    }
}
