package dsc.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;

/**
 * Unit / coordinate-basis normalization.
 *
 * <p>Internal canonical bases (kept in the DSC domain, not raw SI, so diagnostics match
 * instrument language): temperature {@code degC}, heat flow {@code mW}, mass {@code mg},
 * rate {@code K/min}, integrated energy {@code mJ}, specific enthalpy {@code J/g}.
 * Imported raw text is preserved on every {@link Quantity}.
 */
public final class Units {

    private Units() {}

    public static Quantity temperature(JsonElement e) {
        return convert(e, "degC", Units::toCelsius,
                new String[]{"c", "°c", "degc", "oc", "k", "f", "°f", "degf", "of"});
    }

    public static Quantity heatflow(JsonElement e) {
        return convert(e, "mW", Units::toMilliwatt,
                new String[]{"mw", "w", "uw", "µw", "j/s"});
    }

    public static Quantity mass(JsonElement e) {
        return convert(e, "mg", Units::toMilligram,
                new String[]{"mg", "g", "kg", "ug", "µg"});
    }

    public static Quantity rate(JsonElement e) {
        return convert(e, "K/min", Units::toKPerMin,
                new String[]{"k/min", "c/min", "°c/min", "k/s", "c/s"});
    }

    private static double toCelsius(double v, String u) {
        return switch (u) {
            case "k" -> v - 273.15;
            case "f", "°f", "degf", "of" -> (v - 32.0) * 5.0 / 9.0;
            default -> v;
        };
    }

    private static double toMilliwatt(double v, String u) {
        return switch (u) {
            case "w", "j/s" -> v * 1000.0;
            case "uw", "µw" -> v / 1000.0;
            default -> v;
        };
    }

    private static double toMilligram(double v, String u) {
        return switch (u) {
            case "g" -> v * 1000.0;
            case "kg" -> v * 1_000_000.0;
            case "ug", "µg" -> v / 1000.0;
            default -> v;
        };
    }

    private static double toKPerMin(double v, String u) {
        return switch (u) {
            case "k/s", "c/s" -> v * 60.0;
            default -> v;
        };
    }

    private interface Conv { double apply(double v, String unit); }

    private static Quantity convert(JsonElement e, String targetUnit, Conv conv, String[] known) {
        double rawVal;
        String rawUnit;
        String rawText;
        if (e == null || e.isJsonNull()) {
            return Quantity.unconvertible("", "", "missing value");
        }
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            rawText = J.str(o, "rawText", "");
            rawUnit = J.str(o, "rawUnit", J.str(o, "unit", ""));
            if (o.has("value") && o.get("value").isJsonPrimitive()) {
                try {
                    rawVal = o.get("value").getAsDouble();
                } catch (NumberFormatException nfe) {
                    return Quantity.unconvertible(rawText, rawUnit, "unparseable numeric value: " + rawText);
                }
            } else {
                try {
                    rawVal = Double.parseDouble(rawText.trim());
                } catch (NumberFormatException nfe) {
                    return Quantity.unconvertible(rawText, rawUnit, "unparseable numeric value: " + rawText);
                }
            }
            if (rawText.isEmpty()) rawText = Quantity.format(rawVal);
        } else {
            rawText = e.getAsString();
            rawUnit = "";
            try {
                String[] tok = rawText.trim().split("\\s+", 2);
                rawVal = Double.parseDouble(tok[0]);
                if (tok.length == 2) rawUnit = tok[1];
            } catch (NumberFormatException nfe) {
                return Quantity.unconvertible(rawText, rawUnit, "unparseable numeric value: " + rawText);
            }
        }
        String normUnit = rawUnit.toLowerCase(Locale.ROOT).trim();
        for (String k : known) {
            if (normUnit.equals(k)) {
                double nv = conv.apply(rawVal, k);
                return Quantity.of(nv, targetUnit, rawText, rawUnit);
            }
        }
        return Quantity.unconvertible(rawText, rawUnit, "unknown unit '" + rawUnit + "'; kept raw, not plotted as canonical");
    }
}
