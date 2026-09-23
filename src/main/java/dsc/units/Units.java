package dsc.units;

import java.util.Map;

/**
 * Unit registry and conversions for the quantities that occur in a DSC
 * instrument record: temperature, mass, heat flow, scan rate and energy.
 *
 * <p>Conversions intentionally only cover units a materials lab actually
 * uses; anything else is classified {@link Quantity.Kind#UNKNOWN} so the
 * importing layer can keep the raw value and attach a diagnostic.
 */
public final class Units {

    private Units() {}

    private static final Map<String, UnitDef> DEFS = Map.ofEntries(
            Map.entry("c", new UnitDef(Quantity.Kind.TEMPERATURE, v -> v + 273.15)),
            Map.entry("°c", new UnitDef(Quantity.Kind.TEMPERATURE, v -> v + 273.15)),
            Map.entry("k", new UnitDef(Quantity.Kind.TEMPERATURE, v -> v)),
            Map.entry("f", new UnitDef(Quantity.Kind.TEMPERATURE, v -> (v - 32) * 5 / 9 + 273.15)),
            Map.entry("°f", new UnitDef(Quantity.Kind.TEMPERATURE, v -> (v - 32) * 5 / 9 + 273.15)),

            Map.entry("mg", new UnitDef(Quantity.Kind.MASS, v -> v)),
            Map.entry("g", new UnitDef(Quantity.Kind.MASS, v -> v * 1000.0)),
            Map.entry("kg", new UnitDef(Quantity.Kind.MASS, v -> v * 1_000_000.0)),

            Map.entry("mw", new UnitDef(Quantity.Kind.HEAT_FLOW, v -> v / 1000.0)),
            Map.entry("w", new UnitDef(Quantity.Kind.HEAT_FLOW, v -> v)),
            Map.entry("µw", new UnitDef(Quantity.Kind.HEAT_FLOW, v -> v / 1_000_000.0)),
            Map.entry("uw", new UnitDef(Quantity.Kind.HEAT_FLOW, v -> v / 1_000_000.0)),

            Map.entry("w/g", new UnitDef(Quantity.Kind.HEAT_FLOW_RATE, v -> v)),
            Map.entry("mw/mg", new UnitDef(Quantity.Kind.HEAT_FLOW_RATE, v -> v)),
            Map.entry("mw/g", new UnitDef(Quantity.Kind.HEAT_FLOW_RATE, v -> v / 1000.0)),

            Map.entry("k/min", new UnitDef(Quantity.Kind.RATE, v -> v)),
            Map.entry("°c/min", new UnitDef(Quantity.Kind.RATE, v -> v)),
            Map.entry("c/min", new UnitDef(Quantity.Kind.RATE, v -> v)),
            Map.entry("k/s", new UnitDef(Quantity.Kind.RATE, v -> v * 60.0)),

            Map.entry("j/g", new UnitDef(Quantity.Kind.ENERGY_PER_MASS, v -> v)),
            Map.entry("kj/kg", new UnitDef(Quantity.Kind.ENERGY_PER_MASS, v -> v)),
            Map.entry("j/kg", new UnitDef(Quantity.Kind.ENERGY_PER_MASS, v -> v / 1000.0)),
            Map.entry("cal/g", new UnitDef(Quantity.Kind.ENERGY_PER_MASS, v -> v * 4.184)),

            Map.entry("j", new UnitDef(Quantity.Kind.ENERGY, v -> v)),
            Map.entry("mj", new UnitDef(Quantity.Kind.ENERGY, v -> v / 1000.0)),

            Map.entry("-", new UnitDef(Quantity.Kind.DIMENSIONLESS, v -> v)),
            Map.entry("", new UnitDef(Quantity.Kind.DIMENSIONLESS, v -> v)),
            Map.entry("1", new UnitDef(Quantity.Kind.DIMENSIONLESS, v -> v))
    );

    private record UnitDef(Quantity.Kind kind, Convert conv) implements java.util.function.Function<Double, Double> {
        @Override public Double apply(Double v) { return conv.to(v); }
    }

    @FunctionalInterface
    private interface Convert {
        double to(double v);
    }

    public static String normalize(String unit) {
        if (unit == null) return "";
        return unit.trim().toLowerCase().replace("μ", "µ").replace("−", "-");
    }

    public static Quantity.Kind classify(String unit) {
        UnitDef d = DEFS.get(normalize(unit));
        return d == null ? Quantity.Kind.UNKNOWN : d.kind();
    }

    public static double toCanonical(double value, String unit) {
        UnitDef d = DEFS.get(normalize(unit));
        if (d == null) return Double.NaN;
        return d.apply(value);
    }

    public static boolean convertible(String unit) {
        return DEFS.containsKey(normalize(unit));
    }

    /** Convert a canonical value back into the given display unit. */
    public static double fromCanonical(double canonical, String unit) {
        Quantity.Kind targetKind = classify(unit);
        double baseFromUnit = toCanonical(1.0, unit);
        if (Double.isNaN(baseFromUnit)) return Double.NaN;
        // linear units only: canonical = f(1) * value  =>  value = canonical / f(1)
        // (temperature offsets make this false, but we never display K back as °C here)
        if (targetKind == Quantity.Kind.TEMPERATURE && normalize(unit).equals("k")) {
            return canonical;
        }
        return canonical / baseFromUnit;
    }
}
