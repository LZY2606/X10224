package org.materials.dsc.domain;

/**
 * 单位换算入口。规范基准：
 * 温度 K（同时保留 °C 原始表示）、质量 mg、热流 mW、扫描速率 K/min、时间 s、焓 J/g。
 */
public final class Units {

    public static final String KELVIN = "K";
    public static final String CELSIUS = "°C";
    public static final String MILLIGRAM = "mg";
    public static final String GRAM = "g";
    public static final String MILLIWATT = "mW";
    public static final String WATT = "W";
    public static final String K_PER_MIN = "K/min";
    public static final String SECOND = "s";
    public static final String MINUTE = "min";
    public static final String JOULE_PER_GRAM = "J/g";
    public static final String KILOJOULE_PER_MOLE = "kJ/mol";

    private Units() {
    }

    /** 解析温度；支持 K 与 °C，规范值为 K。 */
    public static Quantity parseTemperature(String raw, String unit) {
        return Quantity.parse(raw, unit, KELVIN, (v, u) -> switch (u) {
            case "K", "kelvin" -> v;
            case "C", "°C", "degC", "celsius" -> v + 273.15;
            default -> throw new IllegalArgumentException("unknown temperature unit: " + u);
        });
    }

    public static Quantity parseMass(String raw, String unit) {
        return Quantity.parse(raw, unit, MILLIGRAM, (v, u) -> switch (u) {
            case "mg" -> v;
            case "g" -> v * 1000.0;
            case "kg" -> v * 1_000_000.0;
            case "ug", "µg" -> v / 1000.0;
            default -> throw new IllegalArgumentException("unknown mass unit: " + u);
        });
    }

    public static Quantity parseHeatFlow(String raw, String unit) {
        return Quantity.parse(raw, unit, MILLIWATT, (v, u) -> switch (u) {
            case "mW" -> v;
            case "W" -> v * 1000.0;
            case "uW", "µW" -> v / 1000.0;
            default -> throw new IllegalArgumentException("unknown heat-flow unit: " + u);
        });
    }

    public static Quantity parseRate(String raw, String unit) {
        return Quantity.parse(raw, unit, K_PER_MIN, (v, u) -> switch (u) {
            case "K/min", "°C/min", "C/min" -> v;
            case "K/s", "°C/s", "C/s" -> v * 60.0;
            default -> throw new IllegalArgumentException("unknown scan-rate unit: " + u);
        });
    }

    public static Quantity parseTime(String raw, String unit) {
        return Quantity.parse(raw, unit, SECOND, (v, u) -> switch (u) {
            case "s", "sec" -> v;
            case "min" -> v * 60.0;
            case "ms" -> v / 1000.0;
            default -> throw new IllegalArgumentException("unknown time unit: " + u);
        });
    }

    /** K -> °C，仅用于展示；坐标基准永远保留 K。 */
    public static double kelvinToCelsius(double kelvin) {
        return kelvin - 273.15;
    }
}
