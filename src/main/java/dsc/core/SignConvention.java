package dsc.core;

/**
 * Whether the instrument reports exotherm up (positive HF = exotherm) or
 * endotherm up. This must come from instrument metadata; file names are never
 * consulted. Internally all analysis uses EXO_UP (exotherm positive).
 */
public enum SignConvention {
    EXO_UP, ENDO_UP, UNKNOWN;

    /** Multiplier applied to raw heat flow so the internal convention is exo-up. */
    public double toExoUp(SignConvention from) {
        return switch (from) {
            case ENDO_UP -> -1.0;
            case EXO_UP -> 1.0;
            default -> throw new IllegalStateException("sign convention unknown");
        };
    }
}
