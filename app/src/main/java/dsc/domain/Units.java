package dsc.domain;

/**
 * 单位转换与方向判定。规范化只在派生层发生；不可转换的值原样保留并附诊断。
 */
public final class Units {

    private Units() {
    }

    /** 温度 -> K。接受 K, C, °C, F, °F（大小写不敏感）。 */
    public static Q temperature(String raw, String unit) {
        try {
            double v = Double.parseDouble(raw.trim());
            String u = normUnit(unit);
            return switch (u) {
                case "k" -> Q.of(v, raw, "K");
                case "c" -> Q.of(v + 273.15, raw, "°C");
                case "f" -> Q.of((v - 32.0) * 5.0 / 9.0 + 273.15, raw, "°F");
                default -> Q.bad(raw, unit, "unknown temperature unit '" + unit + "'");
            };
        } catch (NumberFormatException e) {
            return Q.bad(raw, unit, "non-numeric temperature");
        }
    }

    /** 热流 -> W（总量族）或 W/g（比热流族）。族别必须与样品质量归一化方式一致。 */
    public static Q heatFlow(String raw, String unit) {
        try {
            double v = Double.parseDouble(raw.trim());
            String u = normUnit(unit);
            return switch (u) {
                // 总量热流
                case "w" -> Q.of(v, raw, "W");
                case "mw" -> Q.of(v / 1000.0, raw, "mW");
                // 比热流（族不同，单独记录，绝不与总量混用）
                case "w/g" -> Q.of(v, raw, "W/g");
                case "mw/mg" -> Q.of(v, raw, "mW/mg"); // 1 mW/mg = 1 W/g
                default -> Q.bad(raw, unit, "unknown heat flow unit '" + unit + "'");
            };
        } catch (NumberFormatException e) {
            return Q.bad(raw, unit, "non-numeric heat flow");
        }
    }

    /** 质量 -> kg。 */
    public static Q mass(String raw, String unit) {
        try {
            double v = Double.parseDouble(raw.trim());
            String u = normUnit(unit);
            return switch (u) {
                case "mg" -> Q.of(v * 1e-6, raw, "mg");
                case "g" -> Q.of(v * 1e-3, raw, "g");
                case "kg" -> Q.of(v, raw, "kg");
                default -> Q.bad(raw, unit, "unknown mass unit '" + unit + "'");
            };
        } catch (NumberFormatException e) {
            return Q.bad(raw, unit, "non-numeric mass");
        }
    }

    /** 扫描速率 -> K/s。原始带符号时保留；无符号时方向由 temperatureDirection 给出。 */
    public static Q scanRate(String raw, String unit, Integer temperatureDirection) {
        try {
            double v = Double.parseDouble(raw.trim());
            String u = normUnit(unit);
            double perSec = switch (u) {
                case "k/min", "c/min", "°c/min" -> v / 60.0;
                case "k/s", "c/s", "°c/s" -> v;
                default -> Double.NaN;
            };
            if (Double.isNaN(perSec)) {
                return Q.bad(raw, unit, "unknown scan rate unit '" + unit + "'");
            }
            if (v == 0.0 && temperatureDirection != null) {
                perSec = temperatureDirection * 0.0; // 保持 -0.0 无意义，仅记录
            }
            return Q.of(perSec, raw, unit);
        } catch (NumberFormatException e) {
            return Q.bad(raw, unit, "non-numeric scan rate");
        }
    }

    /** 焓 -> J/g（质量归一化结果）。 */
    public static Q enthalpyPerMass(double joulesPerGram) {
        return Q.of(joulesPerGram, String.format("%.6g", joulesPerGram), "J/g");
    }

    /** 时间 -> s。 */
    public static Q seconds(String raw, String unit) {
        try {
            double v = Double.parseDouble(raw.trim());
            String u = normUnit(unit);
            return switch (u) {
                case "s" -> Q.of(v, raw, "s");
                case "min" -> Q.of(v * 60.0, raw, "min");
                case "ms" -> Q.of(v / 1000.0, raw, "ms");
                default -> Q.bad(raw, unit, "unknown time unit '" + unit + "'");
            };
        } catch (NumberFormatException e) {
            return Q.bad(raw, unit, "non-numeric time");
        }
    }

    /** 热流族：total(W,mW) 还是 specific(W/g,mW/mg)。 */
    public static String heatFlowFamily(String unit) {
        String u = normUnit(unit);
        return switch (u) {
            case "w", "mw" -> "total";
            case "w/g", "mw/mg" -> "specific";
            default -> "unknown";
        };
    }

    private static String normUnit(String unit) {
        if (unit == null) {
            return "";
        }
        return unit.trim().toLowerCase().replace("°", "").replace("µ", "u");
    }
}
