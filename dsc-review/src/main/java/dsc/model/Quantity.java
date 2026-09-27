package dsc.model;

/**
 * 带来源的量：导入时保留原始表示，派生层才规范化。
 * 无法转换时 value 为 null、diagnostic 给出原因，原值留在原处，绝不为画图而丢弃。
 */
public final class Quantity {
    public String raw;
    public Double value;
    public String unit;
    public String diagnostic;

    public Quantity() {}

    public Quantity(String raw, Double value, String unit, String diagnostic) {
        this.raw = raw;
        this.value = value;
        this.unit = unit;
        this.diagnostic = diagnostic;
    }

    public boolean valid() {
        return value != null && diagnostic == null;
    }
}
