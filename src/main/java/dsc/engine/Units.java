package dsc.engine;

import dsc.model.Diagnostic;
import java.util.List;
import java.util.function.Consumer;

/**
 * Unit normalization. Import keeps the raw representation; only the derived
 * layer converts. Anything unknown stays put and gets a diagnostic.
 */
public final class Units {
    private Units() {}

    /** Temperature to kelvin. Returns NaN and reports when the unit is unknown. */
    public static double tempToK(double v, String unit, String where, List<Diagnostic> diags) {
        switch (norm(unit)) {
            case "k": return v;
            case "c": return v + 273.15;
            default:
                diags.add(new Diagnostic("UNIT_UNKNOWN_TEMP",
                        "cannot convert temperature unit '" + unit + "'; value left as recorded", where));
                return Double.NaN;
        }
    }

    /** Heat flow to mW. */
    public static double heatFlowToMw(double v, String unit, String where, List<Diagnostic> diags) {
        switch (norm(unit)) {
            case "mw": return v;
            case "w": return v * 1000.0;
            case "uw": return v / 1000.0;
            default:
                diags.add(new Diagnostic("UNIT_UNKNOWN_HEATFLOW",
                        "cannot convert heat-flow unit '" + unit + "'; value left as recorded", where));
                return Double.NaN;
        }
    }

    /** Time to seconds. */
    public static double timeToS(double v, String unit, String where, List<Diagnostic> diags) {
        switch (norm(unit)) {
            case "s": return v;
            case "min": return v * 60.0;
            default:
                diags.add(new Diagnostic("UNIT_UNKNOWN_TIME",
                        "cannot convert time unit '" + unit + "'; value left as recorded", where));
                return Double.NaN;
        }
    }

    /** Mass to mg. */
    public static double massToMg(double v, String unit, String where, List<Diagnostic> diags) {
        switch (norm(unit)) {
            case "mg": return v;
            case "g": return v * 1000.0;
            case "ug": return v / 1000.0;
            default:
                diags.add(new Diagnostic("UNIT_UNKNOWN_MASS",
                        "cannot convert mass unit '" + unit + "'; value left as recorded", where));
                return Double.NaN;
        }
    }

    /** Scan rate to K/min. */
    public static double scanRateToKPerMin(double v, String unit, String where, List<Diagnostic> diags) {
        switch (norm(unit)) {
            case "k/min": case "c/min": return v;
            case "k/s": case "c/s": return v * 60.0;
            default:
                diags.add(new Diagnostic("UNIT_UNKNOWN_SCANRATE",
                        "cannot convert scan-rate unit '" + unit + "'; value left as recorded", where));
                return Double.NaN;
        }
    }

    private static String norm(String unit) {
        if (unit == null) return "";
        String u = unit.trim().toLowerCase();
        u = u.replace("°", "").replace("deg", "");
        return u;
    }

    /** Apply a conversion row-wise, keeping NaN where conversion failed. */
    public static double[] convertAll(double[] in, String unit, String where,
                                      List<Diagnostic> diags, RowConv conv) {
        double[] out = new double[in.length];
        boolean reported = false;
        for (int i = 0; i < in.length; i++) {
            List<Diagnostic> tmp = reported ? null : new java.util.ArrayList<>();
            out[i] = conv.apply(in[i], unit, where + "[" + i + "]", tmp);
            if (tmp != null && !tmp.isEmpty()) {
                diags.addAll(tmp);
                reported = true;
            }
        }
        return out;
    }

    public interface RowConv {
        double apply(double v, String unit, String where, List<Diagnostic> diags);
    }

    public static Consumer<String> noOp() {
        return s -> {};
    }
}
