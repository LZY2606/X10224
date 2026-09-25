package dsc.core;

import dsc.model.Model.Diagnostic;
import dsc.model.Model.Quantity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Unit layer. Raw imports keep their original representation; only the derived
 * layer normalizes. Unconvertible values stay in place and earn a diagnostic.
 */
public final class Units {
    private Units() {}

    public record Normalized(Double value, String canonicalUnit, Diagnostic diagnostic) {
        public boolean ok() { return value != null; }
    }

    /** Normalize a temperature to Celsius. */
    public static Normalized temperature(double value, String unit) {
        return switch (unit) {
            case "C", "degC", "°C" -> new Normalized(value, "C", null);
            case "K" -> new Normalized(value - 273.15, "C", null);
            case "F" -> new Normalized((value - 32.0) * 5.0 / 9.0, "C", null);
            default -> new Normalized(null, "C", diag("UNIT_UNKNOWN",
                    "temperature unit '" + unit + "' cannot be converted to C; raw value kept", null));
        };
    }

    /** Normalize a heat flow to mW. */
    public static Normalized heatFlow(double value, String unit) {
        return switch (unit) {
            case "mW" -> new Normalized(value, "mW", null);
            case "W" -> new Normalized(value * 1000.0, "mW", null);
            case "uW" -> new Normalized(value / 1000.0, "mW", null);
            case "mJ/s" -> new Normalized(value, "mW", null);
            default -> new Normalized(null, "mW", diag("UNIT_UNKNOWN",
                    "heat-flow unit '" + unit + "' cannot be converted to mW; raw value kept", null));
        };
    }

    /** Normalize a mass to mg. */
    public static Normalized mass(Quantity q) {
        return switch (q.unit()) {
            case "mg" -> new Normalized(q.value(), "mg", null);
            case "g" -> new Normalized(q.value() * 1000.0, "mg", null);
            case "ug" -> new Normalized(q.value() / 1000.0, "mg", null);
            default -> new Normalized(null, "mg", diag("UNIT_UNKNOWN",
                    "mass unit '" + q.unit() + "' cannot be converted to mg; raw value kept", null));
        };
    }

    /** Normalize a scan rate to K/min. */
    public static Normalized rate(double value, String unit) {
        return switch (unit) {
            case "K/min", "C/min" -> new Normalized(value, "K/min", null);
            case "K/s", "C/s" -> new Normalized(value * 60.0, "K/min", null);
            default -> new Normalized(null, "K/min", diag("UNIT_UNKNOWN",
                    "rate unit '" + unit + "' cannot be converted to K/min; raw value kept", null));
        };
    }

    public static Diagnostic diag(String code, String message, String refId) {
        return new Diagnostic("diag:" + UUID.randomUUID(), "WARN", code, message, refId);
    }

    /** Collect diagnostics from a batch of normalization results. */
    public static List<Diagnostic> collect(Normalized... results) {
        List<Diagnostic> out = new ArrayList<>();
        for (Normalized n : results) if (n.diagnostic() != null) out.add(n.diagnostic());
        return out;
    }
}
