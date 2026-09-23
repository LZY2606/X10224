package dsc.core;

/**
 * A single imported cell with its original textual representation preserved.
 * When the text cannot be converted, numeric stays null and diagnostic is set;
 * the raw token is never discarded just because the plot needs numbers.
 */
public final class RawCell {

    private final String raw;
    private final Double numeric;
    private final String diagnostic;

    private RawCell(String raw, Double numeric, String diagnostic) {
        this.raw = raw;
        this.numeric = numeric;
        this.diagnostic = diagnostic;
    }

    public static RawCell parse(String raw) {
        if (raw == null) return new RawCell(null, null, "missing cell");
        String t = raw.trim();
        if (t.isEmpty()) return new RawCell(raw, null, "empty cell");
        try {
            return new RawCell(raw, Double.valueOf(t), null);
        } catch (NumberFormatException e) {
            return new RawCell(raw, null, "unconvertible token: '" + t + "' kept in place");
        }
    }

    public static RawCell numeric(double v) {
        return new RawCell(Double.toString(v), v, null);
    }

    public String raw() { return raw; }
    public Double numeric() { return numeric; }
    public String diagnostic() { return diagnostic; }
    public boolean isGap() { return numeric == null; }
}
