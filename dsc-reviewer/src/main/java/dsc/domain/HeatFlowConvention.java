package dsc.domain;

/**
 * Endothermic/exothermic sign convention. This MUST come from instrument
 * metadata — never from a file name such as "heating_run.csv".
 * ENDO_UP: endothermic events deflect the heat-flow signal upward (positive).
 * ENDO_DOWN: endothermic events deflect downward (negative).
 * UNKNOWN: the header did not state a convention; direction-sensitive
 * integration is refused until the analyst asserts one.
 */
public enum HeatFlowConvention {
    ENDO_UP,
    ENDO_DOWN,
    UNKNOWN;

    public static HeatFlowConvention fromHeader(String raw) {
        if (raw == null) return UNKNOWN;
        String s = raw.trim().toLowerCase();
        if (s.contains("endo") && (s.contains("up") || s.contains("positive") || s.contains("+"))) {
            return ENDO_UP;
        }
        if (s.contains("exo") && (s.contains("up") || s.contains("positive"))) {
            return ENDO_DOWN;
        }
        if (s.contains("endo") && (s.contains("down") || s.contains("negative") || s.contains("-"))) {
            return ENDO_DOWN;
        }
        if (s.contains("exo") && (s.contains("down") || s.contains("negative"))) {
            return ENDO_UP;
        }
        return UNKNOWN;
    }
}
