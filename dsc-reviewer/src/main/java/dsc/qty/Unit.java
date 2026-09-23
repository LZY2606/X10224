package dsc.qty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Dimension-aware unit registry. Every domain value carries a unit; conversions
 * are only allowed inside one dimension. Unconvertible input is never silently
 * coerced into a plottable number.
 */
public final class Unit {

    public record Def(String name, String dimension, double factorToSi, double offset) {}

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();

    static {
        def("K",     "temperature", 1.0, 0.0);
        def("degC",  "temperature", 1.0, 273.15);
        def("degF",  "temperature", 5.0 / 9.0, 459.67 * 5.0 / 9.0);
        def("s",     "time", 1.0, 0.0);
        def("min",   "time", 60.0, 0.0);
        def("mg",    "mass", 1e-6, 0.0);
        def("g",     "mass", 1e-3, 0.0);
        def("kg",    "mass", 1.0, 0.0);
        def("K/min", "rate", 1.0 / 60.0, 0.0);
        def("K/s",   "rate", 1.0, 0.0);
        def("degC/min", "rate", 1.0 / 60.0, 0.0);
        def("mW",    "heatflow", 1e-3, 0.0);
        def("W",     "heatflow", 1.0, 0.0);
        def("mW/mg", "specificheatflow", 1.0, 0.0);
        def("W/kg",  "specificheatflow", 1.0, 0.0);
        def("J/g",   "specificenergy", 1000.0, 0.0);
        def("J/kg",  "specificenergy", 1.0, 0.0);
        def("mJ",    "energy", 1e-3, 0.0);
        def("J",     "energy", 1.0, 0.0);
        def("",      "dimensionless", 1.0, 0.0);
    }

    private static void def(String name, String dim, double f, double off) {
        DEFS.put(name, new Def(name, dim, f, off));
    }

    private Unit() {}

    public static Optional<Def> lookup(String name) {
        return Optional.ofNullable(DEFS.get(name));
    }

    public static boolean known(String name) {
        return DEFS.containsKey(name);
    }

    /** Convert a numeric reading expressed in {@code from} into {@code to}. Throws on cross-dimension use. */
    public static double convert(double v, String from, String to) {
        Def a = require(from);
        Def b = require(to);
        if (!a.dimension().equals(b.dimension())) {
            throw new IllegalArgumentException(
                    "cannot convert " + from + " (" + a.dimension() + ") to " + to + " (" + b.dimension() + ")");
        }
        double si = (v + a.offset()) * a.factorToSi();
        return si / b.factorToSi() - b.offset();
    }

    /** True only when both units are known and share a dimension. */
    public static boolean compatible(String from, String to) {
        Def a = DEFS.get(from);
        Def b = DEFS.get(to);
        return a != null && b != null && a.dimension().equals(b.dimension());
    }

    public static String dimension(String unit) {
        return require(unit).dimension();
    }

    private static Def require(String name) {
        Def d = DEFS.get(name);
        if (d == null) {
            throw new IllegalArgumentException("unknown unit: " + name);
        }
        return d;
    }
}
