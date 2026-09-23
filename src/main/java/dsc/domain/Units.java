package dsc.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 领域量统一携带单位与坐标基准。转换是显式的：无法转换时返回 null，
 * 调用方必须把原值留在原位置并附诊断，不能为了画图丢掉。
 */
public final class Units {
    private Units() {}

    /** 一个带单位与坐标基准的量。basis 例如 "sample-mass-normalized"、"instrument-raw"、"extrapolated-onset"。 */
    public record Qty(double value, String unit, String basis) {
        public Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", value);
            m.put("unit", unit);
            m.put("basis", basis);
            return m;
        }
    }

    // 比例换算表：canonical = value * scale（仅比例类单位；温度单独处理）
    private static final Map<String, Double> SCALE = new LinkedHashMap<>();
    private static final Map<String, String> CLASS = new LinkedHashMap<>();
    static {
        reg("W", 1.0, "power"); reg("mW", 1e-3, "power"); reg("uW", 1e-6, "power");
        reg("g", 1.0, "mass"); reg("mg", 1e-3, "mass"); reg("ug", 1e-6, "mass");
        reg("K/s", 1.0, "rate"); reg("K/min", 1.0 / 60.0, "rate"); reg("°C/min", 1.0 / 60.0, "rate");
        reg("J/g", 1.0, "specific-energy"); reg("J/mg", 1e3, "specific-energy");
    }
    private static void reg(String unit, double scale, String cls) {
        SCALE.put(unit, scale);
        CLASS.put(unit, cls);
    }

    /** 比例类单位换算因子：value * factor = value in toUnit。未知或类别不符返回 null。 */
    public static Double scale(String fromUnit, String toUnit) {
        Double f = SCALE.get(fromUnit), t = SCALE.get(toUnit);
        if (f == null || t == null) return null;
        String cf = CLASS.get(fromUnit);
        if (cf == null || !cf.equals(CLASS.get(toUnit))) return null;
        return f / t;
    }

    /** 温度换算到 °C 坐标基准。未知单位返回 null。 */
    public static Double toCelsius(double v, String unit) {
        switch (unit) {
            case "°C": case "degC": case "C": return v;
            case "K": return v - 273.15;
            default: return null;
        }
    }

    public static Map<String, Object> diag(String code, String message, String at) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("code", code);
        d.put("message", message);
        if (at != null) d.put("at", at);
        return d;
    }
}
