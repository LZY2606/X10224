package dsc.domain;

import java.util.Locale;

/**
 * All domain quantities carry a unit or coordinate basis. The original
 * representation is kept at import time; this layer is the only place where
 * normalization to SI happens.
 *
 * <p>Conventions:
 * <ul>
 *   <li>temperature: kelvin (°C/°F offsets handled)</li>
 *   <li>heat flow: W (instrument tables are normally mW)</li>
 *   <li>specific heat flow: W/kg (tables may be mW, W/g, W/kg)</li>
 *   <li>mass: kg (tables may be mg, g)</li>
 *   <li>rate: K/s (tables may be K/min, °C/min)</li>
 *   <li>enthalpy: J; specific enthalpy J/kg (tables may use mJ, J/g)</li>
 * </ul>
 */
public final class Units {

    private Units() {
    }

    public static UnitValue parseTemperature(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) {
            return new UnitValue(raw, null, "K", "empty temperature");
        }
        String unit = "K";
        String body = t;
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.endsWith("\u00b0c") || lower.endsWith("degc")) {
            int cut = lower.endsWith("\u00b0c") ? 2 : 4;
            unit = "\u00b0C";
            body = t.substring(0, t.length() - cut).trim();
        } else if (lower.endsWith("\u00b0f") || lower.endsWith("degf")) {
            int cut = lower.endsWith("\u00b0f") ? 2 : 4;
            unit = "\u00b0F";
            body = t.substring(0, t.length() - cut).trim();
        } else if (lower.endsWith("k")) {
            unit = "K";
            body = t.substring(0, t.length() - 1).trim();
        } else {
            // Bare numeric temperatures in a DSC export are degrees Celsius;
            // the raw text is retained so the ambiguity stays visible.
            unit = "\u00b0C";
            body = t;
        }
        Double value = number(body);
        if (value == null) {
            return new UnitValue(raw, null, unit, "cannot parse temperature: " + raw);
        }
        double si = switch (unit) {
            case "°C" -> value + 273.15;
            case "°F" -> (value - 32.0) * 5.0 / 9.0 + 273.15;
            default -> value;
        };
        return new UnitValue(raw, si, unit, null);
    }

    public static UnitValue parseMass(String raw) {
        return scaled(raw, "kg", new String[]{"mg", "ug", "µg", "g", "kg"},
                new double[]{1e-6, 1e-9, 1e-9, 1e-3, 1.0});
    }

    public static UnitValue parseRate(String raw) {
        String t = raw == null ? "" : raw.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        double perMinute = lower.contains("/min") || lower.contains(" min");
        if (lower.contains("/s") || lower.contains(" per s")) {
            perMinute = false;
        }
        String[] units = {"k/min", "°c/min", "degc/min", "k/s", "°c/s", "degc/s"};
        String body = t;
        String unit = "K/min";
        for (String u : units) {
            if (lower.endsWith(u)) {
                body = t.substring(0, t.length() - u.length()).trim();
                unit = u;
                break;
            }
        }
        Double n = number(body);
        if (n == null) {
            return new UnitValue(raw, null, unit, "cannot parse rate: " + raw);
        }
        double si = lower.contains("/s") ? n : n / 60.0;
        return new UnitValue(raw, si, unit, null);
    }

    public static UnitValue parseHeatFlow(String raw) {
        return scaled(raw, "W", new String[]{"mw", "uw", "µw", "w", "kw"},
                new double[]{1e-3, 1e-6, 1e-6, 1.0, 1e3});
    }

    public static UnitValue parseSpecificHeatFlow(String raw) {
        String lower = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        String body = lower;
        double scale = 1.0;
        String unit = "W/g";
        if (lower.endsWith("mw/g")) {
            scale = 1e-3;
            body = lower.substring(0, lower.length() - 4);
            unit = "mW/g";
        } else if (lower.endsWith("w/kg")) {
            scale = 1.0 / 1000.0;
            body = lower.substring(0, lower.length() - 4);
            unit = "W/kg";
        } else if (lower.endsWith("w/g")) {
            scale = 1.0;
            body = lower.substring(0, lower.length() - 3);
            unit = "W/g";
        }
        Double n = number(body.trim());
        if (n == null) {
            return new UnitValue(raw, null, unit, "cannot parse specific heat flow: " + raw);
        }
        return new UnitValue(raw, n * scale, unit, null);
    }

    private static UnitValue scaled(String raw, String siUnit, String[] suffixes,
                                    double[] factors) {
        String t = raw == null ? "" : raw.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        double factor = 1.0;
        String unit = siUnit;
        String body = t;
        for (int i = 0; i < suffixes.length; i++) {
            if (lower.endsWith(suffixes[i])) {
                factor = factors[i];
                unit = suffixes[i];
                body = t.substring(0, t.length() - suffixes[i].length()).trim();
                break;
            }
        }
        Double n = number(body);
        if (n == null) {
            return new UnitValue(raw, null, unit, "cannot parse value: " + raw);
        }
        return new UnitValue(raw, n * factor, unit, null);
    }

    private static boolean hasDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static Double number(String s) {
        if (s == null) {
            return null;
        }
        s = s.trim().replace(",", ".");
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Trapezoid area of y(x) in SI units; x must be strictly increasing. */
    public static double integrate(double[] x, double[] y) {
        double area = 0.0;
        for (int i = 1; i < x.length; i++) {
            area += 0.5 * (y[i] + y[i - 1]) * (x[i] - x[i - 1]);
        }
        return area;
    }
}
