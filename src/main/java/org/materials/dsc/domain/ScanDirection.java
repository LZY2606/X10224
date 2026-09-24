package org.materials.dsc.domain;

/**
 * Thermal direction is a first-class fact derived from temperature axis
 * progression of the segment, never from a file name.  Heating and cooling
 * segments must never be merged for analysis.
 */
public enum ScanDirection {
    HEATING("heating", +1),
    COOLING("cooling", -1),
    ISOTHERMAL("isothermal", 0),
    UNKNOWN("unknown", 0);

    private final String label;
    private final int sign;

    ScanDirection(String label, int sign) {
        this.label = label;
        this.sign = sign;
    }

    public String label() {
        return label;
    }

    public int sign() {
        return sign;
    }
}
