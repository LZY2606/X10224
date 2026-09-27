package dsc.domain;

/**
 * Endothermic / exothermic assignment. Determined from the measured signal and
 * the instrument's declared exotherm convention, never from a file name.
 */
public enum ThermalSign {
    ENDOTHERMIC("endothermic"),
    EXOTHERMIC("exothermic"),
    UNDETERMINED("undetermined");

    private final String code;

    ThermalSign(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
