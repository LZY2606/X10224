package dsc.core;

/**
 * Exothermic/endothermic convention of the instrument trace. The convention is
 * declared by the instrument metadata; it must never be inferred from a file name.
 */
public enum HeatSign {
    /** Upward deflection is exothermic (NETZSCH-style). */
    EXO_UP("up"),
    /** Downward deflection is exothermic (TA/Setaram-style). */
    EXO_DOWN("down"),
    /** Instrument failed to declare a convention. */
    UNDECLARED("");

    private final String token;

    HeatSign(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    public static HeatSign fromToken(String token) {
        if (token == null) {
            return UNDECLARED;
        }
        String t = token.trim().toLowerCase().replace(" ", "").replace("_", "-");
        return switch (t) {
            case "exo-up", "exoup", "up" -> EXO_UP;
            case "exo-down", "exodown", "down" -> EXO_DOWN;
            default -> UNDECLARED;
        };
    }
}
