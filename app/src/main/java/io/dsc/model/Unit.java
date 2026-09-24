package io.dsc.model;

import java.util.Map;
import java.util.OptionalDouble;

/**
 * Physical unit with conversion to a canonical SI-ish base unit.
 * Quantities carry their original unit; normalisation happens only in the
 * derivation layer. Values that cannot be converted stay at their origin and
 * carry a diagnostic instead of being dropped for plotting.
 */
public enum Unit {
    C("°C", "K", 1.0, 273.15, QuantityKind.TEMPERATURE),
    K("K", "K", 1.0, 0.0, QuantityKind.TEMPERATURE),
    MG("mg", "kg", 1e-6, 0.0, QuantityKind.MASS),
    G("g", "kg", 1e-3, 0.0, QuantityKind.MASS),
    MG_PER_S("mg/s", "kg/s", 1e-6, 0.0, QuantityKind.HEAT_FLOW),
    MW("mW", "W", 1e-3, 0.0, QuantityKind.HEAT_FLOW),
    W("W", "W", 1.0, 0.0, QuantityKind.HEAT_FLOW),
    K_PER_MIN("K/min", "K/s", 1.0 / 60.0, 0.0, QuantityKind.SCAN_RATE),
    C_PER_MIN("°C/min", "K/s", 1.0 / 60.0, 0.0, QuantityKind.SCAN_RATE),
    J_PER_G("J/g", "J/kg", 1e3, 0.0, QuantityKind.SPEC_ENTHALPY),
    MJ("mJ", "J", 1e-3, 0.0, QuantityKind.ENERGY),
    J("J", "J", 1.0, 0.0, QuantityKind.ENERGY);

    public enum QuantityKind { TEMPERATURE, MASS, HEAT_FLOW, SCAN_RATE, SPEC_ENTHALPY, ENERGY, NONE }

    private final String symbol;
    private final String baseSymbol;
    private final double factor;
    private final double offset;
    private final QuantityKind kind;

    Unit(String symbol, String baseSymbol, double factor, double offset, QuantityKind kind) {
        this.symbol = symbol;
        this.baseSymbol = baseSymbol;
        this.factor = factor;
        this.offset = offset;
        this.kind = kind;
    }

    public String symbol() { return symbol; }
    public String baseSymbol() { return baseSymbol; }
    public QuantityKind kind() { return kind; }

    public double toBase(double value) {
        return value * factor + offset;
    }

    public double fromBase(double baseValue) {
        return (baseValue - offset) / factor;
    }

    private static final Map<String, Unit> BY_SYMBOL = Map.of(
            "°C", C, "C", C, "degC", C,
            "K", K,
            "mg", MG, "g", G,
            "mg/s", MG_PER_S, "mW", MW, "W", W,
            "K/min", K_PER_MIN, "°C/min", C_PER_MIN,
            "J/g", J_PER_G, "mJ", MJ, "J", J);

    public static java.util.Optional<Unit> fromSymbol(String symbol) {
        if (symbol == null) return java.util.Optional.empty();
        return java.util.Optional.ofNullable(BY_SYMBOL.get(symbol.trim()));
    }

    public boolean convertibleTo(Unit target) {
        return this.kind == target.kind;
    }

    public OptionalDouble convert(double value, Unit target) {
        if (this.kind != target.kind) return OptionalDouble.empty();
        return OptionalDouble.of(target.fromBase(this.toBase(value)));
    }
}
