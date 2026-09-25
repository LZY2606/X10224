package dsc.model;

import java.util.Locale;

/**
 * 领域单位换算。导入层只做解析与校验，规范化系数集中在此。
 */
public final class Units {

    private Units() {
    }

    /** 温度统一以 K 作为内部规范化基准，显示时保留原始单位。 */
    public static double toKelvin(double value, String unit) {
        return switch (unit.trim().toLowerCase(Locale.ROOT)) {
            case "k" -> value;
            case "°c", "c", "degc" -> value + 273.15;
            default -> throw new IllegalArgumentException("unsupported temperature unit: " + unit);
        };
    }

    /** 热流规范化到 mW（毫瓦）。支持 W/mW/uW 及常见每质量写法（质量归一化在派生层处理）。 */
    public static double heatFlowToMilliWatt(double value, String unit) {
        String u = unit.trim().toLowerCase(Locale.ROOT);
        return switch (u) {
            case "w" -> value * 1000.0;
            case "mw" -> value;
            case "uw", "µw" -> value / 1000.0;
            default -> throw new IllegalArgumentException("unsupported heat-flow unit: " + unit);
        };
    }

    public static double massToMilligram(double value, String unit) {
        String u = unit.trim().toLowerCase(Locale.ROOT);
        return switch (u) {
            case "mg" -> value;
            case "g" -> value * 1000.0;
            case "kg" -> value * 1_000_000.0;
            case "ug", "µg" -> value / 1000.0;
            default -> throw new IllegalArgumentException("unsupported mass unit: " + unit);
        };
    }

    /** 扫描速率规范化到 K/min。 */
    public static double rateToKelvinPerMinute(double value, String unit) {
        String u = unit.trim().toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("°", "");
        return switch (u) {
            case "k/min", "c/min" -> value;
            case "k/s", "c/s" -> value * 60.0;
            default -> throw new IllegalArgumentException("unsupported scan-rate unit: " + unit);
        };
    }
}
