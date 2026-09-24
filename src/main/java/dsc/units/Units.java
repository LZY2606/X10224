package dsc.units;

import dsc.model.Quantity;

/**
 * Unit conversions for the derived (normalization) layer. Raw archives keep
 * their original representation; conversion happens only here. A value that
 * cannot be converted returns null so the caller can attach a diagnostic and
 * leave the raw value in place.
 */
public final class Units {

    private Units() {}

    /** mg, or null if the unit is not a convertible mass unit. */
    public static Double toMg(Quantity q) {
        return switch (q.unit()) {
            case "mg" -> q.value();
            case "g" -> q.value() * 1000.0;
            case "ug" -> q.value() / 1000.0;
            default -> null;
        };
    }

    /** K per second, or null if not a convertible scan-rate unit. */
    public static Double rateToKPerS(Quantity q) {
        return switch (q.unit()) {
            case "K/min" -> q.value() / 60.0;
            case "K/s" -> q.value();
            case "C/min" -> q.value() / 60.0;
            default -> null;
        };
    }

    /** mW, or null if not a convertible heat-flow unit. */
    public static Double toMw(Quantity q) {
        return switch (q.unit()) {
            case "mW" -> q.value();
            case "W" -> q.value() * 1000.0;
            default -> null;
        };
    }
}
