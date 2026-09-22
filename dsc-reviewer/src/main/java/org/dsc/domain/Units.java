package org.dsc.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单位与坐标基准规范化。导入层保留原始表示；只有可解析的值才进入规范化坐标。
 * 规范化坐标：温度 K，热流 W/g，时间 s，速率 K/min。
 * 无法转换时返回带原因的诊断，调用方必须把原始值留在原位置。
 */
public final class Units {
    private Units() {}

    public record Conversion(double value, String diagnostic) {
        public boolean ok() { return diagnostic == null; }
    }

    private static final Pattern NUMBER_PREFIX =
            Pattern.compile("([-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?)\\s*(.*)");

    /** 温度转 K，接受 K、C（°C/℃/degC）、F。 */
    public static Conversion toKelvin(String raw) {
        if (raw == null || raw.isBlank()) return new Conversion(Double.NaN, "missing temperature");
        Matcher m = NUMBER_PREFIX.matcher(raw.trim());
        if (!m.matches()) return new Conversion(Double.NaN, "unparseable temperature: " + raw);
        double v = Double.parseDouble(m.group(1));
        String unit = m.group(2).toLowerCase(Locale.ROOT)
                .replace("°", "").replace("℃", "c").replace("deg", "").trim();
        return switch (unit) {
            case "", "k", "kelvin" -> new Conversion(v, null);
            case "c", "celsius" -> new Conversion(v + 273.15, null);
            case "f", "fahrenheit" -> new Conversion((v - 32.0) * 5.0 / 9.0 + 273.15, null);
            default -> new Conversion(Double.NaN, "unknown temperature unit '" + unit + "' in: " + raw);
        };
    }

    /**
     * 比热流转 W/g。接受 uW/mg、mW/mg、W/g、mW/g、uW/g、W/kg、mW/kg、uW/kg。
     * 换算关系：mW/mg = 1 W/g；W/kg = 1e-3 W/g；mW/g = 1e-3 W/g。
     */
    public static Conversion heatFlowToWPerG(String raw) {
        if (raw == null || raw.isBlank()) return new Conversion(Double.NaN, "missing heat flow");
        String s = raw.trim().toLowerCase(Locale.ROOT).replace("μ", "u").replace("µ", "u");
        Matcher m = NUMBER_PREFIX.matcher(s);
        if (!m.matches()) return new Conversion(Double.NaN, "unparseable heat flow: " + raw);
        double v = Double.parseDouble(m.group(1));
        String unit = m.group(2).replace("*", "").replace("^", "").trim();

        double powerFactor;
        if (unit.startsWith("uw")) powerFactor = 1e-6;
        else if (unit.startsWith("mw")) powerFactor = 1e-3;
        else if (unit.startsWith("w")) powerFactor = 1.0;
        else return new Conversion(Double.NaN, "unknown heat-flow power unit in: " + raw);

        double massFactor;
        if (unit.endsWith("/kg")) massFactor = 1e-3;
        else if (unit.endsWith("/g")) massFactor = 1.0;
        else return new Conversion(Double.NaN, "heat flow must be per-mass (/mg, /g, /kg) in: " + raw);

        // /mg 与 /g 的差恰好被 mW→W 的 1e-3 抵消：mW/mg = W/g。
        if (unit.contains("/mg")) massFactor *= 1e3;
        return new Conversion(v * powerFactor * massFactor, null);
    }

    /** 扫描速率 K/min，取绝对值；缺失返回 empty。 */
    public static Optional<Double> parseRateKPerMin(String raw) {
        if (raw == null) return Optional.empty();
        Matcher m = Pattern.compile("([-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?)").matcher(raw);
        return m.find() ? Optional.of(Math.abs(Double.parseDouble(m.group(1)))) : Optional.empty();
    }
}
