package dsc.model;

/**
 * 领域量：数值 + 单位 + 坐标基准 + 导入时的原始表示。
 * 规范化只发生在派生层；无法转换时 rawText 仍保留在原位置。
 */
public record Quantity(double value, String unit, String basis, String rawText) {

    public static Quantity of(double value, String unit) {
        return new Quantity(value, unit, null, null);
    }

    public static Quantity of(double value, String unit, String basis) {
        return new Quantity(value, unit, basis, null);
    }

    public static Quantity raw(String rawText, String unit) {
        return new Quantity(Double.NaN, unit, null, rawText);
    }

    public boolean hasNumericValue() {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public Quantity withValue(double newValue) {
        return new Quantity(newValue, unit, basis, rawText);
    }

    @Override
    public String toString() {
        if (hasNumericValue()) {
            String b = basis == null || basis.isBlank() ? "" : " [" + basis + "]";
            return value + " " + unit + b;
        }
        return "raw(" + rawText + ") " + unit;
    }
}
