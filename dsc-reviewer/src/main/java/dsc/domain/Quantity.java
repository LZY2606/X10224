package dsc.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * A scalar with its unit / coordinate basis. Raw quantities keep the original
 * textual representation; a value that cannot be converted stays in place with
 * {@code convertible=false} and a diagnostic rather than being dropped.
 */
public record Quantity(double value, String unit, String raw, boolean convertible) {

    public Quantity {
        Objects.requireNonNull(unit, "unit");
    }

    public static Quantity of(double value, String unit) {
        return new Quantity(value, unit, format(value), true);
    }

    public static Quantity raw(String raw, String unit) {
        try {
            return new Quantity(Double.parseDouble(raw.trim()), unit, raw, true);
        } catch (NumberFormatException e) {
            return new Quantity(Double.NaN, unit, raw, false);
        }
    }

    public static String format(double v) {
        if (Double.isNaN(v)) {
            return "NaN";
        }
        return String.format(Locale.ROOT, "%.6g", v);
    }
}
