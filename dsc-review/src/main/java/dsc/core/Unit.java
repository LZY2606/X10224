package dsc.core;

import java.util.Locale;
import java.util.Objects;

/**
 * A physical unit or coordinate basis carried alongside every domain quantity.
 * Units are never inferred from a file name; they come from the archived raw header.
 */
public final class Unit {

    public enum Kind { TEMPERATURE, HEAT_FLOW, MASS, RATE, ENERGY_PER_MASS, TIME, DIMENSIONLESS }

    private final String symbol;
    private final Kind kind;

    private Unit(String symbol, Kind kind) {
        this.symbol = symbol;
        this.kind = kind;
    }

    public static Unit of(String symbol, Kind kind) {
        return new Unit(symbol.trim(), kind);
    }

    public String symbol() { return symbol; }
    public Kind kind() { return kind; }

    /** Linear conversion factor to the canonical basis of the same kind; null when unsupported. */
    public Double toCanonicalFactor() {
        switch (kind) {
            case TEMPERATURE:
                switch (symbol) {
                    case "C": case "\u00b0C": case "degC": return 1.0; // canonical is Celsius for DSC
                    case "K": return 1.0;                   // interval scale: 1 K == 1 \u00b0C
                    case "F": case "\u00b0F": return 5.0 / 9.0;
                    default: return null;
                }
            case HEAT_FLOW:
                // canonical: W/g
                if (symbol.equals("W/g")) return 1.0;
                if (symbol.equals("mW/g")) return 1e-3;
                if (symbol.equals("W/mg")) return 1e3;
                if (symbol.equals("mW/mg")) return 1.0;
                return null;
            case MASS:
                if (symbol.equals("mg")) return 1.0;
                if (symbol.equals("g")) return 1e3;
                if (symbol.equals("\u00b5g") || symbol.equals("ug")) return 1e-3;
                return null;
            case RATE:
                if (symbol.equals("K/min") || symbol.equals("C/min") || symbol.equals("\u00b0C/min")) return 1.0;
                if (symbol.equals("K/s")) return 60.0;
                return null;
            case ENERGY_PER_MASS:
                if (symbol.equals("J/g")) return 1.0;
                if (symbol.equals("mJ/g")) return 1e-3;
                if (symbol.equals("J/mg") || symbol.equals("kJ/g")) return 1e3;
                return null;
            case TIME:
                if (symbol.equals("s")) return 1.0;
                if (symbol.equals("min")) return 60.0;
                return null;
            case DIMENSIONLESS:
                return 1.0;
            default:
                return null;
        }
    }

    public static Unit canonical(Kind kind) {
        switch (kind) {
            case TEMPERATURE: return of("C", Kind.TEMPERATURE);
            case HEAT_FLOW: return of("W/g", Kind.HEAT_FLOW);
            case MASS: return of("mg", Kind.MASS);
            case RATE: return of("K/min", Kind.RATE);
            case ENERGY_PER_MASS: return of("J/g", Kind.ENERGY_PER_MASS);
            case TIME: return of("s", Kind.TIME);
            default: return of("", Kind.DIMENSIONLESS);
        }
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Unit)) return false;
        Unit u = (Unit) o;
        return symbol.equals(u.symbol) && kind == u.kind;
    }
    @Override public int hashCode() { return Objects.hash(symbol, kind); }
    @Override public String toString() { return String.format(Locale.ROOT, "%s(%s)", kind, symbol); }
}
