package org.matlab.dsc.unit;

import java.util.Map;

/**
 * Units known to the normalisation layer. Everything above the raw ingestion
 * layer references one of these; the raw layer keeps the instrument's own
 * representation untouched.
 */
public enum Unit {
    K("K", Dimension.TEMPERATURE, 1.0, 0.0),
    CELSIUS("°C", Dimension.TEMPERATURE, 1.0, 273.15),
    MILLIWATT("mW", Dimension.POWER, 1.0, 0.0),
    W("W", Dimension.POWER, 1000.0, 0.0),
    MILLIGRAM("mg", Dimension.MASS, 1.0, 0.0),
    GRAM("g", Dimension.MASS, 1000.0, 0.0),
    K_PER_MIN("K/min", Dimension.RATE, 1.0, 0.0),
    C_PER_MIN("°C/min", Dimension.RATE, 1.0, 0.0),
    ML_PER_MIN("mL/min", Dimension.FLOW, 1.0, 0.0),
    J_PER_G("J/g", Dimension.SPECIFIC_ENERGY, 1.0, 0.0),
    JOULE("J", Dimension.ENERGY, 1.0, 0.0);

    private static final Map<String, Unit> ALIASES = Map.of(
            "degC", CELSIUS, "C", CELSIUS, "celsius", CELSIUS,
            "kelvin", K,
            "milliwatt", MILLIWATT,
            "milliwatt (endo up)", MILLIWATT,
            "watt", W,
            "milligram", MILLIGRAM,
            "gram", GRAM,
            "K min-1", K_PER_MIN, "°C min-1", C_PER_MIN,
            "ml/min", ML_PER_MIN,
            "Jg-1", J_PER_G);

    private final String symbol;
    private final Dimension dimension;
    private final double factor;
    private final double offset;

    Unit(String symbol, Dimension dimension, double factor, double offset) {
        this.symbol = symbol;
        this.dimension = dimension;
        this.factor = factor;
        this.offset = offset;
    }

    public String symbol() {
        return symbol;
    }

    public Dimension dimension() {
        return dimension;
    }

    /** Convert a numeric value expressed in this unit into canonical SI-ish units. */
    public double toCanonical(double value) {
        return value * factor + offset;
    }

    public static Unit parse(String raw) {
        if (raw == null) {
            throw new UnitConversionException("missing unit");
        }
        String trimmed = raw.trim();
        for (Unit u : values()) {
            if (u.symbol.equals(trimmed)) {
                return u;
            }
        }
        Unit alias = ALIASES.get(trimmed);
        if (alias != null) {
            return alias;
        }
        throw new UnitConversionException("unconvertible unit: " + raw);
    }
}
