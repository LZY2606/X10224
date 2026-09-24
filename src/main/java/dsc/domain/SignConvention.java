package dsc.domain;

import java.util.Locale;

/**
 * Plot/sign convention declared by the instrument header.
 * ENDO_UP: endothermic peaks point up; EXO_UP: exothermic peaks point up
 * (the DSC-3/TA style reverse heat flow). The sign is never guessed from a
 * file name; a missing/invalid convention is retained as a blocking unknown.
 */
public enum SignConvention {
    ENDO_UP, EXO_UP, UNKNOWN;

    /** +1 if an endotherm points upward under this convention, else -1. */
    public int endoUpSign() {
        return switch (this) {
            case ENDO_UP -> 1;
            case EXO_UP -> -1;
            case UNKNOWN -> 0;
        };
    }

    public static SignConvention parse(String raw) {
        if (raw == null) return UNKNOWN;
        String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]", "");
        return switch (s) {
            case "endoup", "endo+", "endothermup" -> ENDO_UP;
            case "exoup", "exo+", "exothermup" -> EXO_UP;
            default -> UNKNOWN;
        };
    }
}
