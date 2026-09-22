package dsc.model;

import com.google.gson.JsonObject;

/**
 * A measured domain quantity.
 *
 * <p>{@code rawText} is the exact representation imported from the instrument file and is
 * never lost. {@code value}/{@code unit} are the normalized (SI-based) form used by the
 * derivation layer. When conversion is impossible (unknown unit, unparseable text),
 * {@code convertible} is false, {@code value} is NaN, the raw value stays in place and a
 * diagnostic is attached — plotting never silently drops the point.
 */
public final class Quantity {
    public final double value;
    public final String unit;
    public final String rawText;
    public final String rawUnit;
    public final boolean convertible;

    private Quantity(double value, String unit, String rawText, String rawUnit, boolean convertible) {
        this.value = value;
        this.unit = unit;
        this.rawText = rawText;
        this.rawUnit = rawUnit;
        this.convertible = convertible;
    }

    public static Quantity of(double value, String unit, String rawText, String rawUnit) {
        return new Quantity(value, unit, rawText, rawUnit, true);
    }

    public static Quantity normalized(double value, String unit) {
        String text = format(value);
        return new Quantity(value, unit, text, unit, true);
    }

    public static Quantity unconvertible(String rawText, String rawUnit, String reason) {
        JsonObject unused = new JsonObject();
        Quantity q = new Quantity(Double.NaN, "?", rawText, rawUnit, false);
        q.reason = reason;
        return q;
    }

    private String reason = "";

    public String reason() { return reason; }

    public static String format(double v) {
        if (Double.isNaN(v)) return "NaN";
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return String.format(java.util.Locale.ROOT, "%.6g", v);
    }

    public JsonObject toJson() {
        JsonObject o = J.obj();
        if (convertible) J.put(o, "value", value);
        else J.put(o, "value", (String) null);
        J.put(o, "unit", unit);
        J.put(o, "rawText", rawText);
        J.put(o, "rawUnit", rawUnit == null ? "" : rawUnit);
        J.put(o, "convertible", convertible);
        if (!convertible && reason != null && !reason.isEmpty()) J.put(o, "diagnostic", reason);
        return o;
    }
}
