package dsc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A scalar with its original textual representation, original unit and canonical value.
 * The raw representation is never discarded on import; when conversion is impossible
 * {@link #convertible} is false, {@link #diagnostic} explains why and the value stays
 * in place so the raw layer is still auditable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Quantity {
    public String raw;
    public String unit;
    public Double value;
    public boolean convertible = true;
    public String canonicalUnit;
    public Double canonicalValue;
    public String diagnostic;

    public Quantity() {
    }

    public Quantity(String raw, String unit) {
        this.raw = raw;
        this.unit = unit;
        parse();
    }

    public Quantity(double value, String unit) {
        this.raw = Double.toString(value);
        this.unit = unit;
        parse();
    }

    private void parse() {
        if (raw == null || raw.isBlank()) {
            this.convertible = false;
            this.diagnostic = "missing value";
            return;
        }
        double v;
        try {
            v = Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            this.convertible = false;
            this.diagnostic = "not numeric: '" + raw + "'";
            return;
        }
        if (!Double.isFinite(v)) {
            this.convertible = false;
            this.diagnostic = "non-finite value: '" + raw + "'";
            return;
        }
        this.value = v;
        try {
            this.canonicalUnit = Unit.canonicalFor(unit);
            this.canonicalValue = Unit.toCanonical(v, unit);
        } catch (IllegalArgumentException e) {
            this.convertible = false;
            this.diagnostic = e.getMessage();
        }
    }

    /** Canonical numeric value, or null when the value cannot be converted. */
    public Double canonical() {
        return convertible ? canonicalValue : null;
    }

    public static Quantity of(double v, String unit) {
        return new Quantity(v, unit);
    }
}
