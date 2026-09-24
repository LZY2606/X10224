package dsc.domain;

import java.util.Map;

/** Linear unit conversion factors grouped by quantity kind. */
public final class UnitTables {
    private UnitTables() {}

    private static final Map<String, Map<String, Double>> TO_CANON = Map.of(
        "K", Map.of("K", 1.0, "C", 1.0, "degC", 1.0, "°C", 1.0),
        "mg", Map.of("mg", 1.0, "g", 1000.0, "kg", 1_000_000.0),
        "K/min", Map.of("K/min", 1.0, "C/min", 1.0, "K/s", 60.0, "C/s", 60.0),
        "mW", Map.of("mW", 1.0, "W", 1000.0, "uW", 0.001, "µW", 0.001),
        "J/g", Map.of("J/g", 1.0, "kJ/kg", 1.0, "J/kg", 0.001)
    );

    /** Returns multiplication factor to go from one unit to another, or null. */
    public static Double factor(String from, String to) {
        for (Map<String, Double> group : TO_CANON.values()) {
            Double f = group.get(from);
            Double t = group.get(to);
            if (f != null && t != null) return f / t;
        }
        return null;
    }

    /** Temperature kind allows offset conversion C/K; points use canonical K values with offset 273.15 handled by caller. */
    public static boolean isTemperature(String unit) {
        return "K".equals(unit) || "C".equals(unit) || "degC".equals(unit) || "°C".equals(unit);
    }
}
