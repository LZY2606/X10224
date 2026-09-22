package dsc.core;

/**
 * 带原始表示、单位与坐标基准的领域量。
 * raw 保存导入时的字符串，规范化派生层才计算 value；无法转换时 value=NaN 并保留诊断。
 */
public final class Quantity {
    private final String raw;
    private final String unit;
    private final double value;          // 内部归一值
    private final boolean convertible;
    private final String diagnostic;

    private Quantity(String raw, String unit, double value, boolean convertible, String diagnostic) {
        this.raw = raw;
        this.unit = unit == null ? "" : unit;
        this.value = value;
        this.convertible = convertible;
        this.diagnostic = diagnostic;
    }

    public static Quantity of(double normalized, String unit) {
        return new Quantity(null, unit, normalized, true, null);
    }

    /** 从原始字符串与单位解析；解析失败不抛丢，返回带诊断的量。 */
    public static Quantity parse(String raw, String unit, Unit.Dim expected) {
        String r = raw == null ? "" : raw.trim();
        double v;
        try {
            v = Double.parseDouble(r);
        } catch (NumberFormatException e) {
            return new Quantity(raw, unit, Double.NaN, false, "数值无法解析: '" + r + "'，保留原值");
        }
        Unit u = Unit.parse(unit);
        if (u == null) {
            return new Quantity(raw, unit, Double.NaN, false, "未知单位 '" + unit + "'，值留在原位置");
        }
        if (u.dim() != expected) {
            return new Quantity(raw, unit, Double.NaN, false,
                    "量纲不匹配: '" + unit + "' 是 " + u.dim() + "，需要 " + expected);
        }
        return new Quantity(raw, unit, u.toNormalized(v), true, null);
    }

    public String raw() { return raw; }
    public String unit() { return unit; }
    public double value() { return value; }
    public boolean convertible() { return convertible; }
    public String diagnostic() { return diagnostic; }

    public String display() {
        if (convertible) return String.format(java.util.Locale.ROOT, "%.4g", value) + " " + internalUnit();
        return raw + " " + unit + " ⚠";
    }

    private String internalUnit() {
        Unit u = Unit.parse(unit);
        if (u == null) return unit;
        return switch (u.dim()) {
            case TEMPERATURE -> "°C";
            case MASS -> "mg";
            case HEAT_FLOW -> "mW";
            case RATE -> unit.contains("K") ? "K/min" : "°C/min";
            case TIME -> "min";
            case HEAT -> "J/g";
            default -> unit;
        };
    }
}
