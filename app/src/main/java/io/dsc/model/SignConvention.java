package io.dsc.model;

/**
 * Instrument plot convention: which y direction is exothermic.
 * Endothermic vs exothermic classification derives from this plus the segment
 * direction, never from a file name.
 */
public enum SignConvention {
    EXO_UP,   // positive heat flow deviation = exotherm (common heat-flux DSC)
    EXO_DOWN; // positive deviation = endotherm (power-compensation / reversed axis)

    public static SignConvention parse(String raw) {
        if (raw == null) return EXO_UP;
        return switch (raw.trim().toLowerCase().replace('_', '-')) {
            case "exo-up", "exo up", "exoup" -> EXO_UP;
            case "exo-down", "exo down", "exodown", "endo-up", "reverse" -> EXO_DOWN;
            default -> throw new IllegalArgumentException("unknown sign convention: " + raw);
        };
    }
}
