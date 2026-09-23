package dsc.model;

/**
 * Declared heat-flow sign convention of an instrument output. The direction of
 * an endotherm MUST come from this declaration (or calibration metadata); it
 * must never be guessed from a file name.
 */
public enum SignConvention {
    ENDO_UP,    // endothermic events deflect upward (positive)
    ENDO_DOWN,  // endothermic events deflect downward (negative)
    UNKNOWN     // declaration absent: treated as a diagnostic, not as a guess
}
