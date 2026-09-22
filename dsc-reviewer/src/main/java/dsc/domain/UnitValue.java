package dsc.domain;

import java.util.Locale;
import java.util.Map;

/**
 * A measured value that keeps its original textual representation and unit.
 *
 * <p>The import layer never throws away a value that cannot be converted: the
 * raw token stays in place and a {@link Diagnostic} explains the failure.
 */
public record UnitValue(double value, String unit, String raw) {

    public UnitValue(double value, String unit) {
        this(value, unit, format(value, unit));
    }

    private static String format(double value, String unit) {
        String body = (value == Math.rint(value))
                ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.6g", value);
        return unit == null || unit.isBlank() ? body : body + " " + unit;
    }

    /** Canonical target unit per quantity; conversion outside these tables is rejected. */
    private static final Map<String, Double> TEMPERATURE_TO_K = Map.of(
            "k", 1.0, "deg c", 273.15, "c", 273.15,
            "degc", 273.15, "deg f", 459.67 / 1.8);

    private static final java.util.function.Function<Double, Double> fToK =
            f -> (f + 459.67) / 1.8;
    private static final java.util.function.Function<Double, Double> cToK = c -> c + 273.15;
    private static final java.util.function.Function<Double, Double> kToK = k -> k;

    /** Convert to kelvin; returns null when the representation cannot be converted. */
    public Double toKelvin() {
        if (unit == null) {
            return null;
        }
        String u = unit.trim().toLowerCase(Locale.ROOT);
        return switch (u) {
            case "k", "kelvin" -> kToK.apply(value);
            case "c", "deg c", "degc", "°c" -> cToK.apply(value);
            case "f", "deg f", "degf", "°f" -> fToK.apply(value);
            default -> null;
        };
    }

    /** Convert mass to grams. */
    public Double toGrams() {
        if (unit == null) {
            return null;
        }
        return switch (unit.trim().toLowerCase(Locale.ROOT)) {
            case "g", "gram", "grams" -> value;
            case "mg" -> value / 1000.0;
            case "kg" -> value * 1000.0;
            default -> null;
        };
    }

    /** Convert a scan rate to kelvin per minute. */
    public Double toKelvinPerMinute() {
        if (unit == null) {
            return null;
        }
        return switch (unit.trim().toLowerCase(Locale.ROOT)) {
            case "k/min", "c/min", "°c/min", "degc/min" -> value;
            case "k/s", "c/s" -> value * 60.0;
            default -> null;
        };
    }

    /** Convert heat flow per mass to watt per gram. */
    public Double toWattPerGram() {
        if (unit == null) {
            return null;
        }
        return switch (unit.trim().toLowerCase(Locale.ROOT)) {
            case "w/g" -> value;
            case "mw/g" -> value / 1000.0;
            case "j/(g*s)", "j/g/s" -> value;
            default -> null;
        };
    }
}
