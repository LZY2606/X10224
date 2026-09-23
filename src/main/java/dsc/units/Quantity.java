package dsc.units;

import java.util.Locale;
import java.util.Objects;

/**
 * A scalar value together with the physical unit it was expressed in.
 *
 * <p>The original textual representation is retained verbatim (see
 * {@link #raw}); derived layers convert from {@link #value()} only when a
 * conversion is known, otherwise the value stays at its origin with a
 * diagnostic instead of being silently dropped.
 */
public final class Quantity {

    public enum Kind {
        TEMPERATURE,      // canonical: K
        MASS,             // canonical: mg
        HEAT_FLOW,        // canonical: W
        HEAT_FLOW_RATE,   // canonical: W/g (specific heat flow)
        RATE,             // canonical: K/min
        ENERGY_PER_MASS,  // canonical: J/g
        ENERGY,           // canonical: J
        DIMENSIONLESS,
        UNKNOWN
    }

    private final double value;
    private final String unit;
    private final String raw;
    private final Kind kind;
    private final boolean convertible;

    private Quantity(double value, String unit, String raw, Kind kind, boolean convertible) {
        this.value = value;
        this.unit = unit;
        this.raw = raw;
        this.kind = kind;
        this.convertible = convertible;
    }

    /** Parsed, convertible quantity. */
    public static Quantity of(double value, String unit) {
        Kind kind = Units.classify(unit);
        return new Quantity(value, unit, format(value, unit), kind, kind != Kind.UNKNOWN);
    }

    /** Quantity with its original on-disk representation preserved. */
    public static Quantity of(double value, String unit, String raw) {
        Kind kind = Units.classify(unit);
        return new Quantity(value, unit, raw == null ? format(value, unit) : raw,
                kind, kind != Kind.UNKNOWN);
    }

    /**
     * A value that could not be parsed or converted. It is kept at its
     * origin; {@link #convertible()} is false and {@link #value()} is NaN.
     */
    public static Quantity unconvertible(String raw, String unit) {
        return new Quantity(Double.NaN, unit == null ? "" : unit, raw,
                Units.classify(unit), false);
    }

    public double value() { return value; }
    public String unit() { return unit; }
    public String raw() { return raw; }
    public Kind kind() { return kind; }
    public boolean convertible() { return convertible; }

    /** Value converted to the canonical unit of its {@link Kind}. */
    public double canonical() {
        if (!convertible) return Double.NaN;
        return Units.toCanonical(value, unit);
    }

    public String canonicalUnit() {
        return switch (kind) {
            case TEMPERATURE -> "K";
            case MASS -> "mg";
            case HEAT_FLOW -> "W";
            case HEAT_FLOW_RATE -> "W/g";
            case RATE -> "K/min";
            case ENERGY_PER_MASS -> "J/g";
            case ENERGY -> "J";
            default -> unit;
        };
    }

    private static String format(double v, String u) {
        return String.format(Locale.ROOT, "%g %s", v, u);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Quantity q)) return false;
        return Double.compare(q.value, value) == 0
                && Objects.equals(unit, q.unit)
                && Objects.equals(raw, q.raw);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, unit, raw);
    }

    @Override
    public String toString() {
        return raw;
    }
}
