package dsc.model;

import java.util.Objects;

/**
 * 带物理单位的标量。原始导入表示以字符串保留（rawText），
 * 规范化只在派生层进行；无法转换时调用方应保留原值并附诊断，而不是丢弃。
 */
public final class Quantity {
    private final double value;
    private final String unit;
    private final String rawText;

    public Quantity(double value, String unit) {
        this(value, unit, null);
    }

    public Quantity(double value, String unit, String rawText) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("quantity value must be finite: " + value);
        }
        this.value = value;
        this.unit = Objects.requireNonNull(unit, "unit");
        this.rawText = rawText;
    }

    public double value() {
        return value;
    }

    public String unit() {
        return unit;
    }

    public String rawText() {
        return rawText;
    }

    public boolean sameUnit(Quantity other) {
        return this.unit.equals(other.unit);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Quantity q)) return false;
        return Double.compare(q.value, value) == 0 && unit.equals(q.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, unit);
    }

    @Override
    public String toString() {
        return value + " " + unit;
    }
}
