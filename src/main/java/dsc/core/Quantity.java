package dsc.core;

import java.util.OptionalDouble;

/** 携带单位的领域量，保留原始文本表示。 */
public final class Quantity {
    public final double value;
    public final String unit;
    public final String rawText;

    public Quantity(double value, String unit, String rawText) {
        this.value = value;
        this.unit = unit;
        this.rawText = rawText;
    }

    public static Quantity of(double value, String unit) {
        return new Quantity(value, unit, value + " " + unit);
    }

    /** 规范化为目标单位制；无法转换时返回 empty（调用方保留原值并写诊断）。 */
    public OptionalDouble normalized(java.util.Map<String, Double> table) {
        return Unit.convert(value, unit, table);
    }
}
