package dsc.core;

import java.util.Locale;

/**
 * 领域量：数值 + 单位 + 坐标基准（如热流的吸热方向约定）。
 * 导入层保留原始表示；派生层用 normalize 做规范化。
 * 无法转换的值返回 diagnostic，原值留在原位置。
 */
public record Quantity(double value, String unit, String basis) {

    public record NormResult(Double canonicalValue, String canonicalUnit, String diagnostic) {
        public boolean ok() { return diagnostic == null; }
    }

    /** 规范化到 canonical 单位；无法转换时 diagnostic 非空且 canonicalValue 为 null。 */
    public static NormResult normalize(double value, String unit, String dimension) {
        String u = unit == null ? "" : unit.trim();
        switch (dimension) {
            case "temperature" -> {
                return switch (canon(u)) {
                    case "c", "degc" -> new NormResult(value, "C", null);
                    case "k" -> new NormResult(value - 273.15, "C", null);
                    default -> new NormResult(null, "C", "UNCONVERTIBLE_UNIT: temperature '" + unit + "'");
                };
            }
            case "heatflow" -> {
                return switch (canon(u)) {
                    case "mw", "mj/s" -> new NormResult(value, "mW", null);
                    case "w", "j/s" -> new NormResult(value * 1000.0, "mW", null);
                    case "uw" -> new NormResult(value / 1000.0, "mW", null);
                    default -> new NormResult(null, "mW", "UNCONVERTIBLE_UNIT: heatflow '" + unit + "'");
                };
            }
            case "mass" -> {
                return switch (canon(u)) {
                    case "mg" -> new NormResult(value, "mg", null);
                    case "g" -> new NormResult(value * 1000.0, "mg", null);
                    case "ug" -> new NormResult(value / 1000.0, "mg", null);
                    default -> new NormResult(null, "mg", "UNCONVERTIBLE_UNIT: mass '" + unit + "'");
                };
            }
            case "scanrate" -> {
                return switch (canon(u)) {
                    case "k/min", "c/min" -> new NormResult(value, "K/min", null);
                    case "k/s", "c/s" -> new NormResult(value * 60.0, "K/min", null);
                    case "k/h" -> new NormResult(value / 60.0, "K/min", null);
                    default -> new NormResult(null, "K/min", "UNCONVERTIBLE_UNIT: scanrate '" + unit + "'");
                };
            }
            default -> throw new IllegalArgumentException("unknown dimension " + dimension);
        }
    }

    private static String canon(String u) {
        return u.toLowerCase(Locale.ROOT).replace("°", "").replace(" ", "");
    }
}
