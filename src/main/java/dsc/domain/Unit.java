package dsc.domain;

/**
 * Measurement units with explicit conversion factors to a canonical unit.
 * Values that cannot be converted stay in their original representation and
 * carry a diagnostic; normalization never silently drops data.
 */
public enum Unit {
    // temperature -> kelvin
    K("K", "temperature", 1.0, 0.0),
    DEGC("°C", "temperature", 1.0, 273.15),
    DEGF("°F", "temperature", 5.0 / 9.0, 459.67 * 5.0 / 9.0),

    // heat flow -> watt
    W("W", "heat_flow", 1.0, 0.0),
    MW("mW", "heat_flow", 0.001, 0.0),

    // time -> second
    S("s", "time", 1.0, 0.0),
    MIN("min", "time", 60.0, 0.0),

    // mass -> kilogram
    KG("kg", "mass", 1.0, 0.0),
    G("g", "mass", 0.001, 0.0),
    MG("mg", "mass", 0.000001, 0.0),

    // scan rate -> K/min
    K_PER_MIN("K/min", "scan_rate", 1.0, 0.0),
    K_PER_S("K/s", "scan_rate", 60.0, 0.0),

    // enthalpy per mass -> J/g
    J_PER_G("J/g", "specific_enthalpy", 1.0, 0.0),
    J_PER_KG("J/kg", "specific_enthalpy", 0.001, 0.0);

    private final String symbol;
    private final String dimension;
    private final double factor;
    private final double offset;

    Unit(String symbol, String dimension, double factor, double offset) {
        this.symbol = symbol;
        this.dimension = dimension;
        this.factor = factor;
        this.offset = offset;
    }

    public String symbol() {
        return symbol;
    }

    public String dimension() {
        return dimension;
    }

    public double toCanonical(double value) {
        return value * factor + offset;
    }

    public double fromCanonical(double canonical) {
        return (canonical - offset) / factor;
    }

    public boolean sameDimension(Unit other) {
        return this.dimension.equals(other.dimension);
    }

    public static Unit fromSymbol(String symbol) {
        if (symbol == null) {
            return null;
        }
        String s = symbol.trim();
        for (Unit u : values()) {
            if (u.symbol.equals(s) || u.name().equalsIgnoreCase(s)) {
                return u;
            }
        }
        return null;
    }
}
