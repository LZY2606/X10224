package dsc.model;

import java.util.Objects;

/**
 * A value as it appeared in the imported file, kept alongside the normalized value.
 * Unconvertible values stay in place: {@code normalized} is null and a diagnostic is attached.
 */
public final class RawField {
    private final String raw;
    private final Double normalized;
    private final String unit;

    public RawField(String raw, Double normalized, String unit) {
        this.raw = raw;
        this.normalized = normalized;
        this.unit = unit;
    }

    public static RawField unconvertible(String raw, String unit) {
        return new RawField(raw, null, unit);
    }

    public String raw() {
        return raw;
    }

    public Double normalized() {
        return normalized;
    }

    public String unit() {
        return unit;
    }

    public boolean convertible() {
        return normalized != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RawField)) return false;
        RawField r = (RawField) o;
        return Objects.equals(raw, r.raw) && Objects.equals(unit, r.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(raw, unit);
    }
}
