package dsc.model;

import java.util.Locale;
import java.util.Map;

/**
 * Canonical internal coordinate bases and the only place where source units are converted.
 * Temperature is normalised to degrees Celsius (instrument calibration frame), heat flow to W/g
 * (sample-mass-normalised, sign preserving), mass to milligram, scan rate to K/min.
 */
public final class Units {
    public static final String T_C = "degC";
    public static final String T_K = "K";
    public static final String HF_WG = "W/g";
    public static final String HF_MWG = "mW/g";
    public static final String HF_MW = "mW";
    public static final String MASS_MG = "mg";
    public static final String RATE_KMIN = "K/min";
    public static final String ENTHALPY_JG = "J/g";

    private static final Map<String, Double> TEMP_FROM = Map.of(
            T_C, 1.0, "c", 1.0, "°c", 1.0, "celsius", 1.0,
            T_K, 1.0, "kelvin", 1.0);
    private static final Map<String, Double> HF_TO_WG = Map.of(
            HF_WG, 1.0, "w/g", 1.0,
            HF_MWG, 0.001, "mw/g", 0.001,
            HF_MW, Double.NaN); // needs sample mass, handled by caller
    private static final Map<String, Double> MASS_TO_MG = Map.of(
            MASS_MG, 1.0, "g", 1000.0, "kg", 1_000_000.0);
    private static final Map<String, Double> RATE_TO_KMIN = Map.of(
            RATE_KMIN, 1.0, "k/s", 60.0, "c/min", 1.0);

    private Units() {
    }

    /** Convert a raw temperature value to the Celsius frame; returns null when the unit is unknown. */
    public static Double convertTemperature(double value, String rawUnit, boolean kelvinZero) {
        if (rawUnit == null) return null;
        Double factor = TEMP_FROM.get(rawUnit.toLowerCase(Locale.ROOT));
        if (factor == null) return null;
        if (T_K.equalsIgnoreCase(rawUnit) || "kelvin".equalsIgnoreCase(rawUnit) || kelvinZero) {
            return value - 273.15;
        }
        return value;
    }

    public static boolean isKnownHeatFlowUnit(String unit) {
        if (unit == null) return false;
        return HF_TO_WG.containsKey(unit.toLowerCase(Locale.ROOT));
    }

    /** Convert heat flow value into W/g. mW raw rows are normalised with the supplied mass in mg. */
    public static Double convertHeatFlow(double value, String rawUnit, Double massMg) {
        if (rawUnit == null) return null;
        String key = rawUnit.toLowerCase(Locale.ROOT);
        Double factor = HF_TO_WG.get(key);
        if (factor == null) return null;
        if (Double.isNaN(factor)) {
            if (massMg == null || massMg <= 0) return null;
            return value / massMg; // 1 mW / 1 mg = 1 W/g
        }
        return value * factor;
    }

    public static Double convertMass(double value, String rawUnit) {
        if (rawUnit == null) return null;
        Double factor = MASS_TO_MG.get(rawUnit.toLowerCase(Locale.ROOT));
        return factor == null ? null : value * factor;
    }

    public static Double convertRate(double value, String rawUnit) {
        if (rawUnit == null) return null;
        Double factor = RATE_TO_KMIN.get(rawUnit.toLowerCase(Locale.ROOT));
        return factor == null ? null : value * factor;
    }
}
