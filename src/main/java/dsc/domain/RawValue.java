package dsc.domain;

import java.util.Objects;

/**
 * 导入时保留原始表示（raw），派生层才规范化（normalized）。
 * 无法转换时 normalized 为 Double.NaN，诊断写入 diagnostics，不丢值。
 */
public final class RawValue {
    private final String raw;
    private final String unit;
    private final double normalized;
    private final String diagnostic;

    private RawValue(String raw, String unit, double normalized, String diagnostic) {
        this.raw = raw;
        this.unit = unit;
        this.normalized = normalized;
    }

    public static RawValue of(String raw, String unit, double normalized, String diagnostic) {
        return new RawValue(raw, unit, normalized, diagnostic);
    }

    public static RawValue numeric(String raw, double normalized) {
        return new RawValue(raw, null, normalized, null);
    }

    public static RawValue unconvertible(String raw, String unit, String reason) {
        return new RawValue(raw, unit, Double.NaN, reason);
    }

    public String raw() { return raw; }
    public String unit() { return unit; }
    public double normalized() { return normalized; }
    public String diagnostic() { return diagnostic; }
    public boolean convertible() { return !Double.isNaN(normalized); }

    @Override public String toString() {
        return raw + (unit == null ? "" : " " + unit)
                + (convertible() ? "" : " [unconverted]");
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof RawValue r)) return false;
        return Objects.equals(raw, r.raw) && Objects.equals(unit, r.unit);
    }
    @Override public int hashCode() { return Objects.hash(raw, unit); }
}
