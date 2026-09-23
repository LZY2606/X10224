package dsc.core;

/**
 * Heat-flow polarity as declared by the instrument output.
 *
 * The endo/exo direction is never guessed from a file name: every archived
 * instrument segment carries an explicit polarity declaration.
 */
public enum SignConvention {
    ENDO_UP("endo-up"),
    ENDO_DOWN("endo-down"),
    UNKNOWN("unknown");

    private final String token;
    SignConvention(String token) { this.token = token; }
    public String token() { return token; }

    public static SignConvention fromToken(String raw) {
        if (raw == null) return UNKNOWN;
        return switch (raw.trim().toLowerCase()) {
            case "endo-up", "endoup", "endo_up", "exo-down", "exodown" -> ENDO_UP;
            case "endo-down", "endodown", "endo_down", "exo-up", "exoup" -> ENDO_DOWN;
            default -> UNKNOWN;
        };
    }

    /**
     * Multiplier mapping instrument watts into the canonical frame
     * "endothermic heat-flow positive" (endo-up).
     */
    public double toEndoUpFactor() {
        return this == ENDO_DOWN ? -1.0 : 1.0;
    }
}
