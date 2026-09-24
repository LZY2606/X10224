package dsc.domain;

import java.util.List;
import java.util.OptionalDouble;

/**
 * 所有领域量携带单位。导入层只解析、不静默丢值；
 * 规范化只发生在派生层，无法转换的值留在原位置并附诊断。
 */
public final class Units {
    private Units() {}

    public record Q(double value, String unit) {}

    public static final List<String> TEMP = List.of("°C", "C", "K", "℃");
    public static final List<String> MASS = List.of("mg", "g", "kg", "ug", "µg");
    public static final List<String> RATE = List.of("K/min", "°C/min", "C/min", "K/s", "°C/s");
    public static final List<String> HF = List.of("mW", "W", "mW/g", "W/g");
    public static final List<String> TIME = List.of("s", "min", "ms");
    public static final List<String> ENTHALPY = List.of("J/g", "kJ/kg", "mJ/mg", "J/kg");

    /** 转摄氏度；不可转换返回空。 */
    public static OptionalDouble toCelsius(double v, String unit) {
        String u = unit == null ? "" : unit.trim();
        return switch (u) {
            case "°C", "C", "℃" -> OptionalDouble.of(v);
            case "K" -> OptionalDouble.of(v - 273.15);
            default -> OptionalDouble.empty();
        };
    }

    /** 热流统一到 mW（样品总量口径）；比热流口径需要质量，规范化阶段再处理。 */
    public static OptionalDouble toMilliWatt(double v, String unit) {
        String u = unit == null ? "" : unit.trim();
        return switch (u) {
            case "mW" -> OptionalDouble.of(v);
            case "W" -> OptionalDouble.of(v * 1000.0);
            default -> OptionalDouble.empty();
        };
    }

    public static OptionalDouble toSeconds(double v, String unit) {
        String u = unit == null ? "" : unit.trim();
        return switch (u) {
            case "s" -> OptionalDouble.of(v);
            case "min" -> OptionalDouble.of(v * 60.0);
            case "ms" -> OptionalDouble.of(v / 1000.0);
            default -> OptionalDouble.empty();
        };
    }

    public static OptionalDouble toMgPerSample(double v, String unit) {
        String u = unit == null ? "" : unit.trim();
        return switch (u) {
            case "mg" -> OptionalDouble.of(v);
            case "g" -> OptionalDouble.of(v * 1000.0);
            case "kg" -> OptionalDouble.of(v * 1_000_000.0);
            case "ug", "µg" -> OptionalDouble.of(v / 1000.0);
            default -> OptionalDouble.empty();
        };
    }

    /** 扫描速率统一到 K/min（摄氏度温差与 K 等价），保留方向符号。 */
    public static OptionalDouble toKPerMin(double v, String unit) {
        String u = unit == null ? "" : unit.trim();
        return switch (u) {
            case "K/min", "°C/min", "C/min" -> OptionalDouble.of(v);
            case "K/s", "°C/s" -> OptionalDouble.of(v * 60.0);
            default -> OptionalDouble.empty();
        };
    }

    public static boolean known(List<String> allowed, String unit) {
        return unit != null && allowed.contains(unit.trim());
    }
}
