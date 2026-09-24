package dsc.model;

import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable physical quantity: numeric value plus unit.
 * Values are never normalised in place; the raw representation is carried through import and only
 * converted in the derivation layer (see {@code Units}).
 */
public final class Quant {
    private final double value;
    private final String unit;
    /** Set when a source cell could not be converted; value is kept at its original location. */
    private final String raw;
    private final String diagnostic;

    @JsonCreator
    private Quant(@JsonProperty("value") Double value, @JsonProperty("unit") String unit,
                  @JsonProperty("raw") String raw, @JsonProperty("diagnostic") String diagnostic) {
        this.value = value == null ? Double.NaN : value;
        this.unit = unit;
        this.raw = raw;
        this.diagnostic = diagnostic;
    }

    public Quant(double value, String unit) {
        this(value, unit, null, null);
    }

    public static Quant of(double value, String unit) {
        return new Quant(value, unit);
    }

    public static Quant unconverted(String raw, String unit, String diagnostic) {
        return new Quant(null, unit, raw, diagnostic);
    }

    public double value() {
        return value;
    }

    public String unit() {
        return unit;
    }

    public String raw() {
        return raw;
    }

    public String diagnostic() {
        return diagnostic;
    }

    public boolean isValid() {
        return raw == null && !Double.isNaN(value);
    }

    public Quant withValue(double newValue) {
        return new Quant(newValue, unit, null, null);
    }

    @Override
    public String toString() {
        if (raw != null) {
            return String.format(Locale.ROOT, "%s? [%s] (%s)", raw, unit, diagnostic);
        }
        return String.format(Locale.ROOT, "%.6g %s", value, unit);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Quant quant)) return false;
        return Double.compare(quant.value, value) == 0 && Objects.equals(unit, quant.unit)
                && Objects.equals(raw, quant.raw);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, unit, raw);
    }
}
