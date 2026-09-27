package dsc.model;

import java.util.Locale;
import java.util.Map;

/**
 * A measured value that keeps its original textual representation. Normalization to a
 * canonical unit happens only in the derivation layer; a value that cannot be converted
 * stays at the source with a diagnostic instead of being dropped for plotting.
 */
public record Q(String raw, String unit, Double canonical, String canonicalUnit, String diagnostic) {

    public static final String MASS_MG = "mg";
    public static final String TEMP_C = "°C";
    public static final String RATE_C_PER_MIN = "°C/min";
    public static final String HEAT_FLOW_MW = "mW";
    public static final String TIME_S = "s";
    public static final String ENERGY_MJ = "mJ";
    public static final String ENTHALPY_J_PER_G = "J/g";

    // canonical unit -> multiplier of (value * factor + offset)
    private static final Map<String, double[]> FACTORS = Map.ofEntries(
            Map.entry("mg", new double[] {1.0, 0.0}),
            Map.entry("g", new double[] {1000.0, 0.0}),
            Map.entry("ug", new double[] {0.001, 0.0}),
            Map.entry("µg", new double[] {0.001, 0.0}),
            Map.entry("mW", new double[] {1.0, 0.0}),
            Map.entry("W", new double[] {1000.0, 0.0}),
            Map.entry("uW", new double[] {0.001, 0.0}),
            Map.entry("µW", new double[] {0.001, 0.0}),
            Map.entry("W/g", new double[] {1.0, 0.0}),       // canonical specific flow J/(g·s)? keep W/g
            Map.entry("mJ", new double[] {1.0, 0.0}),
            Map.entry("J", new double[] {1000.0, 0.0}),
            Map.entry("kJ", new double[] {1_000_000.0, 0.0}),
            Map.entry("J/g", new double[] {1.0, 0.0}),
            Map.entry("kJ/kg", new double[] {1.0, 0.0}),
            Map.entry("s", new double[] {1.0, 0.0}),
            Map.entry("min", new double[] {60.0, 0.0}),
            Map.entry("°C/min", new double[] {1.0, 0.0}),
            Map.entry("K/min", new double[] {1.0, 0.0}),
            Map.entry("mL/min", new double[] {1.0, 0.0})
    );

    public static Q parse(String raw, String unit, String canonicalUnit) {
        String trimmedRaw = raw == null ? "" : raw.trim();
        String u = unit == null ? "" : unit.trim();
        if (trimmedRaw.isEmpty()) {
            return new Q(trimmedRaw, u, null, canonicalUnit, "EMPTY_VALUE");
        }
        double value;
        try {
            value = Double.parseDouble(trimmedRaw.replaceAll("[^0-9eE+\\-.]", ""));
        } catch (NumberFormatException e) {
            return new Q(trimmedRaw, u, null, canonicalUnit,
                    "UNCONVERTIBLE_VALUE:" + trimmedRaw + " (" + u + ")");
        }
        if (!Double.isFinite(value)) {
            return new Q(trimmedRaw, u, null, canonicalUnit, "NON_FINITE_VALUE");
        }
        double[] factor = FACTORS.get(u.toLowerCase(Locale.ROOT).replace(" ", ""));
        if (factor == null) {
            return new Q(trimmedRaw, u, null, canonicalUnit, "UNKNOWN_UNIT:" + u);
        }
        if (!isCompatible(u, canonicalUnit)) {
            return new Q(trimmedRaw, u, null, canonicalUnit,
                    "INCOMPATIBLE_UNIT:" + u + "->" + canonicalUnit);
        }
        return new Q(trimmedRaw, u, value * factor[0] + factor[1], canonicalUnit, null);
    }

    private static boolean isCompatible(String from, String canonical) {
        String f = from.toLowerCase(Locale.ROOT).replace(" ", "");
        return switch (canonical) {
            case MASS_MG -> f.equals("mg") || f.equals("g") || f.equals("ug") || f.equals("µg");
            case HEAT_FLOW_MW -> f.equals("mw") || f.equals("w") || f.equals("uw") || f.equals("µw");
            case ENERGY_MJ -> f.equals("mj") || f.equals("j") || f.equals("kj");
            case ENTHALPY_J_PER_G -> f.equals("j/g") || f.equals("kj/kg");
            case TIME_S -> f.equals("s") || f.equals("min");
            case RATE_C_PER_MIN -> f.equals("°c/min") || f.equals("k/min");
            default -> false
                    || f.equals(canonical.toLowerCase(Locale.ROOT).replace(" ", ""));
        };
    }

    public boolean valid() {
        return canonical != null;
    }
}
