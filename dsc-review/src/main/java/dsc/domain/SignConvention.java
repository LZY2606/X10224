package dsc.domain;

/**
 * Endothermic/exothermic plotting convention. The sign never comes from the
 * file name: it comes from the instrument metadata (heatflow-sign /
 * plot-convention). When the metadata is missing the series stays in an
 * UNKNOWN sign state and integrations report the diagnostic rather than
 * guessing.
 */
public enum SignConvention {
    /** Positive heat-flow numbers are endothermic (common TA convention). */
    ENDO_UP,
    /** Positive heat-flow numbers are exothermic (common Mettler convention). */
    EXO_UP,
    UNKNOWN;

    public static SignConvention fromMetadata(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        String s = raw.trim().toLowerCase(java.util.Locale.ROOT)
                .replace('_', '-').replace(' ', '-');
        return switch (s) {
            case "endo-up", "endoup", "endo-positive", "endothermic-up", "ta" -> ENDO_UP;
            case "exo-up", "exoup", "exo-positive", "exothermic-up", "mettler" -> EXO_UP;
            default -> UNKNOWN;
        };
    }

    public String code() {
        return switch (this) {
            case ENDO_UP -> "endo_up";
            case EXO_UP -> "exo_up";
            case UNKNOWN -> "unknown";
        };
    }
}
