package dsc.model;

import java.util.Map;
import java.util.Optional;

/**
 * Unit conversion registry. Raw imports keep original units; only the derived
 * layer normalizes. Unconvertible values stay in place with a diagnostic.
 */
public final class Units {
    private Units() {}

    private static final Map<String, Double> MASS_TO_MG = Map.of(
            "mg", 1.0,
            "g", 1000.0,
            "ug", 0.001);

    public static Optional<Double> massToMg(double value, String unit) {
        Double f = MASS_TO_MG.get(unit.trim().toLowerCase());
        return f == null ? Optional.empty() : Optional.of(value * f);
    }

    public static Optional<Double> tempToC(double value, String unit) {
        return switch (unit.trim().toUpperCase()) {
            case "C", "DEGC", "°C" -> Optional.of(value);
            case "K" -> Optional.of(value - 273.15);
            default -> Optional.empty();
        };
    }

    public static Optional<Double> rateToCPerMin(double value, String unit) {
        return switch (unit.trim().toLowerCase()) {
            case "c/min", "k/min", "degc/min" -> Optional.of(value);
            case "c/s", "k/s" -> Optional.of(value * 60.0);
            default -> Optional.empty();
        };
    }
}
