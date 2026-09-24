package dsc;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unit registry. Every domain quantity carries its unit and coordinate basis.
 * Raw representations are preserved at import; conversion to SI happens only
 * in the derived layer. Unconvertible units stay in place and produce a
 * diagnostic instead of being silently dropped.
 */
public final class Units {

    public record Conv(double factor, double offset, String dimension, String siUnit) {}

    private static final Map<String, Conv> TABLE = new LinkedHashMap<>();

    static {
        // temperature (offset applied before factor: si = (v + offset) * factor)
        reg("K", 1.0, 0.0, "TEMP", "K");
        reg("°C", 1.0, 273.15, "TEMP", "K");
        reg("degC", 1.0, 273.15, "TEMP", "K");
        reg("C", 1.0, 273.15, "TEMP", "K");
        // heat flow
        reg("W", 1.0, 0.0, "POWER", "W");
        reg("mW", 1e-3, 0.0, "POWER", "W");
        // mass
        reg("g", 1.0, 0.0, "MASS", "g");
        reg("mg", 1e-3, 0.0, "MASS", "g");
        reg("kg", 1e3, 0.0, "MASS", "g");
        // scan rate
        reg("K/min", 1.0, 0.0, "RATE", "K/min");
        reg("°C/min", 1.0, 0.0, "RATE", "K/min");
        reg("K/s", 60.0, 0.0, "RATE", "K/min");
        // energy per mass
        reg("J/g", 1.0, 0.0, "ENERGY_PER_MASS", "J/g");
        reg("mJ/mg", 1.0, 0.0, "ENERGY_PER_MASS", "J/g");
        reg("kJ/g", 1e3, 0.0, "ENERGY_PER_MASS", "J/g");
        // gas flow
        reg("mL/min", 1.0, 0.0, "FLOW", "mL/min");
        reg("sccm", 1.0, 0.0, "FLOW", "mL/min");
    }

    private static void reg(String unit, double factor, double offset, String dim, String si) {
        TABLE.put(unit, new Conv(factor, offset, dim, si));
    }

    private Units() {}

    public static boolean known(String unit) {
        return unit != null && TABLE.containsKey(unit.trim());
    }

    public static String dimensionOf(String unit) {
        Conv c = TABLE.get(unit == null ? "" : unit.trim());
        return c == null ? null : c.dimension();
    }

    /** Convert to SI. Throws IllegalArgumentException for unknown units. */
    public static double toSi(double value, String unit) {
        Conv c = TABLE.get(unit == null ? "" : unit.trim());
        if (c == null) throw new IllegalArgumentException("unconvertible unit: " + unit);
        return (value + c.offset()) * c.factor();
    }

    public static String siUnitOf(String unit) {
        Conv c = TABLE.get(unit == null ? "" : unit.trim());
        return c == null ? null : c.siUnit();
    }
}
