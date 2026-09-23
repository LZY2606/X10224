package dsc.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unit registry. Raw values keep their original representation in {@link Quantity};
 * conversion happens only in the derived layer. Unknown units are reported, never
 * silently coerced or dropped.
 */
public final class Units {

    public record Dim(String name) {
        @Override public String toString() { return name; }
    }

    public static final Dim TEMPERATURE = new Dim("TEMPERATURE");
    public static final Dim MASS = new Dim("MASS");
    public static final Dim POWER = new Dim("POWER");
    public static final Dim TIME = new Dim("TIME");
    public static final Dim RATE = new Dim("RATE");
    public static final Dim SPECIFIC_ENERGY = new Dim("SPECIFIC_ENERGY");
    public static final Dim FLOW = new Dim("FLOW");

    /** value(base) = value * factor + offset */
    public record Spec(Dim dim, double factor, double offset, String baseUnit) {
        double toBase(double v) { return v * factor + offset; }
        double fromBase(double v) { return (v - offset) / factor; }
    }

    private static final Map<String, Spec> REGISTRY = new LinkedHashMap<>();

    private static void u(String alias, Dim dim, double factor, double offset, String base) {
        REGISTRY.put(alias, new Spec(dim, factor, offset, base));
    }

    static {
        u("K", TEMPERATURE, 1, 0, "K");
        u("C", TEMPERATURE, 1, 273.15, "K");
        u("degC", TEMPERATURE, 1, 273.15, "K");
        u("°C", TEMPERATURE, 1, 273.15, "K");
        u("F", TEMPERATURE, 5.0 / 9.0, 459.67 * 5.0 / 9.0, "K");

        u("g", MASS, 1, 0, "g");
        u("mg", MASS, 0.001, 0, "g");
        u("kg", MASS, 1000, 0, "g");

        u("W", POWER, 1, 0, "W");
        u("mW", POWER, 0.001, 0, "W");
        u("uW", POWER, 1e-6, 0, "W");
        u("µW", POWER, 1e-6, 0, "W");

        u("s", TIME, 1, 0, "s");
        u("ms", TIME, 0.001, 0, "s");
        u("min", TIME, 60, 0, "s");

        u("K/s", RATE, 1, 0, "K/s");
        u("K/min", RATE, 1.0 / 60.0, 0, "K/s");
        u("C/min", RATE, 1.0 / 60.0, 0, "K/s");
        u("°C/min", RATE, 1.0 / 60.0, 0, "K/s");
        u("degC/min", RATE, 1.0 / 60.0, 0, "K/s");

        u("J/g", SPECIFIC_ENERGY, 1, 0, "J/g");
        u("J/kg", SPECIFIC_ENERGY, 0.001, 0, "J/g");
        u("kJ/kg", SPECIFIC_ENERGY, 1, 0, "J/g");
        u("mJ/mg", SPECIFIC_ENERGY, 1, 0, "J/g");

        u("mL/min", FLOW, 1, 0, "mL/min");
        u("mL/s", FLOW, 60, 0, "mL/min");
        u("L/min", FLOW, 1000, 0, "mL/min");
    }

    private Units() {}

    public static Spec spec(String unit) {
        Spec s = REGISTRY.get(unit);
        if (s == null) {
            throw new UnconvertibleValueException("未知单位: " + unit, unit);
        }
        return s;
    }

    public static boolean known(String unit) {
        return REGISTRY.containsKey(unit);
    }

    public static Dim dimension(String unit) {
        return spec(unit).dim();
    }

    public static String baseUnit(String unit) {
        return spec(unit).baseUnit();
    }

    public static double toBase(Quantity q) {
        Spec s = spec(q.unit());
        return s.toBase(q.value());
    }

    public static double convert(Quantity q, String targetUnit) {
        Spec from = spec(q.unit());
        Spec to = spec(targetUnit);
        if (!from.dim().equals(to.dim())) {
            throw new UnconvertibleValueException(
                    "量纲不一致，无法把 " + q.unit() + " 转换为 " + targetUnit, q.unit());
        }
        return to.fromBase(from.toBase(q.value()));
    }
}
