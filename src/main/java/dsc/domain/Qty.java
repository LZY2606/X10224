package dsc.domain;

/**
 * A physical quantity that keeps its original textual representation and unit.
 * Normalisation to a canonical unit happens only in the derivation layer;
 * a value that cannot be converted stays at its origin with a diagnostic.
 */
public record Qty(double value, String unit, String raw) {
    public Qty {
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("unit is mandatory");
        }
    }

    public static Qty of(double value, String unit) {
        return new Qty(value, unit, format(value, unit));
    }

    public static Qty raw(String text, String unit) {
        return new Qty(parseLoose(text), unit, text);
    }

    public static double parseLoose(String text) {
        return Double.parseDouble(text.replaceAll("[^0-9eE+\\-.]", ""));
    }

    private static String format(double v, String u) {
        return Double.toString(v) + " " + u;
    }

    /** Canonical value in the requested unit, or empty when conversion is unknown. */
    public double to(String targetUnit) {
        if (targetUnit.equals(unit)) return value;
        Double f = Units.factor(unit, targetUnit);
        if (f == null) {
            throw new UnconvertableException(unit, targetUnit);
        }
        return value * f;
    }

    public boolean convertibleTo(String targetUnit) {
        return targetUnit.equals(unit) || Units.factor(unit, targetUnit) != null;
    }

    public static final class UnconvertableException extends RuntimeException {
        public UnconvertableException(String from, String to) {
            super("cannot convert " + from + " -> " + to);
        }
    }
}
