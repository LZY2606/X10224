package org.dscreview.num;

import java.util.Map;
import org.dscreview.model.Quant;

/**
 * Temperature / mass / heat-flow unit conversion. Inputs keep their original
 * representation; values that cannot be converted stay in place with a diagnostic.
 */
public final class Units {
    private Units() {}

    public static final String BASIS_DEGC = "coord:temperature[degC]";
    public static final String BASIS_TIME_S = "coord:time[s]";
    public static final String BASIS_HF_MW = "derived:heatflow[mW]";
    public static final String BASIS_MASS_MG = "source:mass[mg]";
    public static final String BASIS_DH_JG = "derived:delta-h[J/g]";

    private static final Map<String, Double> TEMP_FACTOR = Map.of(
            "degC", 1.0, "C", 1.0, "celsius", 1.0,
            "K", 1.0, "kelvin", 1.0,
            "degF", 1.0, "F", 1.0, "fahrenheit", 1.0);

    /** Convert a temperature quantity to degrees Celsius. NaN + diagnostic-bearing raw on failure. */
    public static double toDegC(Quant q) {
        if (q == null || !q.convertible()) return Double.NaN;
        Double f = TEMP_FACTOR.get(q.unit());
        if (f == null) return Double.NaN;
        double v = q.value();
        if (q.unit().equalsIgnoreCase("K") || q.unit().equalsIgnoreCase("kelvin")) return v - 273.15;
        if (q.unit().equalsIgnoreCase("degF") || q.unit().equalsIgnoreCase("F")
                || q.unit().equalsIgnoreCase("fahrenheit")) return (v - 32.0) * 5.0 / 9.0;
        return v;
    }

    public static double toMg(Quant q) {
        if (q == null || !q.convertible()) return Double.NaN;
        return switch (q.unit()) {
            case "mg" -> q.value();
            case "g" -> q.value() * 1000.0;
            case "ug" -> q.value() / 1000.0;
            case "kg" -> q.value() * 1_000_000.0;
            default -> Double.NaN;
        };
    }

    /** Normalize heat flow to mW; supports common instrument units. */
    public static double toMilliwatt(Quant q) {
        if (q == null || !q.convertible()) return Double.NaN;
        return switch (q.unit()) {
            case "mW" -> q.value();
            case "W" -> q.value() * 1000.0;
            case "uW" -> q.value() / 1000.0;
            case "mW/g" -> q.value(); // mass-specific: kept as-is, normalization by mass is separate
            case "W/g" -> q.value() * 1000.0;
            default -> Double.NaN;
        };
    }

    /** J/g from mJ and mg: delta_h[J/g] = area[mJ]/mass[mg]. */
    public static double joulesPerGram(double enthalpyMj, double massMg) {
        if (Double.isNaN(massMg) || massMg == 0.0 || Double.isNaN(enthalpyMj)) return Double.NaN;
        return enthalpyMj / massMg;
    }

    public static double toKPerMin(Quant q) {
        if (q == null || !q.convertible()) return Double.NaN;
        return switch (q.unit()) {
            case "K/min", "degC/min" -> q.value();
            case "K/s" -> q.value() * 60.0;
            default -> Double.NaN;
        };
    }
}
