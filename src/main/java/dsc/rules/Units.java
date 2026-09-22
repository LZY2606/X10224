package dsc.rules;

import java.util.List;
import java.util.Map;

/**
 * Unit handling: raw representations are kept verbatim on import; this layer
 * only converts at derivation time. Values that cannot be converted stay at
 * their origin and receive a diagnostic instead of being dropped for plotting.
 */
public final class Units {

    private Units() {
    }

    public static final List<String> TEMPERATURE = List.of("degC", "K", "degF");
    public static final List<String> MASS = List.of("mg", "g", "kg", "ug");
    public static final List<String> HEAT_FLOW = List.of("W/g", "mW", "W", "mW/g");

    public static boolean convertible(String unit, List<String> known) {
        return known.contains(norm(unit));
    }

    private static String norm(String unit) {
        return unit == null ? "" : unit.trim().replace("°", "deg").replace("℃", "degC")
                .replace("µ", "u").replace(" ", "");
    }

    public static String normalized(String unit) {
        return norm(unit);
    }

    /** Convert temperature to degC. */
    public static double temperatureToC(double value, String rawUnit) {
        switch (norm(rawUnit)) {
            case "degC":
            case "c":
                return value;
            case "k":
                return value - 273.15;
            case "degf":
            case "f":
                return (value - 32.0) * 5.0 / 9.0;
            default:
                throw new UnconvertibleException("temperature", rawUnit);
        }
    }

    /** Convert mass to mg. */
    public static double massToMg(double value, String rawUnit) {
        switch (norm(rawUnit)) {
            case "mg":
                return value;
            case "g":
                return value * 1000.0;
            case "kg":
                return value * 1_000_000.0;
            case "ug":
                return value / 1000.0;
            default:
                throw new UnconvertibleException("mass", rawUnit);
        }
    }

    /**
     * Normalise a heat-flow reading to W/g.
     *
     * @param massMg sample mass in mg; required for total-power units.
     */
    public static double heatFlowToWPerG(double value, String rawUnit, double massMg) {
        double mG = massMg / 1000.0;
        switch (norm(rawUnit)) {
            case "w/g":
                return value;
            case "mw/g":
                return value / 1000.0;
            case "w":
                return value / mG;
            case "mw":
                return value / 1000.0 / mG;
            default:
                throw new UnconvertibleException("heat flow", rawUnit);
        }
    }

    /** Convert a normalised (W/g) reading into raw-unit space. */
    public static double wPerGToHeatFlow(double wpg, String rawUnit, double massMg) {
        double mG = massMg / 1000.0;
        switch (norm(rawUnit)) {
            case "w/g":
                return wpg;
            case "mw/g":
                return wpg * 1000.0;
            case "w":
                return wpg * mG;
            case "mw":
                return wpg * mG * 1000.0;
            default:
                throw new UnconvertibleException("heat flow", rawUnit);
        }
    }

    /** Trapezoidal integral of y over t (degC), returning W/g*degC units. */
    public static double trapZT(double[] t, double[] z, int from, int to) {
        double acc = 0;
        for (int i = from; i < to; i++) {
            acc += 0.5 * (z[i + 1] + z[i]) * (t[i + 1] - t[i]);
        }
        return acc;
    }

    public static class UnconvertibleException extends RuntimeException {
        public final String quantity;
        public final String unit;

        public UnconvertibleException(String quantity, String unit) {
            super("cannot convert " + quantity + " unit: " + unit);
            this.quantity = quantity;
            this.unit = unit;
        }

        public Map<String, Object> detail() {
            return Map.of("quantity", quantity, "unit", String.valueOf(unit));
        }
    }
}
