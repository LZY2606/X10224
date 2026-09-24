package dsc.qty;

import java.util.Locale;

public enum HeatUnit {
    J_PER_G(1.0, "J/g"),
    KJ_PER_G(1000.0, "kJ/g"),
    J_PER_MG(1000.0, "J/mg");

    private final double factorToJPerG;
    private final String symbol;

    HeatUnit(double factorToJPerG, String symbol) {
        this.factorToJPerG = factorToJPerG;
        this.symbol = symbol;
    }

    public double toJPerG(double value) {
        return value * factorToJPerG;
    }

    public String symbol() {
        return symbol;
    }

    public static HeatUnit fromSymbol(String s) {
        String t = s.trim().toUpperCase(Locale.ROOT);
        for (HeatUnit u : values()) {
            if (u.symbol.equalsIgnoreCase(s.trim()) || u.name().equals(t)) {
                return u;
            }
        }
        throw new UnitException("Unknown heat unit: " + s);
    }
}
