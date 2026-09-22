package dsc.domain;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A domain value with its unit/coordinate basis and the original textual representation.
 * Normalization happens only on the derivation layer; values that cannot be converted stay
 * in place with a diagnostic rather than being dropped for plotting.
 */
public final class Quant {
    public final double value;          // normalized numeric value (canonical unit), NaN if unconvertible
    public final String unit;           // canonical unit, e.g. "K", "K/min", "mg", "W/g"
    public final String rawText;        // original representation as imported
    public final String rawUnit;        // original unit token as imported
    public final boolean convertible;

    private Quant(double value, String unit, String rawText, String rawUnit, boolean convertible) {
        this.value = value; this.unit = unit; this.rawText = rawText; this.rawUnit = rawUnit;
        this.convertible = convertible;
    }

    public static Quant of(double value, String unit) { return new Quant(value, unit, fmt(value) + " " + unit, unit, true); }

    /** Parse with a small explicit unit table. Anything unknown stays in place (unconvertible). */
    public static Quant parse(String text, String rawUnit) {
        String t = text == null ? "" : text.trim();
        String u = rawUnit == null ? "" : rawUnit.trim();
        Double num = number(t);
        if (num == null) return new Quant(Double.NaN, u == null ? "" : u, t, u, false);
        String key = u.replace("°", "deg").replace("℃", "degC").replace("*", "");
        switch (key) {
            case "K":
                return new Quant(num, "K", t, u, true);
            case "degC":
                return new Quant(num + 273.15, "K", t, u, true);
            case "mg":
                return new Quant(num, "mg", t, u, true);
            case "g":
                return new Quant(num * 1000.0, "mg", t, u, true);
            case "K/min":
                return new Quant(num, "K/min", t, u, true);
            case "degC/min":
                return new Quant(num, "K/min", t, u, true);
            case "mL/min":
                return new Quant(num, "mL/min", t, u, true);
            case "W/g":
                return new Quant(num, "W/g", t, u, true);
            case "mW":
                return new Quant(num, "mW", t, u, true);
            case "J/g":
                return new Quant(num, "J/g", t, u, true);
            case "":
                return new Quant(num, "", t, u, true);
            default:
                return new Quant(Double.NaN, u, t, u, false);
        }
    }

    private static Double number(String t) {
        try { return Double.valueOf(t); } catch (NumberFormatException e) { return null; }
    }

    private static String fmt(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    public ObjectNode node() {
        ObjectNode n = Json.obj();
        n.put("value", convertible ? value : (Double) null);
        n.put("unit", unit);
        n.put("rawText", rawText);
        n.put("rawUnit", rawUnit);
        n.put("convertible", convertible);
        return n;
    }

    public static ObjectNode raw(String text, String unit, String diagnostic) {
        ObjectNode n = Json.obj();
        n.putNull("value");
        n.put("unit", unit == null ? "" : unit);
        n.put("rawText", text);
        n.put("rawUnit", unit);
        n.put("convertible", false);
        if (diagnostic != null) n.put("diagnostic", diagnostic);
        return n;
    }
}
