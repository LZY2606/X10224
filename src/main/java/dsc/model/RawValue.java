package dsc.model;

/**
 * 原始表示与规范化值同时保留。无法转换时 normalized 为 NaN，
 * diagnostic 说明原因，原始值留在原位置，绝不因画图需要丢弃。
 */
public record RawValue(String raw, String unit, double normalized, String diagnostic) {
    public static RawValue of(String raw, String unit, double normalized) {
        return new RawValue(raw, unit, normalized, Double.isNaN(normalized)
                ? "无法将 '" + raw + " " + unit + "' 转换为规范化单位" : null);
    }

    public boolean convertible() {
        return !Double.isNaN(normalized);
    }
}
