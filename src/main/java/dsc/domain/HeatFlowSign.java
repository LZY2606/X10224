package dsc.domain;

/**
 * The declared instrument heat-flow convention. Enthalpy sign decisions must
 * be derived from this field; guessing from a file name is explicitly forbidden
 * (see SIGN_NOT_FROM_FILENAME ledger rule).
 */
public enum HeatFlowSign {
    /** Increasing signal means heat flows into sample (endothermic up). */
    ENDO_UP("endo-up"),
    /** Increasing signal means heat flows out of sample (exothermic up). */
    EXO_UP("exo-up"),
    UNKNOWN("undeclared");

    private final String label;

    HeatFlowSign(String label) { this.label = label; }

    public String label() { return label; }

    /** +1 when an upward peak in the raw trace is endothermic. */
    public int upwardIsEndothermic() {
        return switch (this) {
            case ENDO_UP -> 1;
            case EXO_UP -> -1;
            case UNKNOWN -> 0;
        };
    }
}
