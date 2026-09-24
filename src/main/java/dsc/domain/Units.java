package dsc.domain;

import java.util.Locale;

/**
 * 领域量的单位换算。所有换算只在派生层发生；原始表示由调用方保留。
 * 无法转换时返回 null，并由调用方在原位置附诊断。
 */
public final class Units {
    private Units() {
    }

    public static final class Parse {
        public final Double value;
        public final String diagnostic;

        private Parse(Double value, String diagnostic) {
            this.value = value;
            this.diagnostic = diagnostic;
        }

        public static Parse ok(double v) {
            return new Parse(v, null);
        }

        public static Parse bad(String raw, String unit) {
            return new Parse(null, "UNCONVERTIBLE_VALUE: '" + raw + "' 无法按单位 '" + unit + "' 解析，原值保留在原位置");
        }
    }

    private static Double numeric(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("NA") || t.equalsIgnoreCase("N/A")
                || t.equalsIgnoreCase("NULL") || t.equalsIgnoreCase("OVERLOAD")
                || t.equalsIgnoreCase("SATURATED")) {
            return null;
        }
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 温度 -> K。支持 K、°C、C、degC。 */
    public static Parse temperatureK(String raw, String unit) {
        Double v = numeric(raw);
        if (v == null) {
            return Parse.bad(raw, unit);
        }
        String u = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
        switch (u) {
            case "k":
                return Parse.ok(v);
            case "°c":
            case "c":
            case "degc":
            case "celsius":
                return Parse.ok(v + 273.15);
            default:
                return Parse.bad(raw, unit);
        }
    }

    /** 热流 -> W。支持 W、mW、µW/uW、cal/s、mcal/s。 */
    public static Parse powerW(String raw, String unit) {
        Double v = numeric(raw);
        if (v == null) {
            return Parse.bad(raw, unit);
        }
        String u = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
        switch (u) {
            case "w":
                return Parse.ok(v);
            case "mw":
                return Parse.ok(v * 1.0e-3);
            case "µw":
            case "uw":
                return Parse.ok(v * 1.0e-6);
            case "cal/s":
            case "cal·s^-1":
                return Parse.ok(v * 4.184);
            case "mcal/s":
                return Parse.ok(v * 4.184e-3);
            default:
                return Parse.bad(raw, unit);
        }
    }

    /** 质量 -> g。支持 mg、g。 */
    public static Parse massG(String raw, String unit) {
        Double v = numeric(raw);
        if (v == null) {
            return Parse.bad(raw, unit);
        }
        String u = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
        switch (u) {
            case "g":
                return Parse.ok(v);
            case "mg":
                return Parse.ok(v * 1.0e-3);
            default:
                return Parse.bad(raw, unit);
        }
    }

    /** 扫描速率 -> K/min。支持 K/min、°C/min、C/min、K/s。 */
    public static Parse rateKPerMin(String raw, String unit) {
        Double v = numeric(raw);
        if (v == null) {
            return Parse.bad(raw, unit);
        }
        String u = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
        switch (u) {
            case "k/min":
            case "°c/min":
            case "c/min":
                return Parse.ok(v);
            case "k/s":
                return Parse.ok(v * 60.0);
            default:
                return Parse.bad(raw, unit);
        }
    }

    public static double celsius(double kelvin) {
        return kelvin - 273.15;
    }
}
