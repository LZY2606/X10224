package dsc.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/** Derives thermal-event results from parsed points. Emits machine-readable judgment
 *  events for every rule decision; never collapses multi-valued states into one. */
public final class Engine {

    public record Inputs(
            List<DscMath.Point> points,
            String kind,                 // glass | crystallization | melting
            double tStartC, double tEndC,
            Double massMg,               // null when no mass recorded
            double calibOffsetC,         // temperature calibration offset, degC
            double calibUncertaintyC,
            String endoConvention        // "exo-up" | "endo-up" | null
    ) {}

    /** Compute the result payload for one baseline candidate. */
    public static Map<String, Object> compute(Inputs in, DscMath.Baseline bl, String baselineLabel) {
        Map<String, Object> p = new LinkedHashMap<>();
        List<String> diags = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();

        List<DscMath.Point> inWindow = DscMath.window(in.points(), in.tStartC(), in.tEndC());
        p.put("points_in_interval", inWindow.size());
        p.put("baseline_label", baselineLabel);

        if (inWindow.isEmpty()) {
            diags.add("interval.empty: no samples inside [" + in.tStartC() + ", " + in.tEndC() + "] degC");
            p.put("onset_c", null); p.put("peak_c", null); p.put("endset_c", null);
            p.put("enthalpy_mj", null); p.put("enthalpy_j_per_g", null);
            p.put("endo_exo", "undecided");
            p.put("diagnostics", diags);
            p.put("errors", errors);
            return p;
        }

        var ope = DscMath.onsetPeakEndset(in.points(), in.tStartC(), in.tEndC(), bl);
        // calibration shifts reported temperatures; raw values stay in the source layer
        p.put("onset_c", ope.onsetC() == null ? null : ope.onsetC() + in.calibOffsetC());
        p.put("peak_c", ope.peakC() == null ? null : ope.peakC() + in.calibOffsetC());
        p.put("endset_c", ope.endsetC() == null ? null : ope.endsetC() + in.calibOffsetC());
        p.put("deviation_sign", ope.deviationSign());

        // endo/exo: convention comes from instrument metadata, never from file names
        if (in.endoConvention() == null) {
            p.put("endo_exo", "undecided");
            diags.add("convention.unknown: instrument header lacks endo/exo convention; " +
                    "if exo-up then \"" + reading("exo-up", ope.deviationSign()) + "\", " +
                    "if endo-up then \"" + reading("endo-up", ope.deviationSign()) + "\"; both kept");
        } else {
            p.put("endo_exo", reading(in.endoConvention(), ope.deviationSign()));
        }

        OptionalDouble mj = DscMath.enthalpyMj(in.points(), in.tStartC(), in.tEndC(), bl);
        if (mj.isEmpty()) {
            p.put("enthalpy_mj", null);
            p.put("enthalpy_j_per_g", null);
            diags.add("interval.empty: enthalpy not computable");
        } else {
            double areaMj = mj.getAsDouble();
            p.put("enthalpy_mj", round(areaMj, 4));
            if (in.massMg() != null && in.massMg() > 0) {
                p.put("enthalpy_j_per_g", round(areaMj / in.massMg(), 4)); // mJ/mg = J/g
                double massTol = 0.02; // mg, balance readout tolerance
                errors.add(Map.of("source", "mass",
                        "detail", "±" + massTol + " mg on " + in.massMg() + " mg",
                        "rel", round(massTol / in.massMg(), 6)));
            } else {
                p.put("enthalpy_j_per_g", null);
                diags.add("mass.missing: normalized enthalpy (J/g) unavailable, raw mJ kept");
            }
        }

        if (DscMath.isNonUniform(inWindow))
            diags.add("sampling.non_uniform: temperature spacing is non-uniform; trapezoid integration on actual samples");
        long neg = inWindow.stream().filter(pt -> pt.hfMw() < 0).count();
        if (neg > 0)
            diags.add("signal.reverse_heat_flow: " + neg + " negative heat-flow samples inside interval; sign preserved in integral");

        errors.add(Map.of("source", "integration",
                "detail", "trapezoid over " + inWindow.size() + " samples" +
                        (DscMath.isNonUniform(inWindow) ? ", non-uniform spacing" : ""),
                "rel", inWindow.size() > 0 ? round(1.0 / inWindow.size(), 6) : 1.0));
        errors.add(Map.of("source", "calibration",
                "detail", "±" + in.calibUncertaintyC() + " degC on onset/peak/endset",
                "abs_c", in.calibUncertaintyC()));

        p.put("errors", errors);
        p.put("diagnostics", diags);
        return p;
    }

    private static String reading(String convention, String deviationSign) {
        boolean positive = "positive-deviation".equals(deviationSign);
        if ("exo-up".equals(convention)) return positive ? "exothermic" : "endothermic";
        return positive ? "endothermic" : "exothermic";
    }

    /** Baseline-choice error: relative spread of enthalpy across parallel candidates. */
    public static Map<String, Object> baselineChoiceError(List<Double> enthalpiesMj) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double e : enthalpiesMj) { min = Math.min(min, e); max = Math.max(max, e); }
        double mid = (max + min) / 2;
        double rel = mid != 0 ? (max - min) / 2 / Math.abs(mid) : 0;
        return Map.of("source", "baseline",
                "detail", enthalpiesMj.size() + " parallel baseline candidates kept; enthalpy spread ±" + round(rel * 100, 2) + "%",
                "rel", round(rel, 6));
    }

    static double round(double v, int digits) {
        double f = Math.pow(10, digits);
        return Math.round(v * f) / f;
    }
}
