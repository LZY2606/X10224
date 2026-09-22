package dsc.units;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Units {
    private Units() {
    }

    private static final Pattern NUMBER = Pattern.compile("^\\s*([+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*(.*?)\\s*$");

    public static Quantity parse(String rawText, String dimension, String declaredUnit) {
        if (rawText == null || rawText.isBlank()) {
            return Quantity.unconvertible(rawText == null ? "" : rawText, dimension, "empty-value");
        }
        Matcher m = NUMBER.matcher(rawText);
        if (!m.matches()) {
            return Quantity.unconvertible(rawText, dimension, "not-a-number");
        }
        double value;
        try {
            value = Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return Quantity.unconvertible(rawText, dimension, "not-a-number");
        }
        String unit = declaredUnit != null && !declaredUnit.isBlank() ? declaredUnit.trim() : m.group(2);
        return convert(value, unit, dimension, rawText);
    }

    public static Quantity convert(double value, String unit, String dimension, String rawText) {
        String u = unit == null ? "" : unit.trim();
        switch (dimension) {
            case Quantity.TEMPERATURE -> {
                if (u.equalsIgnoreCase("K")) {
                    return new Quantity(value, rawText, "K", dimension, true, null);
                }
                if (u.equals("°C") || u.equalsIgnoreCase("C") || u.equals("degC")) {
                    return new Quantity(value + 273.15, rawText, "°C", dimension, true, null);
                }
                return Quantity.unconvertible(rawText, dimension, "unknown-temperature-unit:" + u);
            }
            case Quantity.MASS -> {
                if (u.equalsIgnoreCase("mg")) {
                    return new Quantity(value * 1e-6, rawText, "mg", dimension, true, null);
                }
                if (u.equalsIgnoreCase("g")) {
                    return new Quantity(value * 1e-3, rawText, "g", dimension, true, null);
                }
                if (u.equalsIgnoreCase("kg")) {
                    return new Quantity(value, rawText, "kg", dimension, true, null);
                }
                return Quantity.unconvertible(rawText, dimension, "unknown-mass-unit:" + u);
            }
            case Quantity.RATE -> {
                String uu = u.replace("°C", "K").replace("degC", "K");
                if (uu.equalsIgnoreCase("K/min")) {
                    return new Quantity(value / 60.0, rawText, u, dimension, true, null);
                }
                if (uu.equalsIgnoreCase("K/s")) {
                    return new Quantity(value, rawText, u, dimension, true, null);
                }
                return Quantity.unconvertible(rawText, dimension, "unknown-rate-unit:" + u);
            }
            case Quantity.HEAT_FLOW -> {
                if (u.equalsIgnoreCase("mW")) {
                    return new Quantity(value * 1e-3, rawText, "mW", dimension, true, null);
                }
                if (u.equalsIgnoreCase("W")) {
                    return new Quantity(value, rawText, "W", dimension, true, null);
                }
                if (u.equals("µW") || u.equals("uW") || u.equals("μW")) {
                    return new Quantity(value * 1e-6, rawText, u, dimension, true, null);
                }
                return Quantity.unconvertible(rawText, dimension, "unknown-heat-flow-unit:" + u);
            }
            case Quantity.ENERGY -> {
                if (u.equalsIgnoreCase("mJ")) {
                    return new Quantity(value * 1e-3, rawText, "mJ", dimension, true, null);
                }
                if (u.equalsIgnoreCase("J")) {
                    return new Quantity(value, rawText, "J", dimension, true, null);
                }
                return Quantity.unconvertible(rawText, dimension, "unknown-energy-unit:" + u);
            }
            case Quantity.SPECIFIC_ENERGY -> {
                if (u.equalsIgnoreCase("J/g")) {
                    return new Quantity(value, rawText, "J/g", dimension, true, null);
                }
                if (u.equalsIgnoreCase("kJ/kg")) {
                    return new Quantity(value, rawText, u, dimension, true, null);
                }
                return Quantity.unconvertible(rawText, dimension, "unknown-specific-energy-unit:" + u);
            }
            case Quantity.TIME -> {
                if (u.equalsIgnoreCase("s") || u.isEmpty()) {
                    return new Quantity(value, rawText, u.isEmpty() ? "s" : u, dimension, true, null);
                }
                if (u.equalsIgnoreCase("min")) {
                    return new Quantity(value * 60.0, rawText, "min", dimension, true, null);
                }
                return Quantity.unconvertible(rawText, dimension, "unknown-time-unit:" + u);
            }
            default -> {
                return Quantity.unconvertible(rawText, dimension, "unknown-dimension:" + dimension);
            }
        }
    }

    public static String formatK(double kelvin, String preferUnit) {
        if (preferUnit != null && preferUnit.contains("C")) {
            return String.format(Locale.ROOT, "%.2f °C", kelvin - 273.15);
        }
        return String.format(Locale.ROOT, "%.2f K", kelvin);
    }
}
