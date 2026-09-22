package dsc.domain;

/**
 * 导入时保留的原始表示：字符串原文 + 解析值（可能失败）。
 * 无法转换的值留在原位置并附诊断，绝不为了画图而丢弃。
 */
public final class RawValue {
    public final String raw;
    public final double parsed;
    public final boolean parseOk;
    public final String diagnostic;

    private RawValue(String raw, double parsed, boolean parseOk, String diagnostic) {
        this.raw = raw;
        this.parsed = parsed;
        this.parseOk = parseOk;
        this.diagnostic = diagnostic;
    }

    public static RawValue of(String raw) {
        if (raw == null) {
            return new RawValue(null, Double.NaN, false, "missing value");
        }
        String trimmed = raw.trim();
        try {
            double v = Double.parseDouble(trimmed);
            if (Double.isFinite(v)) {
                return new RawValue(raw, v, true, null);
            }
            return new RawValue(raw, Double.NaN, false, "non-finite value: " + trimmed);
        } catch (NumberFormatException e) {
            return new RawValue(raw, Double.NaN, false, "not a number: " + trimmed);
        }
    }

    @Override
    public String toString() {
        return parseOk ? Double.toString(parsed) : (raw + " [" + diagnostic + "]");
    }
}
