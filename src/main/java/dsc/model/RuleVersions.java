package dsc.model;

/**
 * Versions of the rule set. Each derived event records which rule version
 * produced it; rewinding to an event reconstructs the visible conclusions
 * that version gave, instead of overwriting history with the newest rules.
 */
public final class RuleVersions {

    private RuleVersions() {}

    public static final String CURRENT = "dsc-rules-1.0.0";

    public static final String R_DIRECTION = "DIR-DATA-1";
    public static final String R_NO_MIX = "SEG-SEP-1";
    public static final String R_BASELINE_CHORD = "BL-CHORD-1";
    public static final String R_BASELINE_TANGENT = "BL-TANGENT-1";
    public static final String R_BASELINE_STEP = "BL-STEP-1";
    public static final String R_INTEGRAL = "INT-NONUNIFORM-1";
    public static final String R_ENTHALPY_SIGN = "DH-SIGN-1";
    public static final String R_MASS_NORMALIZE = "MASS-NORM-1";
    public static final String R_CALIBRATION = "TEMP-CAL-1";
    public static final String R_OVERLAP = "OVERLAP-1";
    public static final String R_EMPTY = "EMPTY-1";
    public static final String R_DEPGRAPH = "DEPGRAPH-1";
}
