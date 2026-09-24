package dsc.qty;

import java.util.Locale;

public enum RateUnit {
    C_PER_MIN(1.0, "°C/min"),
    K_PER_MIN(1.0, "K/min"),
    C_PER_SEC(60.0, "°C/s");

    private final double factorToCPerMin;
    private final String symbol;

    RateUnit(double factorToCPerMin, String symbol) {
        this.factorToCPerMin = factorToCPerMin;
        this.symbol = symbol;
    }

    public double toCPerMin(double value) {
        return value * factorToCPerMin;
    }

    public String symbol() {
        return symbol;
    }

    public static RateUnit fromSymbol(String s) {
        String t = s.trim().toUpperCase(Locale.ROOT);
        for (RateUnit u : values()) {
            if (u.symbol().equalsIgnoreCase(s.trim()) || u.name().equals(t)) {
                return u;
            }
        }
        throw new UnitException("Unknown scan rate unit: " + s);
    }
}
