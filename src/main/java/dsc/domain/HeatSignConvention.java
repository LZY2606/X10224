package dsc.domain;

/**
 * Instrument sign convention, declared by the instrument output header.
 * Exo-up means a melting (endothermic on heating) peak points down.
 * Direction is resolved with this convention + the explicit segment kind,
 * never with the file name.
 */
public enum HeatSignConvention {
    EXO_UP("exo_up"),
    ENDO_UP("endo_up"),
    UNKNOWN("unknown");

    public final String code;

    HeatSignConvention(String code) { this.code = code; }

    public static HeatSignConvention fromCode(String code) {
        for (HeatSignConvention c : values()) if (c.code.equals(code)) return c;
        return UNKNOWN;
    }
}
