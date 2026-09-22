package dsc.rules;

/**
 * Heat-flow sign convention.
 *
 * "exo-up" (the common DSC convention: positive signal = exotherm, e.g. crystallization
 * on cooling) and "exo-down" (positive = endotherm, e.g. melting on heating) are both
 * legitimate instrument conventions. The direction of endothermic/exothermic events must
 * be taken from explicit instrument metadata — never guessed from a file name.
 */
public final class Polarity {

    public static final String EXO_UP = "exo-up";
    public static final String EXO_DOWN = "exo-down";

    private Polarity() {
    }

    public static boolean known(String p) {
        return EXO_UP.equals(p) || EXO_DOWN.equals(p);
    }

    /**
     * Normalize to an "endotherm-positive" signal (W/g).
     *
     * @param rawSigned raw heat-flow value already divided by mass when applicable
     */
    public static double endoPositive(double rawSigned, String polarity) {
        return switch (polarity) {
            case EXO_DOWN -> rawSigned;
            case EXO_UP -> -rawSigned;
            default -> throw new IllegalArgumentException("unknown polarity: " + polarity);
        };
    }

    /**
     * Sign of an integrated area under endo-positive convention.
     *
     * @return "endothermic" for positive (melting/glass step convention), else "exothermic"
     */
    public static String classify(double endoPositiveArea) {
        return endoPositiveArea >= 0 ? "endothermic" : "exothermic";
    }
}
