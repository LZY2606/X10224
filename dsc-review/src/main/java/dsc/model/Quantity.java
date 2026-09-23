package dsc.model;

/**
 * A scalar domain quantity that always carries its unit or coordinate basis.
 * Raw imports preserve the original textual representation; normalization to
 * the internal canonical unit happens only in the derivation layer.
 */
public record Quantity(double value, String unit) {

    public static final String CELSIUS = "degC";
    public static final String KELVIN = "K";
    public static final String MG = "mg";
    public static final String G = "g";
    public static final String K_PER_MIN = "K/min";
    public static final String MW = "mW";
    public static final String W = "W";
    public static final String J_PER_G = "J/g";
    public static final String MJ = "mJ";
    public static final String J = "J";

    /** Convert a temperature expressed in its declared unit to degrees Celsius. */
    public static double toCelsius(double value, String unit) {
        return switch (unit) {
            case CELSIUS -> value;
            case KELVIN -> value - 273.15;
            default -> throw new IllegalArgumentException("unsupported temperature unit: " + unit);
        };
    }

    /** Convert a mass expressed in its declared unit to milligrams. */
    public static double toMg(double value, String unit) {
        return switch (unit) {
            case MG -> value;
            case G -> value * 1000.0;
            default -> throw new IllegalArgumentException("unsupported mass unit: " + unit);
        };
    }

    public static double toMw(double value, String unit) {
        return switch (unit) {
            case MW -> value;
            case W -> value * 1000.0;
            default -> throw new IllegalArgumentException("unsupported heat-flow unit: " + unit);
        };
    }

    public static double fromCelsius(double celsius, String unit) {
        return switch (unit) {
            case CELSIUS -> celsius;
            case KELVIN -> celsius + 273.15;
            default -> throw new IllegalArgumentException("unsupported temperature unit: " + unit);
        };
    }

    public String format() {
        return trim(value) + " " + unit;
    }

    public static String trim(double v) {
        if (v == (long) v) return Long.toString((long) v);
        return String.format(java.util.Locale.ROOT, "%.6g", v);
    }
}
