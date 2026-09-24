package dsc.model;

import java.util.Locale;

/**
 * 领域量的单位与坐标基准。原始导入保留原始字符串表示，
 * 只有派生层才转换为规范化内部单位。
 */
public final class Units {
    // 规范化内部单位
    public static final String CELSIUS = "°C";
    public static final String KELVIN = "K";
    public static final String MW_PER_MG = "mW/mg";
    public static final String W_PER_G = "W/g";
    public static final String J_PER_G = "J/g";
    public static final String MG = "mg";
    public static final String G = "g";
    public static final String K_PER_MIN = "K/min";
    public static final String SECOND = "s";
    public static final String MINUTE = "min";
    public static final String MW = "mW";

    private Units() {}

    /** 温度转摄氏度；无法转换返回 NaN。 */
    public static double toCelsius(double value, String unit) {
        if (unit == null) return Double.NaN;
        switch (unit.trim()) {
            case CELSIUS: return value;
            case KELVIN: return value - 273.15;
            default: return Double.NaN;
        }
    }

    /** 热流密度归一化到 mW/mg；无法转换返回 NaN。 */
    public static double heatFluxToMwPerMg(double value, String unit) {
        if (unit == null) return Double.NaN;
        switch (unit.trim()) {
            case MW_PER_MG: return value;
            case W_PER_G: return value; // 1 W/g = 1 mW/mg
            default: return Double.NaN;
        }
    }

    /** 质量归一化到 mg；无法转换返回 NaN。 */
    public static double massToMg(double value, String unit) {
        if (unit == null) return Double.NaN;
        switch (unit.trim()) {
            case MG: return value;
            case G: return value * 1000.0;
            default: return Double.NaN;
        }
    }

    /** 扫描速率归一化到 K/min（数值上等于 °C/min）；无法转换返回 NaN。 */
    public static double rateToKPerMin(double value, String unit) {
        if (unit == null) return Double.NaN;
        if (unit.trim().equals(K_PER_MIN) || unit.trim().equals("°C/min")) return value;
        return Double.NaN;
    }

    /** 焓变归一化到 J/g（由 mW*min/mg 换算：1 mW·min/mg = 60 J/g）。 */
    public static double enthalpyToJPerG(double integratedMwMinPerMg) {
        return integratedMwMinPerMg * 60.0;
    }

    public static String norm(String unit) {
        return unit == null ? "" : unit.trim();
    }

    public static boolean sameUnit(String a, String b) {
        return norm(a).toLowerCase(Locale.ROOT).equals(norm(b).toLowerCase(Locale.ROOT));
    }
}
