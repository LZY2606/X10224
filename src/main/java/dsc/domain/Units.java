package dsc.domain;

import java.util.Map;

/**
 * 所有领域量都携带单位或坐标基准。
 * 导入层只保留原始表示；本类属于派生层，负责规范化。
 * 无法转换的量保留原值并产生 {@link ConversionDiagnostic}，绝不丢弃数据。
 */
public final class Units {

    private Units() {
    }

    /** 已知量纲。 */
    public enum Dimension {
        TEMPERATURE, MASS, HEAT_FLOW, RATE, TIME, ENERGY_PER_MASS, DIMENSIONLESS, UNKNOWN
    }

    /** 一次值的规范化结果：原始表示始终保留。 */
    public record Quantity(String rawText, String rawUnit, double rawValue,
                           Double siValue, Dimension dimension,
                           String canonicalUnit, ConversionDiagnostic diagnostic) {

        public boolean convertible() {
            return siValue != null && diagnostic == null;
        }

        public double siOrThrow() {
            if (siValue == null) {
                throw new IllegalStateException("值无法规范化为 SI: " + rawText + " " + rawUnit);
            }
            return siValue;
        }
    }

    public record ConversionDiagnostic(String code, String message) {
    }

    // 线性换算：si = value * slope + intercept
    private record Linear(double slope, double intercept, Dimension dimension, String canonical) {
    }

    private static final Map<String, Linear> CONVERSIONS = Map.ofEntries(
            Map.entry("c", new Linear(1.0, 273.15, Dimension.TEMPERATURE, "K")),
            Map.entry("°c", new Linear(1.0, 273.15, Dimension.TEMPERATURE, "K")),
            Map.entry("k", new Linear(1.0, 0.0, Dimension.TEMPERATURE, "K")),
            Map.entry("mg", new Linear(1e-6, 0.0, Dimension.MASS, "kg")),
            Map.entry("g", new Linear(1e-3, 0.0, Dimension.MASS, "kg")),
            Map.entry("kg", new Linear(1.0, 0.0, Dimension.MASS, "kg")),
            Map.entry("mw", new Linear(1e-3, 0.0, Dimension.HEAT_FLOW, "W")),
            Map.entry("w", new Linear(1.0, 0.0, Dimension.HEAT_FLOW, "W")),
            Map.entry("k/min", new Linear(1.0 / 60.0, 0.0, Dimension.RATE, "K/s")),
            Map.entry("°c/min", new Linear(1.0 / 60.0, 0.0, Dimension.RATE, "K/s")),
            Map.entry("k/s", new Linear(1.0, 0.0, Dimension.RATE, "K/s")),
            Map.entry("s", new Linear(1.0, 0.0, Dimension.TIME, "s")),
            Map.entry("min", new Linear(60.0, 0.0, Dimension.TIME, "s")),
            Map.entry("j/g", new Linear(1000.0, 0.0, Dimension.ENERGY_PER_MASS, "J/kg")),
            Map.entry("j/kg", new Linear(1.0, 0.0, Dimension.ENERGY_PER_MASS, "J/kg")),
            Map.entry("", new Linear(1.0, 0.0, Dimension.DIMENSIONLESS, ""))
    );

    /** 解析 "12.3 mg" 这类原始表示，原始文本原样保留。 */
    public static Quantity parse(String rawText) {
        if (rawText == null) {
            return new Quantity(null, null, Double.NaN, null, Dimension.UNKNOWN, null,
                    new ConversionDiagnostic("MISSING_VALUE", "原始值缺失，保留空位置"));
        }
        String text = rawText.trim();
        int split = firstNonNumeric(text);
        String numPart = text.substring(0, split).trim();
        String unitPart = text.substring(split).trim();
        double value;
        try {
            value = Double.parseDouble(numPart);
        } catch (NumberFormatException e) {
            return new Quantity(text, unitPart, Double.NaN, null, Dimension.UNKNOWN, null,
                    new ConversionDiagnostic("UNPARSEABLE_NUMBER",
                            "无法解析数值 '" + numPart + "'，原值保留在原位"));
        }
        return from(value, unitPart, text);
    }

    public static Quantity from(double value, String unitPart, String rawText) {
        String key = unitPart == null ? "" : unitPart.trim().toLowerCase();
        Linear linear = CONVERSIONS.get(key);
        if (linear == null) {
            return new Quantity(rawText, unitPart, value, null, Dimension.UNKNOWN, unitPart,
                    new ConversionDiagnostic("UNKNOWN_UNIT",
                            "未知单位 '" + unitPart + "'，无法规范化；原始表示已保留，未为画图丢弃"));
        }
        return new Quantity(rawText, unitPart, value,
                value * linear.slope() + linear.intercept(),
                linear.dimension(), linear.canonical(), null);
    }

    private static int firstNonNumeric(String text) {
        int i = 0;
        while (i < text.length()) {
            char ch = text.charAt(i);
            boolean numeric = (ch >= '0' && ch <= '9') || ch == '.' || ch == '-' || ch == '+'
                    || ch == 'e' || ch == 'E';
            if (!numeric) {
                break;
            }
            i++;
        }
        return i;
    }

    public static boolean sameDimension(String unitA, Dimension dimension) {
        Linear linear = CONVERSIONS.get(unitA == null ? "" : unitA.trim().toLowerCase());
        return linear != null && linear.dimension() == dimension;
    }
}
