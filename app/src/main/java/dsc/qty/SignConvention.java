package dsc.qty;

/**
 * Declared sign convention of an instrument output. Explicit source metadata only;
 * file names are never consulted.
 */
public enum SignConvention {
    /** Positive (upward) heat flow means exothermic. */
    EXO_UP("exo-up"),
    /** Positive (upward) heat flow means endothermic. */
    ENDO_UP("endo-up");

    private final String code;

    SignConvention(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static SignConvention fromCode(String s) {
        String t = s.trim().toLowerCase();
        for (SignConvention v : values()) {
            if (v.code.equals(t) || v.name().equalsIgnoreCase(t)) {
                return v;
            }
        }
        throw new UnitException("Unknown sign convention: " + s);
    }
}
