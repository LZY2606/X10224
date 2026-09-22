package dsc.units;

public record Quantity(
        double si,
        String rawText,
        String unit,
        String dimension,
        boolean converted,
        String diagnostic
) {
    public static final String TEMPERATURE = "temperature";
    public static final String MASS = "mass";
    public static final String RATE = "scan_rate";
    public static final String HEAT_FLOW = "heat_flow";
    public static final String ENERGY = "energy";
    public static final String SPECIFIC_ENERGY = "specific_energy";
    public static final String TIME = "time";

    public static Quantity unconvertible(String rawText, String dimension, String diagnostic) {
        return new Quantity(Double.NaN, rawText, "", dimension, false, diagnostic);
    }

    public boolean valid() {
        return converted && !Double.isNaN(si) && Double.isFinite(si);
    }
}
