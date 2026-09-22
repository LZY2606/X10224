package dsc.core;

import java.util.Objects;

/**
 * A value exactly as it appeared in the instrument/operator source: original
 * textual representation is retained verbatim. If the text cannot be converted
 * to the declared unit it stays in place with a diagnostic instead of being
 * dropped to make plotting possible.
 */
public final class RawValue {
    private final String raw;
    private final Unit declaredUnit;

    public RawValue(String raw, Unit declaredUnit) {
        this.raw = Objects.requireNonNull(raw);
        this.declaredUnit = Objects.requireNonNull(declaredUnit);
    }

    public String raw() { return raw; }
    public Unit declaredUnit() { return declaredUnit; }

    /** Parse result: either a quantity or a diagnostic, never both null. */
    public Parsed parse() {
        String t = raw.trim();
        if (t.isEmpty()) return new Parsed(null, "empty token");
        try {
            double v = Double.parseDouble(t);
            if (Double.isNaN(v)) return new Parsed(null, "NaN token not accepted");
            return new Parsed(new Quantity(v, declaredUnit), null);
        } catch (NumberFormatException e) {
            return new Parsed(null, "not a number: " + t);
        }
    }

    public record Parsed(Quantity quantity, String diagnostic) {
        public boolean ok() { return quantity != null; }
    }

    @Override public String toString() { return raw + " " + declaredUnit.symbol(); }
}
