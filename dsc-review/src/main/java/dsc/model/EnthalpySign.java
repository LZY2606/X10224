package dsc.model;

/** Thermodynamic sign of a transition enthalpy. */
public enum EnthalpySign {
    ENDOTHERMIC("endothermic", +1),
    EXOTHERMIC("exothermic", -1),
    /** Convention missing or contradictory: magnitude is kept, sign is not guessed. */
    AMBIGUOUS("ambiguous", 0);

    private final String code;
    private final int deltaHSign;
    EnthalpySign(String code, int deltaHSign) { this.code = code; this.deltaHSign = deltaHSign; }
    public String code() { return code; }
    public int deltaHSign() { return deltaHSign; }
}
