package dsc.model;

/**
 * Which way the instrument plotted the heat-flow axis. This is read from the
 * instrument header/metadata. If it is unknown the enthalpy sign is reported as
 * AMBIGUOUS rather than guessed from the file name or the sweep direction.
 */
public enum SignalConvention {
    EXO_UP("exo-up", "exothermic deflection points up"),
    ENDO_UP("endo-up", "endothermic deflection points up"),
    UNKNOWN("unknown", "convention not declared; sign cannot be decided");

    private final String code;
    private final String description;
    SignalConvention(String code, String description) { this.code = code; this.description = description; }
    public String code() { return code; }
    public String description() { return description; }

    public static SignalConvention parse(String raw) {
        if (raw == null) return UNKNOWN;
        String s = raw.trim().toLowerCase();
        return switch (s) {
            case "exo-up", "exo_up", "exoup", "exotherm-up" -> EXO_UP;
            case "endo-up", "endo_up", "endoup", "endotherm-up" -> ENDO_UP;
            default -> UNKNOWN;
        };
    }
}
