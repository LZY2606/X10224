package dsc.model;

/** 领域量：数值 + 单位 + 坐标基准。raw 保留导入时的原始表示。 */
public record Quantity(double value, String unit, String basis, String raw) {
    public static Quantity of(double v, String unit, String basis) {
        return new Quantity(v, unit, basis, null);
    }
}
