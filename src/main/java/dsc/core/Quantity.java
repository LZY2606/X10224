package dsc.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A measured value that always travels with its unit and original textual
 * representation. The raw text is preserved through import/export so that nothing
 * domain-relevant is lost for plotting convenience.
 */
public record Quantity(double value, String unit, String raw) {

    private static final Pattern NUMBER_UNIT =
            Pattern.compile("^\\s*([+-]?[0-9]*\\.?[0-9]+(?:[eE][+-]?[0-9]+)?)\\s*(\\S.*?)?\\s*$");

    public static Quantity of(double value, String unit) {
        return new Quantity(value, unit, trim(value) + " " + unit);
    }

    public static Quantity preserved(double value, String unit, String raw) {
        return new Quantity(value, unit, raw);
    }

    /** Parses "10.0 mg" / "10mg" / "10,2 mg" (comma decimal is accepted). */
    public static Quantity parse(String text) {
        if (text == null) {
            throw new UnconvertibleValueException("空数值", null);
        }
        String norm = text.trim().replace(',', '.');
        Matcher m = NUMBER_UNIT.matcher(norm);
        if (!m.matches() || m.group(2) == null || m.group(2).isBlank()) {
            throw new UnconvertibleValueException("无法解析数值与单位: " + text, null);
        }
        double v;
        try {
            v = Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            throw new UnconvertibleValueException("无法解析数值: " + text, null);
        }
        return new Quantity(v, m.group(2).trim(), text.trim());
    }

    public double toBase() {
        return Units.toBase(this);
    }

    public double to(String targetUnit) {
        return Units.convert(this, targetUnit);
    }

    private static String trim(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    @Override public String toString() { return raw; }
}
