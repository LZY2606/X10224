package dsc.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * A measured value that always carries its unit (or coordinate basis) and the
 * exact textual representation received from the instrument. Conversion to the
 * internal canonical basis happens only here, at ingestion; an unconvertible
 * value is never silently dropped ({@link #convertible()} is false and
 * {@link #conversionError()} explains why).
 *
 * Canonical bases: temperature K, scan rate K/min, heat flow mW, mass mg,
 * specific enthalpy J/g.
 */
public record Quantity(double value, String unit, String raw, String conversionError) {

    public Quantity {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(raw, "raw");
    }

    public static Quantity of(double value, String unit) {
        double factor = factor(unit);
        if (Double.isNaN(factor)) {
            return new Quantity(value, unit, trim(raw(value, unit)),
                    "no conversion from unit '" + unit + "' to canonical basis");
        }
        double canonical = value * factor + offset(unit);
        return new Quantity(canonical, canonical(unit), trim(raw(value, unit)), null);
    }

    /** Parse an instrument cell whose numeric parse already succeeded; keeps the original text. */
    public static Quantity parsed(double numericValue, String rawText, String unit) {
        double factor = factor(unit);
        if (Double.isNaN(factor)) {
            return new Quantity(numericValue, unit, trim(rawText),
                    "no conversion from unit '" + unit + "' to canonical basis");
        }
        return new Quantity(numericValue * factor + offset(unit), canonical(unit), trim(rawText), null);
    }

    /** A cell whose numeric content could not be parsed: it stays at the origin. */
    public static Quantity unparseable(String rawText, String unit) {
        return new Quantity(Double.NaN, unit, trim(rawText), "cannot parse numeric value: " + rawText);
    }

    public boolean convertible() {
        return conversionError == null && !Double.isNaN(value);
    }

    private static String raw(double value, String unit) {
        return String.format(Locale.ROOT, "%s %s", fmt(value), unit);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    static String fmt(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return Long.toString((long) v);
        return String.format(Locale.ROOT, "%.6g", v);
    }

    private static String key(String unit) {
        return unit.trim().toLowerCase(Locale.ROOT)
                .replace("°", "").replace("℃", "c").replace("μ", "u");
    }

    private static double factor(String unit) {
        return switch (key(unit)) {
            case "k", "c", "degc" -> 1.0;           // offset applied separately
            case "f", "degf" -> 5.0 / 9.0;
            case "k/min", "c/min" -> 1.0;
            case "f/min" -> 5.0 / 9.0;
            case "mw" -> 1.0;
            case "w" -> 1000.0;
            case "uw" -> 0.001;
            case "mg" -> 1.0;
            case "g" -> 1000.0;
            case "j/g" -> 1.0;
            case "j/mg" -> 1000.0;
            default -> Double.NaN;
        };
    }

    private static double offset(String unit) {
        return switch (key(unit)) {
            case "c", "degc" -> 273.15;
            case "f", "degf" -> 273.15 - 32.0 * 5.0 / 9.0;
            default -> 0.0;
        };
    }

    private static String canonical(String unit) {
        return switch (key(unit)) {
            case "k", "c", "degc", "f", "degf" -> "K";
            case "k/min", "c/min", "f/min" -> "K/min";
            case "mw", "w", "uw" -> "mW";
            case "mg", "g" -> "mg";
            case "j/g", "j/mg" -> "J/g";
            default -> unit.trim();
        };
    }
}
