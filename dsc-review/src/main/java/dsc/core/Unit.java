package dsc.core;

import java.util.Objects;

/** A symbol + dimension + reference basis. Immutable value object. */
public final class Unit {
    private final String symbol;
    private final String name;
    private final Units.Dimension dimension;
    private final boolean absolute;

    public Unit(String symbol, String name, Units.Dimension dimension, boolean absolute) {
        this.symbol = symbol;
        this.name = name;
        this.dimension = dimension;
        this.absolute = absolute;
    }

    public String symbol() { return symbol; }
    public String name() { return name; }
    public Units.Dimension dimension() { return dimension; }
    /** Absolute reference (kelvin) vs. relative offset (celsius). */
    public boolean absolute() { return absolute; }

    @Override public boolean equals(Object o) {
        return o instanceof Unit u && u.symbol.equals(symbol) && u.dimension == dimension;
    }
    @Override public int hashCode() { return Objects.hash(symbol, dimension); }
    @Override public String toString() { return symbol; }
}
