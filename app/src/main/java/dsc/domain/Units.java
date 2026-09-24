package dsc.domain;

import java.util.List;
import java.util.OptionalDouble;

/**
 * All domain quantities carry a unit (or a coordinate frame). Original representations are kept
 * verbatim at import; normalization happens only in the derivation layer. Values that cannot be
 * converted stay in place and produce a diagnostic instead of being dropped for plotting.
 */
public final class Units {
    private Units() {}

    public static final List<String> TEMPERATURE = List.of("°C", "K", "F");
    public static final List<String> HEAT_FLOW = List.of("W/g", "mW/mg", "W", "mW");
    public static final List<String> MASS = List.of("mg", "g", "kg");
    public static final List<String> RATE = List.of("°C/min", "K/min");
    public static final List<String> ENTHALPY_SPECIFIC = List.of("J/g");

    public static OptionalDouble toCelsius(double value, String unit) {
        if (unit == null) return OptionalDouble.empty();
        switch (unit.trim()) {
            case "°C": case "C": return OptionalDouble.of(value);
            case "K": return OptionalDouble.of(value - 273.15);
            case "F": return OptionalDouble.of((value - 32.0) * 5.0 / 9.0);
            default: return OptionalDouble.empty();
        }
    }

    /** Heat flow in W/g. Returns empty when the unit is unknown (diagnostic keeps the raw value). */
    public static OptionalDouble toWattsPerGram(double value, String unit, double massMg) {
        if (unit == null) return OptionalDouble.empty();
        switch (unit.trim()) {
            case "W/g": return OptionalDouble.of(value);
            case "mW/mg": return OptionalDouble.of(value);
            case "W": return massMg > 0 ? OptionalDouble.of(value * 1000.0 / massMg) : OptionalDouble.empty();
            case "mW": return massMg > 0 ? OptionalDouble.of(value / massMg) : OptionalDouble.empty();
            default: return OptionalDouble.empty();
        }
    }

    public static OptionalDouble toMg(double value, String unit) {
        if (unit == null) return OptionalDouble.empty();
        switch (unit.trim()) {
            case "mg": return OptionalDouble.of(value);
            case "g": return OptionalDouble.of(value * 1000.0);
            case "kg": return OptionalDouble.of(value * 1_000_000.0);
            default: return OptionalDouble.empty();
        }
    }

    /** °C/min. */
    public static OptionalDouble toCelsiusPerMin(double value, String unit) {
        if (unit == null) return OptionalDouble.empty();
        switch (unit.trim()) {
            case "°C/min": case "K/min": return OptionalDouble.of(value);
            default: return OptionalDouble.empty();
        }
    }
}
