package io.dscreview.domain;

import java.util.Map;

/**
 * Minimal, explicit unit table for DSC domain quantities. No guessing: a unit
 * that is not listed cannot be converted and the source keeps its raw value
 * plus a diagnostic.
 *
 * Sign convention carried by heat flow units: an explicit direction suffix
 * ({@code endo+}/{@code endo-}) records the instrument's endotherm direction
 * so it never has to be inferred from a file name.
 */
public final class Units {
    private Units() {}

    private static final Map<String, Double> TEMPERATURE_FACTOR = Map.of(
            "°C", 1.0, "C", 1.0, "degC", 1.0,
            "K", 1.0, "mK", 0.001);
    private static final Map<String, Double> MASS_FACTOR = Map.of(
            "mg", 1.0, "g", 1000.0, "kg", 1_000_000.0, "ug", 0.001, "µg", 0.001);
    private static final Map<String, Double> RATE_FACTOR = Map.of(
            "K/min", 1.0, "°C/min", 1.0, "C/min", 1.0,
            "K/s", 60.0, "K/h", 1.0 / 60.0);
    private static final Map<String, Double> HEATFLOW_FACTOR = Map.of(
            "W", 1.0, "mW", 0.001, "uW", 0.000001, "µW", 0.000001);
    private static final Map<String, Double> SPECIFIC_HEATFLOW_FACTOR = Map.of(
            "W/g", 1.0, "mW/mg", 1.0, "W/kg", 0.001, "J/(s·g)", 1.0);
    private static final Map<String, Double> ENERGY_FACTOR = Map.of(
            "J", 1.0, "mJ", 0.001, "kJ", 1000.0);
    private static final Map<String, Double> SPECIFIC_ENERGY_FACTOR = Map.of(
            "J/g", 1.0, "mJ/mg", 1.0, "kJ/kg", 1.0, "J/kg", 0.001);

    public static Double convert(double value, String fromUnit, String toUnit) {
        if (fromUnit.equals(toUnit)) return value;
        Double base = factor(fromUnit);
        Double target = factor(toUnit);
        if (base == null || target == null) return null;
        return value * base / target;
    }

    private static Double factor(String u) {
        String key = stripDirection(u);
        Map<String, Double> factor = factorMap(u);
        return factor == null ? null : factor.get(key);
    }

    private static Map<String, Double> factorMap(String u) {
        String family = family(u);
        return switch (family) {
            case "temperature" -> TEMPERATURE_FACTOR;
            case "mass" -> MASS_FACTOR;
            case "rate" -> RATE_FACTOR;
            case "heatflow" -> HEATFLOW_FACTOR;
            case "specific_heatflow" -> SPECIFIC_HEATFLOW_FACTOR;
            case "energy" -> ENERGY_FACTOR;
            case "specific_energy" -> SPECIFIC_ENERGY_FACTOR;
            default -> null;
        };
    }

    public static String family(String unit) {
        String u = stripDirection(unit);
        if (TEMPERATURE_FACTOR.containsKey(u)) return "temperature";
        if (MASS_FACTOR.containsKey(u)) return "mass";
        if (RATE_FACTOR.containsKey(u)) return "rate";
        if (SPECIFIC_HEATFLOW_FACTOR.containsKey(u)) return "specific_heatflow";
        if (HEATFLOW_FACTOR.containsKey(u)) return "heatflow";
        if (SPECIFIC_ENERGY_FACTOR.containsKey(u)) return "specific_energy";
        if (ENERGY_FACTOR.containsKey(u)) return "energy";
        return "unknown";
    }

    public static boolean convertible(String fromUnit, String toUnit) {
        return family(fromUnit).equals(family(toUnit)) && !"unknown".equals(family(fromUnit));
    }

    /** Removes the endotherm-direction suffix, e.g. "mW;endo+". */
    public static String stripDirection(String unit) {
        int idx = unit.indexOf(';');
        return idx >= 0 ? unit.substring(0, idx) : unit;
    }

    /**
     * Signed endotherm flag of a heat-flow unit tag.
     * @return +1 if positive readings mean endothermic, -1 if positive means
     *         exothermic, null if not declared
     */
    public static Integer endoPositive(String unit) {
        int idx = unit.indexOf("endo");
        if (idx < 0) return null;
        String tail = unit.substring(idx + 4);
        if (tail.startsWith("+") || tail.startsWith("up")) return 1;
        if (tail.startsWith("-") || tail.startsWith("down")) return -1;
        return null;
    }
}
