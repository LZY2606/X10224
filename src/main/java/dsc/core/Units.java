package dsc.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unit / coordinate-basis handling. Raw archives keep their original textual
 * representation; only the derived layer normalizes. Values that cannot be
 * converted stay in place and produce a diagnostic instead of being dropped.
 */
public final class Units {

    public record Converted(Double canonical, String canonicalUnit, String diagnostic) {
        public boolean ok() { return canonical != null; }
    }

    private Units() {}

    private static final Map<String, Double> MASS_TO_MG = new LinkedHashMap<>();
    private static final Map<String, Double> RATE_TO_K_PER_MIN = new LinkedHashMap<>();
    private static final Map<String, Double> FLOW_TO_MW = new LinkedHashMap<>();

    static {
        MASS_TO_MG.put("mg", 1.0);
        MASS_TO_MG.put("g", 1000.0);
        MASS_TO_MG.put("ug", 0.001);
        RATE_TO_K_PER_MIN.put("K/min", 1.0);
        RATE_TO_K_PER_MIN.put("C/min", 1.0);
        RATE_TO_K_PER_MIN.put("K/s", 60.0);
        FLOW_TO_MW.put("mW", 1.0);
        FLOW_TO_MW.put("mJ/s", 1.0);
        FLOW_TO_MW.put("W", 1000.0);
    }

    public static Converted massToMg(double v, String unit) {
        Double f = MASS_TO_MG.get(unit == null ? "" : unit.trim());
        if (f == null) return new Converted(null, "mg",
                "unit_error: cannot convert mass unit '" + unit + "' to mg; value left at original position");
        return new Converted(v * f, "mg", null);
    }

    public static Converted rateToKPerMin(double v, String unit) {
        Double f = RATE_TO_K_PER_MIN.get(unit == null ? "" : unit.trim());
        if (f == null) return new Converted(null, "K/min",
                "unit_error: cannot convert scan-rate unit '" + unit + "' to K/min; value left at original position");
        return new Converted(v * f, "K/min", null);
    }

    public static Converted heatFlowToMw(double v, String unit) {
        Double f = FLOW_TO_MW.get(unit == null ? "" : unit.trim());
        if (f == null) return new Converted(null, "mW",
                "unit_error: cannot convert heat-flow unit '" + unit + "' to mW; value left at original position");
        return new Converted(v * f, "mW", null);
    }

    /** Linear temperature calibration: T_true = scale * T_raw + offset. */
    public static double calibrateTemperature(double raw, double scale, double offset) {
        return scale * raw + offset;
    }
}
