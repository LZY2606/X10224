package org.materials.dsc.domain;

/**
 * 扫描方向来自仪器元数据（程序段方向），绝不从文件名猜测。
 */
public enum Direction {
    HEATING, COOLING, ISOTHERMAL, UNKNOWN;

    public static Direction parse(String s) {
        if (s == null) {
            return UNKNOWN;
        }
        return switch (s.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "heating", "heat", "up", "升温", "加热" -> HEATING;
            case "cooling", "cool", "down", "降温", "冷却" -> COOLING;
            case "isothermal", "hold", "等温" -> ISOTHERMAL;
            default -> UNKNOWN;
        };
    }

    public String label() {
        return switch (this) {
            case HEATING -> "heating";
            case COOLING -> "cooling";
            case ISOTHERMAL -> "isothermal";
            case UNKNOWN -> "unknown";
        };
    }
}
