package dsc.derive;

/** Rule bundle version. Historical derivations snapshot this and are never silently reinterpreted. */
public final class Rules {
    public static final String VERSION = "dsc-rules-1.0.0";

    public static final String METHOD_CHORD = "CHORD";
    public static final String METHOD_TANGENT_LS = "TANGENT_LS";
    public static final String METHOD_STEP_TANGENT = "STEP_TANGENT";

    private Rules() {
    }
}
