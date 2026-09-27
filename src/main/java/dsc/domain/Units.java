package dsc.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * 物理量：原始表示始终保留，只有派生层才规范化（SI）。
 * 无法转换时 {@link #canonical} 为 null，并通过 {@link #diagnostic} 记录原因；
 * 原始字符串不会为了画图而被丢弃。
 */
public final class Qty {
    public final String raw;
    public final String unit;
    public final Double canonical;
    public final String diagnostic;

    private Qty(String raw, String unit, Double canonical, String diagnostic) {
        this.raw = raw;
        this.unit = unit;
        this.canonical = canonical;
        this.diagnostic = diagnostic;
    }

    public static Qty of(String raw, String unit, double canonical) {
        return new Qty(raw, unit, canonical, null);
    }

    public static Qty unconvertible(String raw, String unit, String reason) {
        return new Qty(raw, unit, null, reason);
    }

    /** 温度 → K。支持 °C / K / °F。 */
    public static Qty temperature(String raw, String unit) {
        Double v = parseNumber(raw);
        if (v == null) return unconvertible(raw, unit, "温度不是可解析数值");
        String u = norm(unit);
        switch (u) {
            case "°c", "c", "degc", "celsius" -> { return of(raw, u, v + 273.15); }
            case "k", "kelvin" -> { return of(raw, u, v); }
            case "°f", "f" -> { return of(raw, u, (v - 32.0) * 5.0 / 9.0 + 273.15); }
            default -> { return unconvertible(raw, u, "未知温度单位: " + u); }
        }
    }

    /** 质量 → kg。 */
    public static Qty mass(String raw, String unit) {
        Double v = parseNumber(raw);
        if (v == null) return unconvertible(raw, unit, "质量不是可解析数值");
        String u = norm(unit);
        switch (u) {
            case "mg" -> { return of(raw, u, v * 1e-6); }
            case "g" -> { return of(raw, u, v * 1e-3); }
            case "kg" -> { return of(raw, u, v); }
            case "µg", "ug" -> { return of(raw, u, v * 1e-9); }
            default -> { return unconvertible(raw, u, "未知质量单位: " + u); }
        }
    }

    /** 扫描速率 → K/min（保留符号）。 */
    public static Qty scanRate(String raw, String unit) {
        Double v = parseNumber(raw);
        if (v == null) return unconvertible(raw, unit, "扫描速率不是可解析数值");
        String u = norm(unit);
        switch (u) {
            case "k/min", "°c/min", "c/min", "kpm" -> { return of(raw, u, v); }
            case "k/s", "°c/s" -> { return of(raw, u, v * 60.0); }
            default -> { return unconvertible(raw, u, "未知扫描速率单位: " + u); }
        }
    }

    /**
     * 热流。
     * normalized=false：绝对热流，SI 为 W（支持 mW/µW/W）。
     * normalized=true ：每质量热流，SI 为 W/kg（支持 mW/g = 1e-3 W / 1e-3 kg = 1 W/kg、W/g、W/kg）。
     */
    public static Qty heatFlow(String raw, String unit, boolean normalized) {
        Double v = parseNumber(raw);
        if (v == null) return unconvertible(raw, unit, "热流不是可解析数值");
        String u = norm(unit);
        if (normalized) {
            switch (u) {
                case "mw/g" -> { return of(raw, u, v); }             // mW/g == W/kg
                case "w/g" -> { return of(raw, u, v * 1e3); }
                case "w/kg" -> { return of(raw, u, v); }
                default -> {
                    return unconvertible(raw, u, "归一化热流需要 mW/g|W/g|W/kg，实际: " + u);
                }
            }
        }
        switch (u) {
            case "mw" -> { return of(raw, u, v * 1e-3); }
            case "w" -> { return of(raw, u, v); }
            case "µw", "uw" -> { return of(raw, u, v * 1e-6); }
            default -> { return unconvertible(raw, u, "绝对热流需要 mW|W|µW，实际: " + u); }
        }
    }

    public static Double parseNumber(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        try {
            double v = Double.parseDouble(s);
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean usable() {
        return canonical != null;
    }

    private static String norm(String unit) {
        return unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return raw + (unit == null || unit.isEmpty() ? "" : " " + unit);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return o instanceof Qty q && Objects.equals(raw, q.raw) && Objects.equals(unit, q.unit);
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(raw, unit);
    }
}
