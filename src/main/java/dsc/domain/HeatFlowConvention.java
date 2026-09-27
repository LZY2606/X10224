package dsc.domain;

/**
 * Exo/endo direction is decided ONLY from instrument metadata
 * (header field heat_flow_convention), never from the file name.
 */
public enum HeatFlowConvention {
    EXO_UP, EXO_DOWN, UNKNOWN;

    public static HeatFlowConvention fromMetadata(String headerValue) {
        if (headerValue == null) return UNKNOWN;
        return switch (headerValue.trim().toLowerCase()) {
            case "exo_up" -> EXO_UP;
            case "exo_down" -> EXO_DOWN;
            default -> UNKNOWN;
        };
    }
}
