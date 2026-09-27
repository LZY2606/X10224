package dsc.core;

import dsc.model.Quantity;

/**
 * 单位与坐标基准转换。所有领域量携带单位；导入层只解析与保留原始表示，
 * 规范化在派生层发生。无法转换时返回带诊断的 Quantity，原值留在原处。
 */
public final class Units {
    private Units() {}

    /** 温度：支持 °C / deg C / K（后者按基准换算）。结果统一为 °C。 */
    public static Quantity parseTemperature(String raw) {
        if (raw == null) return new Quantity(null, null, "°C", "missing temperature");
        String s = raw.trim();
        String unit = "°C";
        String num = s;
        String low = s.toLowerCase();
        if (low.endsWith("k") && !low.contains("c")) {
            unit = "K";
            num = s.substring(0, s.length() - 1).trim();
        } else if (low.endsWith("°c") || low.endsWith("degc") || low.endsWith("c")) {
            int cut = low.startsWith("-") || low.startsWith("+") ? 1 : 0;
            int u = Math.max(low.lastIndexOf('c'), low.lastIndexOf('k'));
            num = s.substring(0, u).trim();
        }
        try {
            double v = Double.parseDouble(num);
            if ("K".equals(unit)) v = v - 273.15;
            return new Quantity(raw, v, "°C", null);
        } catch (NumberFormatException e) {
            return new Quantity(raw, null, "°C", "unparseable temperature token: " + raw);
        }
    }

    /** 热流：mW / W（每点）。统一 mW。 */
    public static Quantity parseHeatFlow(String raw) {
        if (raw == null) return new Quantity(null, null, "mW", "missing heat flow");
        String s = raw.trim();
        double factor = 1.0;
        String low = s.toLowerCase();
        String num = s;
        if (low.endsWith("mw")) {
            num = s.substring(0, s.length() - 2).trim();
        } else if (low.endsWith("w")) {
            factor = 1000.0;
            num = s.substring(0, s.length() - 1).trim();
        }
        try {
            return new Quantity(raw, Double.parseDouble(num) * factor, "mW", null);
        } catch (NumberFormatException e) {
            return new Quantity(raw, null, "mW", "unparseable heat-flow token: " + raw);
        }
    }

    /** 质量：mg / g / kg，统一 g。 */
    public static Quantity parseMass(String raw) {
        if (raw == null || raw.isBlank())
            return new Quantity(raw, null, "g", "missing mass");
        String s = raw.trim();
        String low = s.toLowerCase();
        double factor;
        String num;
        if (low.endsWith("mg")) { factor = 0.001; num = s.substring(0, s.length() - 2).trim(); }
        else if (low.endsWith("kg")) { factor = 1000.0; num = s.substring(0, s.length() - 2).trim(); }
        else if (low.endsWith("g")) { factor = 1.0; num = s.substring(0, s.length() - 1).trim(); }
        else { factor = 1.0; num = s; }
        try {
            double v = Double.parseDouble(num) * factor;
            if (v <= 0) return new Quantity(raw, null, "g", "non-positive mass: " + raw);
            return new Quantity(raw, v, "g", null);
        } catch (NumberFormatException e) {
            return new Quantity(raw, null, "g", "unparseable mass token: " + raw);
        }
    }

    /** 扫描速率 K/min（也接受 K/s）。 */
    public static Quantity parseRate(String raw) {
        if (raw == null || raw.isBlank())
            return new Quantity(raw, null, "K/min", "missing scan rate");
        String s = raw.trim().replace(" ", "");
        String low = s.toLowerCase();
        double factor = 1.0;
        String num = s;
        if (low.endsWith("k/min")) num = s.substring(0, s.length() - 5);
        else if (low.endsWith("k/s")) { factor = 60.0; num = s.substring(0, s.length() - 3); }
        else if (low.endsWith("kmin-1") || low.endsWith("k·min-1") || low.endsWith("kmin^-1"))
            num = s.replaceAll("(?i)k(·|\\^)?min.1$", "");
        try {
            return new Quantity(raw, Math.abs(Double.parseDouble(num) * factor), "K/min", null);
        } catch (NumberFormatException e) {
            return new Quantity(raw, null, "K/min", "unparseable rate token: " + raw);
        }
    }

    /** 流量 mL/min。 */
    public static Quantity parseFlow(String raw) {
        if (raw == null || raw.isBlank())
            return new Quantity(raw, null, "mL/min", "missing flow");
        String s = raw.trim().replace(" ", "");
        String num = s.toLowerCase().endsWith("ml/min") ? s.substring(0, s.length() - 7) : s;
        try {
            return new Quantity(raw, Double.parseDouble(num), "mL/min", null);
        } catch (NumberFormatException e) {
            return new Quantity(raw, null, "mL/min", "unparseable flow token: " + raw);
        }
    }
}
