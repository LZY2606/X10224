package dsc.model;

/**
 * Instrument sign convention. Exo/endo direction is carried by explicit
 * instrument metadata, never guessed from file names.
 */
public enum SignConvention {
    /** Positive heat flow = exothermic (common TA convention). */
    EXO_UP,
    /** Positive heat flow = endothermic (common Mettler convention). */
    ENDO_UP;

    public static SignConvention parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("missing sign convention");
        }
        switch (raw.trim().toLowerCase().replace(' ', '_')) {
            case "exo_up":
            case "exo-up":
            case "exoup":
                return EXO_UP;
            case "endo_up":
            case "endo-up":
            case "endoup":
                return ENDO_UP;
            default:
                throw new IllegalArgumentException("unrecognised sign convention: " + raw);
        }
    }

    public boolean positiveIsExothermic() {
        return this == EXO_UP;
    }
}
