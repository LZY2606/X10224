package dsc.domain;

import java.util.Map;

/**
 * Explicit, finite unit table. Anything not listed is retained in place and
 * flagged as a diagnostic rather than silently dropped for plotting.
 */
public final class Units {
    private Units() {}

    private static final Map<String, Map<String, Double>> FACTORS = Map.of(
            "mg", Map.of("g", 0.001, "mg", 1.0),
            "g", Map.of("g", 1.0, "mg", 1000.0),
            "mW", Map.of("mW", 1.0, "W", 0.001, "W/g", Double.NaN),
            "mW/g", Map.of("mW/g", 1.0, "W/g", 0.001),
            "W/g", Map.of("W/g", 1.0, "mW/g", 1000.0),
            "J/g", Map.of("J/g", 1.0),
            "K", Map.of("K", 1.0, "C", 1.0, "\u00b0C", 1.0),
            "C", Map.of("C", 1.0, "K", 1.0),
            "\u00b0C", Map.of("\u00b0C", 1.0, "K", 1.0, "C", 1.0),
            "K/min", Map.of("K/min", 1.0, "C/min", 1.0, "\u00b0C/min", 1.0),
            "C/min", Map.of("C/min", 1.0, "K/min", 1.0),
            "\u00b0C/min", Map.of("\u00b0C/min", 1.0, "K/min", 1.0),
            "min", Map.of("min", 1.0, "s", 60.0),
            "s", Map.of("s", 1.0, "min", 1.0 / 60.0)
    );

    public static Double factor(String from, String to) {
        Map<String, Double> row = FACTORS.get(from);
        return row == null ? null : row.get(to);
    }
}
