package org.materials.dsc.domain;

/**
 * Sign convention for the heat-flow axis as delivered by the instrument.
 * Instrument files disagree about which way is exothermic, so the convention
 * must be an explicit, archived property of the instrument output.
 */
public enum HeatFlowConvention {
    /** Positive heat flow = endothermic (common in many DSC exports). */
    ENDO_UP("endo_up", +1),
    /** Positive heat flow = exothermic. */
    EXO_UP("exo_up", -1);

    private final String code;
    private final int toEndoUpFactor;

    HeatFlowConvention(String code, int toEndoUpFactor) {
        this.code = code;
        this.toEndoUpFactor = toEndoUpFactor;
    }

    public String code() {
        return code;
    }

    public int toEndoUpFactor() {
        return toEndoUpFactor;
    }

    public static HeatFlowConvention fromCode(String raw) {
        if (raw == null) {
            return null;
        }
        String c = raw.trim().toLowerCase().replace(' ', '_');
        for (HeatFlowConvention v : values()) {
            if (v.code.equals(c)) {
                return v;
            }
        }
        if (c.contains("exo")) {
            return EXO_UP;
        }
        if (c.contains("endo")) {
            return ENDO_UP;
        }
        return null;
    }
}
