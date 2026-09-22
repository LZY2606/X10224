package org.materials.dsc.domain;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 一个携带单位的领域量。导入时保留原始表示（raw），派生层才转换为规范值。
 * 无法转换时 canonical 为 null，并附带诊断——绝不为了画图而丢弃原值。
 */
public record Quantity(String raw, Double canonical, String canonicalUnit, String diagnostic) {

    public Quantity {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(canonicalUnit, "canonicalUnit");
    }

    public static Quantity of(double value, String canonicalUnit) {
        return new Quantity(format(value), value, canonicalUnit, null);
    }

    public static Quantity invalid(String raw, String canonicalUnit, String diagnostic) {
        return new Quantity(raw, null, canonicalUnit, diagnostic);
    }

    public boolean isConvertible() {
        return canonical != null && diagnostic == null;
    }

    public static Quantity parse(String rawText, String declaredUnit, String canonicalUnit,
                                 java.util.function.BiFunction<Double, String, Double> converter) {
        String raw = rawText == null ? "" : rawText.trim();
        if (raw.isEmpty()) {
            return invalid(raw, canonicalUnit, "empty value");
        }
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return invalid(raw, canonicalUnit, "not a number: " + raw);
        }
        if (!Double.isFinite(value)) {
            return invalid(raw, canonicalUnit, "non-finite value");
        }
        try {
            Double converted = converter.apply(value, declaredUnit == null ? "" : declaredUnit.trim());
            if (converted == null || !Double.isFinite(converted)) {
                return invalid(raw, canonicalUnit, "cannot convert from unit '" + declaredUnit + "'");
            }
            return new Quantity(raw, converted, canonicalUnit, null);
        } catch (IllegalArgumentException e) {
            return invalid(raw, canonicalUnit, e.getMessage());
        }
    }

    public List<String> diagnostics() {
        return diagnostic == null ? List.of() : List.of(diagnostic);
    }

    static String format(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return String.valueOf((long) v);
        }
        return String.format(Locale.ROOT, "%.6g", v);
    }
}
