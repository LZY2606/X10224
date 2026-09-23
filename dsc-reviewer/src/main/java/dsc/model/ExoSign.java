package dsc.model;

/**
 * Instrument wiring convention: which deflection of the reported heat flow
 * means "exothermic". Must be carried by the instrument output itself.
 */
public enum ExoSign {
    HF_UP_IS_EXO,
    HF_DOWN_IS_EXO;

    public static ExoSign parse(String s) {
        if (s == null) {
            throw new IllegalArgumentException("missing exo sign convention");
        }
        return switch (s.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "up", "hf_up_is_exo", "exo_up", "+" -> HF_UP_IS_EXO;
            case "down", "hf_down_is_exo", "exo_down", "-" -> HF_DOWN_IS_EXO;
            default -> throw new IllegalArgumentException("unknown exo sign convention: " + s);
        };
    }
}
