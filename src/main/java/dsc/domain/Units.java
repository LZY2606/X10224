package dsc.domain;

import java.util.Optional;

/** 领域量：所有数值携带单位与坐标基准。导入层保留原始表示，派生层才做规范化。 */
public final class Units {

    /** basis: 坐标基准，例如 temperature 的 "C"/"K"，或自定义标定基准名。 */
    public record Quantity(double value, String unit, String basis) {
        public static Quantity of(double value, String unit) {
            return new Quantity(value, unit, unit);
        }
    }

    /** 转换结果；无法转换时 converted 为空、original 保留原值并附诊断。 */
    public record Conversion(Optional<Quantity> converted, Quantity original, String diagnostic) {
        public boolean ok() { return converted.isPresent(); }
    }

    private Units() {}

    public static Conversion convert(Quantity q, String targetUnit) {
        if (q.unit().equals(targetUnit)) {
            return new Conversion(Optional.of(q), q, null);
        }
        Double factor = linearFactor(q.unit(), targetUnit);
        if (factor != null) {
            return new Conversion(Optional.of(new Quantity(q.value() * factor, targetUnit, q.basis())), q, null);
        }
        // 温度仿射转换
        if (q.unit().equals("C") && targetUnit.equals("K")) {
            return new Conversion(Optional.of(new Quantity(q.value() + 273.15, "K", q.basis())), q, null);
        }
        if (q.unit().equals("K") && targetUnit.equals("C")) {
            return new Conversion(Optional.of(new Quantity(q.value() - 273.15, "C", q.basis())), q, null);
        }
        // 无法转换：值留在原位置并附诊断，不为了画图丢掉
        return new Conversion(Optional.empty(), q,
                "cannot convert unit '" + q.unit() + "' to '" + targetUnit + "'; value left in original position");
    }

    private static Double linearFactor(String from, String to) {
        return switch (from + "->" + to) {
            case "mW->W" -> 0.001;
            case "W->mW" -> 1000.0;
            case "mg->g" -> 0.001;
            case "g->mg" -> 1000.0;
            case "mJ->J" -> 0.001;
            case "J->mJ" -> 1000.0;
            case "K/min->K/s" -> 1.0 / 60.0;
            case "K/s->K/min" -> 60.0;
            case "min->s" -> 60.0;
            case "s->min" -> 1.0 / 60.0;
            default -> null;
        };
    }
}
