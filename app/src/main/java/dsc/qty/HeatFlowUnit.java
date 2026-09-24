package dsc.qty;

import java.util.Locale;

/**
 * DSC heat-flow per-mass units. The sign convention (which direction is exothermic)
 * is carried separately by SignConvention; it must never be guessed from a file name.
 */
public enum HeatFlowUnit {
    W_PER_G(1.0, "W/g"),
    MW_PER_G(0.001, "mW/g"),
    J_PER_GS(1.0, "J/(g·s)");

    private final double factorToWPerG;
    private final String symbol;

    HeatFlowUnit(double factorToWPerG, String symbol) {
        this.factorToWPerG = factorToWPerG;
        this.symbol = symbol;
    }

    public double toWPerG(double value) {
        return value * factorToWPerG;
    }

    public String symbol() {
        return symbol;
    }

    public static HeatFlowUnit fromSymbol(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        for (HeatFlowUnit u : values()) {
            if (u.symbol().equalsIgnoreCase(s.trim()) || u.name().equalsIgnoreCase(t)) {
                return u;
            }
        }
        throw new UnitException("Unknown heat flow unit: " + s);
    }
}
