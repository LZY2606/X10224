package dsc.domain;

/**
 * A domain measurement that always travels with its unit and original token.
 * Normalization happens only in the derivation layer; if a value cannot be
 * converted it stays in place and carries a diagnostic instead of being dropped.
 */
public final class Quantity {
    private final double value;
    private final String unit;
    private final String raw;
    private final String diagnostic;

    public Quantity(double value, String unit, String raw, String diagnostic) {
        this.value = value;
        this.unit = unit;
        this.raw = raw;
        this.diagnostic = diagnostic;
    }

    public static Quantity of(double value, String unit) {
        return new Quantity(value, unit, null, null);
    }

    public static Quantity raw(String raw) {
        return new Quantity(Double.NaN, null, raw, "unparseable: kept at source for audit");
    }

    public double value() { return value; }
    public String unit() { return unit; }
    public String raw() { return raw; }
    public String diagnostic() { return diagnostic; }
    public boolean convertible() { return diagnostic == null; }

    public Quantity withDiagnostic(String d) {
        return new Quantity(value, unit, raw, d);
    }
}
