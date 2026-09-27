package dsc.domain;

/**
 * A domain quantity. Raw representation is preserved exactly as imported;
 * normalization happens only in the derived layer.
 */
public final class Quantity {
    public final String raw;        // original text as imported, never rewritten
    public final Double value;      // parsed numeric value, null if unparseable
    public final String unit;       // e.g. "degC", "K", "mW", "mg", "K/min"
    public final String basis;      // coordinate basis, e.g. "exo_up", "exo_down", "" if none
    public final String diagnostic; // non-null when the value cannot be converted/parsed

    public Quantity(String raw, Double value, String unit, String basis, String diagnostic) {
        this.raw = raw;
        this.value = value;
        this.unit = unit;
        this.basis = basis;
        this.diagnostic = diagnostic;
    }

    public static Quantity of(String raw, double value, String unit, String basis) {
        return new Quantity(raw, value, unit, basis, null);
    }

    public static Quantity unconvertible(String raw, String unit, String basis, String diagnostic) {
        return new Quantity(raw, null, unit, basis, diagnostic);
    }

    public boolean usable() {
        return value != null && diagnostic == null;
    }
}
