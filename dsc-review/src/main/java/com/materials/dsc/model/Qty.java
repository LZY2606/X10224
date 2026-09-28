package com.materials.dsc.model;

/**
 * A physical quantity as imported (raw representation preserved) together with
 * the optional normalised value produced by the derived layer.  A value that
 * cannot be converted stays in place with a diagnostic instead of being
 * dropped for plotting convenience.
 */
public final class Qty {
    public final String raw;
    public final Double value;
    public final String unit;
    public final String diagnostic;

    private Qty(String raw, Double value, String unit, String diagnostic) {
        this.raw = raw;
        this.value = value;
        this.unit = unit;
        this.diagnostic = diagnostic;
    }

    public static Qty parsed(String raw, double value, String unit) {
        return new Qty(raw, value, unit, null);
    }

    public static Qty unconvertible(String raw, String unit, String diagnostic) {
        return new Qty(raw, null, unit, diagnostic);
    }

    public boolean usable() {
        return value != null && diagnostic == null;
    }
}
