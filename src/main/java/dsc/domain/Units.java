package dsc.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Units and coordinate bases for every domain quantity.
 *
 * <p>Raw representations are preserved on import; only the derived layer normalises. Values that
 * cannot be converted stay at their origin with a diagnostic instead of being dropped for plotting.
 */
public final class Units {
    public static final String CELSIUS = "°C";
    public static final String KELVIN = "K";
    public static final String MG = "mg";
    public static final String G = "g";
    public static final String MW = "mW";
    public static final String W_PER_G = "W/g";
    public static final String J_PER_G = "J/g";
    public static final String K_PER_MIN = "K/min";
    public static final String ML_PER_MIN = "mL/min";

    private static final Map<String, Double> TEMPERATURE_TO_C = new LinkedHashMap<>();
    private static final Map<String, Double> MASS_TO_MG = new LinkedHashMap<>();

    static {
        TEMPERATURE_TO_C.put(CELSIUS, 1.0);
        TEMPERATURE_TO_C.put(KELVIN, 1.0);
        MASS_TO_MG.put(MG, 1.0);
        MASS_TO_MG.put(G, 1000.0);
    }

    private Units() {
    }

    public static boolean isTemperatureUnit(String unit) {
        return TEMPERATURE_TO_C.containsKey(unit);
    }

    public static boolean isMassUnit(String unit) {
        return MASS_TO_MG.containsKey(unit);
    }

    /** Converts a raw temperature value to degrees Celsius; throws for unsupported units. */
    public static double toCelsius(double value, String unit) {
        if (CELSIUS.equals(unit)) {
            return value;
        }
        if (KELVIN.equals(unit)) {
            return value - 273.15;
        }
        throw new ConversionException("unsupported temperature unit: " + unit);
    }

    /** Converts a raw mass value to milligrams; throws for unsupported units. */
    public static double toMilligrams(double value, String unit) {
        Double factor = MASS_TO_MG.get(unit);
        if (factor == null) {
            throw new ConversionException("unsupported mass unit: " + unit);
        }
        return value * factor;
    }

    public static final class ConversionException extends RuntimeException {
        public ConversionException(String message) {
            super(message);
        }
    }
}
