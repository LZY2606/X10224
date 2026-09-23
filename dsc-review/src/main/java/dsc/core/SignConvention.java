package dsc.core;

/**
 * How the instrument encoded heat-flow direction in the archived output.
 *
 * This is the ONLY accepted source of endotherm/exotherm direction. It must be
 * read from the instrument header (or an explicit reviewer declaration recorded
 * as an event). File names such as "..._exo_up.csv" are never consulted.
 *
 * Internal normalized traces always use EXO_UP.
 */
public enum SignConvention {
    EXO_UP("exo_up", +1.0),
    ENDO_UP("endo_up", -1.0),
    UNDECLARED("undeclared", 0.0);

    private final String code;
    private final double toExoUpMultiplier;

    SignConvention(String code, double mult) {
        this.code = code;
        this.toExoUpMultiplier = mult;
    }

    public String code() { return code; }
    public double toExoUpMultiplier() { return toExoUpMultiplier; }
    public boolean isDeclared() { return this != UNDECLARED; }

    public static SignConvention fromHeader(String raw) {
        if (raw == null) return UNDECLARED;
        String t = raw.trim().toLowerCase().replace(' ', '_').replace('-', '_');
        switch (t) {
            case "exo_up": case "exotherm_up": case "exo+": return EXO_UP;
            case "endo_up": case "endotherm_up": case "endo+": return ENDO_UP;
            default: return UNDECLARED;
        }
    }
}
