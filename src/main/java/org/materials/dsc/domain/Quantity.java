package org.materials.dsc.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 带单位与原始表示的量。rawText/rawUnit 保存导入时的字面量；
 * 只有转换成功时 canonical 才有值，无法转换时留在原位置并附诊断。
 */
public record Quantity(Double value, Units unit, String rawText, String rawUnit) {

    public Quantity {
        if (unit == null) {
            throw new IllegalArgumentException("unit is required; use raw-only form with explicit null value");
        }
    }

    public static Quantity of(double value, Units unit) {
        return new Quantity(value, unit, format(value), unit.symbol());
    }

    public static Quantity raw(String rawText, String rawUnit) {
        return new Quantity(null, Units.DIMENSIONLESS, rawText, rawUnit);
    }

    public static Quantity canonicalText(double canonical, Units unit) {
        return new Quantity(unit.fromCanonical(canonical), unit,
                format(unit.fromCanonical(canonical)), unit.symbol());
    }

    private static String format(double v) {
        return String.format(java.util.Locale.ROOT, "%.6g", v);
    }

    public boolean convertible() {
        return value != null;
    }

    @JsonIgnore
    public double canonical() {
        if (value == null) {
            throw new IllegalStateException("raw-only quantity cannot be canonicalized: " + rawText + " " + rawUnit);
        }
        return unit.toCanonical(value);
    }
}
