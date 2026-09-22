package dsc.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Units and coordinate bases for DSC domain quantities. Values are stored in
 * their original representation at import time; only the derived layer converts
 * them to canonical SI-like units, and conversions that cannot be performed
 * leave the value in place with a diagnostic instead of being dropped.
 */
public final class Units {

    public static final String C = "°C";
    public static final String K = "K";
    public static final String S = "s";
    public static final String MIN = "min";
    public static final String MW = "mW";
    public static final String W = "W";
    public static final String J_PER_G = "J/g";
    public static final String KJ_PER_G = "kJ/g";
    public static final String MG = "mg";
    public static final String G = "g";
    public static final String K_PER_MIN = "K/min";

    private static final Map<String, Double> MASS_TO_G = new LinkedHashMap<>();
    private static final Map<String, Double> POWER_TO_MW = new LinkedHashMap<>();
    private static final Map<String, Double> ENERGY_PER_MASS_TO_JG = new LinkedHashMap<>();
    private static final Map<String, Double> TIME_TO_S = new LinkedHashMap<>();

    static {
        MASS_TO_G.put(MG, 1.0 / 1000.0);
        MASS_TO_G.put(G, 1.0);
        POWER_TO_MW.put(MW, 1.0);
        POWER_TO_MW.put(W, 1000.0);
        ENERGY_PER_MASS_TO_JG.put(J_PER_G, 1.0);
        ENERGY_PER_MASS_TO_JG.put(KJ_PER_G, 1000.0);
        TIME_TO_S.put(S, 1.0);
        TIME_TO_S.put(MIN, 60.0);
    }

    private Units() {
    }

    /** Offsets for temperature are nonlinear so they are handled explicitly. */
    public static double temperatureToC(double value, String unit) {
        if (C.equals(unit)) {
            return value;
        }
        if (K.equals(unit)) {
            return value - 273.15;
        }
        throw new InconvertibleUnitException("temperature", unit, C);
    }

    public static double massToG(double value, String unit) {
        Double f = MASS_TO_G.get(unit);
        if (f == null) {
            throw new InconvertibleUnitException("mass", unit, G);
        }
        return value * f;
    }

    public static double powerToMw(double value, String unit) {
        Double f = POWER_TO_MW.get(unit);
        if (f == null) {
            throw new InconvertibleUnitException("heat-flow", unit, MW);
        }
        return value * f;
    }

    public static double energyPerMassToJg(double value, String unit) {
        Double f = ENERGY_PER_MASS_TO_JG.get(unit);
        if (f == null) {
            throw new InconvertibleUnitException("specific-enthalpy", unit, J_PER_G);
        }
        return value * f;
    }

    public static double timeToS(double value, String unit) {
        Double f = TIME_TO_S.get(unit);
        if (f == null) {
            throw new InconvertibleUnitException("time", unit, S);
        }
        return value * f;
    }

    public static boolean convertible(String unit) {
        return C.equals(unit) || K.equals(unit) || MASS_TO_G.containsKey(unit)
                || POWER_TO_MW.containsKey(unit) || ENERGY_PER_MASS_TO_JG.containsKey(unit)
                || TIME_TO_S.containsKey(unit) || K_PER_MIN.equals(unit);
    }

    public static final class InconvertibleUnitException extends RuntimeException {
        public InconvertibleUnitException(String quantity, String from, String to) {
            super("cannot convert " + quantity + " from '" + from + "' to '" + to + "'");
        }
    }
}
