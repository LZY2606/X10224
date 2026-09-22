package dsc.domain;

import java.util.OptionalDouble;

/**
 * 领域单位与坐标基准。所有领域量都携带单位；原始层保留原始表示，
 * 派生层（normalizeX）才转换为内部基准。无法转换时返回 empty 并保留原值与诊断。
 */
public final class Units {

    private Units() {
    }

    /** 内部统一温度坐标：开尔文。 */
    public static final String TEMP_CANONICAL = "K";
    /** 内部统一热流坐标：瓦/克（质量归一化放在派生层；曲线原始热流使用 mW 或 W/g）。 */
    public static final String HEATFLOW_CANONICAL = "W/g";
    /** 积分焓内部基准：焦/克。 */
    public static final String ENTHALPY_CANONICAL = "J/g";
    public static final String RATE_CANONICAL = "K/min";

    /**
     * 温度转换到 K。支持 K、°C/C、F。无法识别返回 empty，调用方必须在原位置保留原始值与诊断。
     */
    public static OptionalDouble normalizeTemperature(double value, String unit) {
        if (unit == null) {
            return OptionalDouble.empty();
        }
        switch (unit.trim().toLowerCase()) {
            case "k":
            case "kelvin":
                return OptionalDouble.of(value);
            case "c":
            case "°c":
            case "degc":
            case "celsius":
                return OptionalDouble.of(value + 273.15);
            case "f":
            case "°f":
            case "fahrenheit":
                return OptionalDouble.of((value - 32.0) * 5.0 / 9.0 + 273.15);
            default:
                return OptionalDouble.empty();
        }
    }

    public static double toCelsius(double kelvin) {
        return kelvin - 273.15;
    }

    /**
     * 热流转换到 W/g。样品质量在原始热流未归一化（mW、W）时才需要；
     * 原始即 W/g、mW/mg、J/(g·s) 等比热单位时直接换算。
     */
    public static OptionalDouble normalizeHeatFlow(double value, String unit, double massGrams) {
        if (unit == null) {
            return OptionalDouble.empty();
        }
        String u = unit.trim().toLowerCase();
        switch (u) {
            case "w/g":
                return OptionalDouble.of(value);
            case "mw/mg":
                return OptionalDouble.of(value); // 1 mW/mg = 1 W/g
            case "j/(g*s)":
            case "j/(g.s)":
            case "j/g/s":
                return OptionalDouble.of(value);
            case "mw":
                if (massGrams <= 0) {
                    return OptionalDouble.empty();
                }
                return OptionalDouble.of(value / 1000.0 / massGrams);
            case "w":
                if (massGrams <= 0) {
                    return OptionalDouble.empty();
                }
                return OptionalDouble.of(value / massGrams);
            case "µw":
            case "uw":
                if (massGrams <= 0) {
                    return OptionalDouble.empty();
                }
                return OptionalDouble.of(value / 1.0e6 / massGrams);
            default:
                return OptionalDouble.empty();
        }
    }

    /** 扫描速率统一到 K/min（升温速率的数值在 K/min 与 °C/min 相同）。 */
    public static OptionalDouble normalizeRate(double value, String unit) {
        if (unit == null) {
            return OptionalDouble.empty();
        }
        switch (unit.trim().toLowerCase()) {
            case "k/min":
            case "°c/min":
            case "c/min":
                return OptionalDouble.of(value);
            case "k/s":
                return OptionalDouble.of(value * 60.0);
            default:
                return OptionalDouble.empty();
        }
    }

    public static String describe(String unit) {
        return unit == null ? "?" : unit;
    }
}
