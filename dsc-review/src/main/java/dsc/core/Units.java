package dsc.core;

import java.util.Locale;
import java.util.Objects;

/**
 * Physical unit metadata. Units are intentionally a small closed vocabulary:
 * DSC data in this application only ever needs temperature, heat-flow and mass.
 * Every domain value carries its unit so that a number is never interpreted
 * without its coordinate/reference basis.
 */
public final class Units {

    public enum Dimension { TEMPERATURE, HEAT_FLOW, SPECIFIC_HEAT_FLOW, MASS, RATE, ENERGY, SPECIFIC_ENERGY, DIMENSIONLESS }

    public static final Unit C = new Unit("\u00b0C", "degree Celsius", Dimension.TEMPERATURE, false);
    public static final Unit K = new Unit("K", "kelvin", Dimension.TEMPERATURE, true);
    public static final Unit MW = new Unit("mW", "milliwatt", Dimension.HEAT_FLOW, false);
    public static final Unit W = new Unit("W", "watt", Dimension.HEAT_FLOW, false);
    public static final Unit J_PER_G = new Unit("J/g", "joule per gram", Dimension.SPECIFIC_ENERGY, false);
    public static final Unit J_PER_G_K = new Unit("J/(g\u00b7K)", "joule per gram kelvin", Dimension.SPECIFIC_HEAT_FLOW, false);
    public static final Unit MG = new Unit("mg", "milligram", Dimension.MASS, false);
    public static final Unit G = new Unit("g", "gram", Dimension.MASS, false);
    public static final Unit C_PER_MIN = new Unit("\u00b0C/min", "celsius per minute", Dimension.RATE, false);
    public static final Unit K_PER_MIN = new Unit("K/min", "kelvin per minute", Dimension.RATE, false);
    public static final Unit J = new Unit("J", "joule", Dimension.ENERGY, false);
    public static final Unit MJ = new Unit("mJ", "millijoule", Dimension.ENERGY, false);
    public static final Unit ONE = new Unit("-", "dimensionless", Dimension.DIMENSIONLESS, false);

    private Units() {}

    public static Unit bySymbol(String symbol) {
        String s = symbol == null ? "" : symbol.trim();
        for (Unit u : new Unit[]{C, K, MW, W, J_PER_G, J_PER_G_K, MG, G, C_PER_MIN, K_PER_MIN, J, MJ, ONE}) {
            if (u.symbol().equals(s)) return u;
        }
        throw new IllegalArgumentException("unknown unit symbol: " + symbol);
    }

    /** Convert {@code value} expressed in {@code from} to unit {@code to}. */
    public static double convert(double value, Unit from, Unit to) {
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
        if (from.dimension() != to.dimension()) {
            throw new IllegalArgumentException(
                    String.format("cannot convert %s (%s) to %s (%s)",
                            from.symbol(), from.dimension(), to.symbol(), to.dimension()));
        }
        if (from.equals(to)) return value;
        switch (from.dimension()) {
            case TEMPERATURE:
                double celsius = from == K ? value - 273.15 : value;
                return to == K ? celsius + 273.15 : celsius;
            case MASS: {
                double grams = from == MG ? value / 1000.0 : value;
                return to == MG ? grams * 1000.0 : grams;
            }
            case HEAT_FLOW: {
                double mw = from == W ? value * 1000.0 : value;
                return to == W ? mw / 1000.0 : mw;
            }
            case ENERGY: {
                double j = from == MJ ? value / 1000.0 : value;
                return to == MJ ? j * 1000.0 : j;
            }
            case RATE:
                return value; // K/min and °C/min have identical step size
            default:
                throw new IllegalArgumentException(
                        "no conversion path between " + from.symbol() + " and " + to.symbol());
        }
    }

    public static String fmt(double v) {
        if (Double.isNaN(v)) return "NaN";
        if (Double.isInfinite(v)) return v > 0 ? "Inf" : "-Inf";
        return String.format(Locale.ROOT, "%.6g", v);
    }
}
