package dsc.core;

import java.util.Map;
import java.util.OptionalDouble;

/**
 * 领域量单位。原始导入保留原始表示（{@link Quantity#rawText}），
 * 仅在派生层规范化；无法转换的值留在原位并附诊断。
 */
public final class Unit {
    private Unit() {}

    public static final Map<String, Double> MASS = Map.of(
            "mg", 1e-6, "g", 1e-3, "kg", 1.0,
            "Mg", 1e-6, "G", 1e-3);

    public static final Map<String, Double> RATE = Map.of(
            "K/min", 1.0, "°C/min", 1.0, "K/s", 60.0, "°C/s", 60.0);

    public static final Map<String, Double> HEAT_FLOW = Map.of(
            "W/g", 1.0, "mW/g", 1e-3, "W/mg", 1e3, "mW/mg", 1.0);

    public static OptionalDouble convert(double value, String unit, Map<String, Double> table) {
        if (unit == null) return OptionalDouble.empty();
        Double f = table.get(unit);
        if (f == null) return OptionalDouble.empty();
        return OptionalDouble.of(value * f);
    }
}
