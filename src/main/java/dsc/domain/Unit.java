package dsc.domain;

/**
 * A unit or coordinate basis carried by every domain quantity.
 * Raw import values keep their original unit; the derived layer normalizes to the
 * canonical bases (degC, W/g, s, g, K/min) via {@link #convert}.
 */
public final class Unit {

    public static final String CELSIUS = "degC";
    public static final String KELVIN = "K";
    public static final String FAHRENHEIT = "degF";
    public static final String W_PER_G = "W/g";
    public static final String MW_PER_G = "mW/g";
    public static final String MW = "mW";
    public static final String W = "W";
    public static final String SECOND = "s";
    public static final String MINUTE = "min";
    public static final String GRAM = "g";
    public static final String MILLIGRAM = "mg";
    public static final String K_PER_MIN = "K/min";
    public static final String C_PER_MIN = "degC/min";
    public static final String J_PER_G = "J/g";
    public static final String KJ_PER_G = "kJ/g";

    private Unit() {
    }

    public static boolean isTemperature(String u) {
        return CELSIUS.equals(u) || KELVIN.equals(u) || FAHRENHEIT.equals(u);
    }

    public static boolean isHeatFlow(String u) {
        return W_PER_G.equals(u) || MW_PER_G.equals(u) || MW.equals(u) || W.equals(u);
    }

    public static boolean isTime(String u) {
        return SECOND.equals(u) || MINUTE.equals(u);
    }

    public static boolean isMass(String u) {
        return GRAM.equals(u) || MILLIGRAM.equals(u);
    }

    public static boolean isRate(String u) {
        return K_PER_MIN.equals(u) || C_PER_MIN.equals(u);
    }

    public static boolean isEnthalpy(String u) {
        return J_PER_G.equals(u) || KJ_PER_G.equals(u);
    }

    /** Convert a value expressed in {@code from} to the canonical unit of its kind. */
    public static double toCanonical(double v, String from) {
        switch (from) {
            case KELVIN:
                return v - 273.15;
            case FAHRENHEIT:
                return (v - 32.0) * 5.0 / 9.0;
            case CELSIUS:
                return v;
            case MW_PER_G:
                return v / 1000.0;
            case W_PER_G:
                return v;
            case MW:
                return v / 1000.0;
            case W:
                return v;
            case MINUTE:
                return v * 60.0;
            case SECOND:
                return v;
            case MILLIGRAM:
                return v / 1000.0;
            case GRAM:
                return v;
            case C_PER_MIN:
            case K_PER_MIN:
                return v;
            case KJ_PER_G:
                return v * 1000.0;
            case J_PER_G:
                return v;
            default:
                throw new IllegalArgumentException("unknown or non-convertible unit: " + from);
        }
    }

    public static String canonicalFor(String from) {
        if (isTemperature(from)) {
            return CELSIUS;
        }
        if (W_PER_G.equals(from) || MW_PER_G.equals(from)) {
            return W_PER_G;
        }
        if (MW.equals(from) || W.equals(from)) {
            return W;
        }
        if (isTime(from)) {
            return SECOND;
        }
        if (isMass(from)) {
            return GRAM;
        }
        if (isRate(from)) {
            return K_PER_MIN;
        }
        if (isEnthalpy(from)) {
            return J_PER_G;
        }
        throw new IllegalArgumentException("no canonical unit for: " + from);
    }
}
