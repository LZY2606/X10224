package dsc.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单位目录与换算。内部规范单位：温度 K、质量 g、热流 W、速率 K/min、
 * 焓 J/g、热容 J/(g*K)。未登记的单位不做猜测转换。
 */
public final class Units {

    public static final String KELVIN = "K";
    public static final String CELSIUS = "°C";
    public static final String GRAM = "g";
    public static final String MILLIGRAM = "mg";
    public static final String WATT = "W";
    public static final String MILLIWATT = "mW";
    public static final String K_PER_MIN = "K/min";
    public static final String JOULE_PER_GRAM = "J/g";
    public static final String J_PER_GRAM_KELVIN = "J/(g*K)";

    private static final Map<String, Double> TO_CANON = new LinkedHashMap<>();

    static {
        TO_CANON.put(MILLIGRAM, 0.001);
        TO_CANON.put(GRAM, 1.0);
        TO_CANON.put(MILLIWATT, 0.001);
        TO_CANON.put(WATT, 1.0);
        TO_CANON.put(JOULE_PER_GRAM, 1.0);
        TO_CANON.put(J_PER_GRAM_KELVIN, 1.0);
        TO_CANON.put(K_PER_MIN, 1.0);
        TO_CANON.put("°C/min", 1.0);
        TO_CANON.put(KELVIN, 1.0);
        TO_CANON.put(CELSIUS, Double.NaN);
    }

    private Units() {
    }

    public static boolean isTemperature(String unit) {
        return KELVIN.equals(unit) || CELSIUS.equals(unit);
    }

    public static boolean isKnown(String unit) {
        return TO_CANON.containsKey(unit);
    }

    /**
     * 把数值换到规范单位。摄氏温度必须显式给出坐标基准，避免把偏移量和坐标值混淆。
     */
    public static double toCanonical(double value, String unit, String basis) {
        if (!isKnown(unit)) {
            throw new UnknownUnitException(unit);
        }
        if (isTemperature(unit)) {
            if (CELSIUS.equals(unit)) {
                return value + 273.15;
            }
            return value;
        }
        Double factor = TO_CANON.get(unit);
        return value * factor;
    }

    public static double fromKelvin(double valueK, String targetUnit) {
        if (KELVIN.equals(targetUnit)) {
            return valueK;
        }
        if (CELSIUS.equals(targetUnit)) {
            return valueK - 273.15;
        }
        throw new UnknownUnitException(targetUnit);
    }

    public static final class UnknownUnitException extends RuntimeException {
        public UnknownUnitException(String unit) {
            super("未知或未登记的单位，按规则不做猜测转换: " + unit);
        }
    }
}
