package dsc.domain;

/**
 * Thermal direction is an explicit, independently archived instrument fact.
 * It must never be inferred from file names: a file named *_heat.csv may still
 * carry a cooling segment, and deriving the sign from the name is forbidden.
 */
public enum SegmentKind {
    HEATING("heating", "加热段"),
    COOLING("cooling", "冷却段"),
    ISOTHERMAL("isothermal", "等温段");

    public final String code;
    public final String label;

    SegmentKind(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static SegmentKind fromCode(String code) {
        for (SegmentKind k : values()) if (k.code.equals(code)) return k;
        throw new IllegalArgumentException("unknown segment kind: " + code);
    }

    /** True only when the two segments may share derived analyses. Heating and cooling never mix. */
    public boolean compatibleWith(SegmentKind other) {
        if (other == null) return false;
        if (this == ISOTHERMAL || other == ISOTHERMAL) return false;
        return this == other;
    }
}
