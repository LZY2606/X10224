package dsc.rules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Onset / peak / endset, raw integral (trapezoid in time so non-uniform temperature
 * spacing is handled correctly), mass-normalized enthalpy and a per-component error
 * budget explaining the enthalpy uncertainty sources.
 */
public final class ThermalAnalysis {

    public static final String RULE_VERSION = "thermal-analysis/v5";

    private ThermalAnalysis() {
    }

    public record BaselinePick(String kind, boolean accepted, double spreadMj) {
    }

    public record Result(
            double onsetC, double peakC, double endsetC,
            double peakResidualMw, String peakPolarity, String eventClass,
            double areaSignedMj, double areaAbsMj,
            Double enthalpyJg, String enthalpySign,
            double[] residual,
            Map<String, Double> errorBudgetJg,
            double combinedErrorJg,
            List<String> diagnostics
    ) {
    }

    /**
     * @param t        calibrated temperature, strictly increasing within one segment
     * @param timeS    sample times in seconds, strictly increasing
     * @param oriented heat flow oriented so exothermic is positive, in mW
     * @param baseline candidate values at each sample, mW
     * @param massMg   accepted sample mass in mg, or null when the mass cannot convert
     * @param rateFrac relative scan-rate uncertainty (e.g. 0.01)
     * @param calFrac  relative temperature calibration uncertainty contribution
     * @param spreadMj area spread between all reasonable baseline candidates, mJ
     */
    public static Result analyze(double[] t, double[] timeS, double[] oriented,
                                 double[] baseline, Double massMg,
                                 double rateFrac, double calFrac, double spreadMj,
                                 List<String> diagnostics) {
        int n = t.length;
        double[] residual = new double[n];
        for (int i = 0; i < n; i++) {
            residual[i] = oriented[i] - baseline[i];
        }

        int peakIdx = 0;
        double bestAbs = -1;
        for (int i = 0; i < n; i++) {
            if (Math.abs(residual[i]) > bestAbs) {
                bestAbs = Math.abs(residual[i]);
                peakIdx = i;
            }
        }
        double peakResidual = residual[peakIdx];
        String polarity = peakResidual >= 0 ? "EXOTHERMIC" : "ENDOTHERMIC";
        String eventClass = classify(polarity, baseline);

        double onset = findTangent(t, residual, peakIdx, true);
        double endset = findTangent(t, residual, peakIdx, false);

        // ∫ residual dt : mW * s = mJ. Trapezoid in time tolerates non-uniform T steps.
        double signed = 0;
        double abs = 0;
        for (int i = 1; i < n; i++) {
            double dt = timeS[i] - timeS[i - 1];
            if (!(dt > 0) || !Double.isFinite(dt)) {
                diagnostics.add("NON_INCREASING_TIME_AT:" + i);
                continue;
            }
            double a = 0.5 * (residual[i] + residual[i - 1]) * dt / 1000.0;
            signed += a;
            abs += Math.abs(a);
        }

        Double enthalpy = null;
        String enthalpySign = null;
        Map<String, Double> budget = new LinkedHashMap<>();
        double combined = Double.NaN;
        if (massMg != null && massMg > 0) {
            // area in mJ, mass in mg -> mJ/mg = J/g
            enthalpy = signed / massMg;
            enthalpySign = signed >= 0 ? "EXOTHERMIC_POSITIVE" : "ENDOTHERMIC_NEGATIVE";
            double base = Math.abs(enthalpy);
            budget.put("baselineSpread", (spreadMj / 1000.0) / massMg);
            budget.put("sampleMass", base * 0.002);                 // balance calibration floor
            budget.put("scanRate", base * rateFrac);
            budget.put("temperatureCalibration", base * calFrac);
            budget.put("numericalIntegration", trapezoidError(t, residual, massMg));
            combined = 0;
            for (double v : budget.values()) {
                combined += v * v;
            }
            combined = Math.sqrt(combined);
        } else {
            diagnostics.add("NORMALIZED_ENTHALPY_EXPIRED:mass-unavailable");
        }

        return new Result(onset, t[peakIdx], endset, peakResidual, polarity, eventClass,
                signed, abs, enthalpy, enthalpySign, residual, budget, combined, diagnostics);
    }

    private static String classify(String polarity, double[] baseline) {
        // A step-shaped residual (glass transition) has no single dominant excursion;
        // classification uses residual symmetry at the caller's interval label instead.
        return switch (polarity) {
            case "EXOTHERMIC" -> "CRYSTALLIZATION";
            default -> "MELTING";
        };
    }

    /**
     * Tangent-intercept onset/endset: build the steepest-slope tangent around the peak
     * and intersect with the zero-residual level. Falls back to interval edges.
     */
    static double findTangent(double[] t, double[] r, int peakIdx, boolean onset) {
        int sign = r[peakIdx] >= 0 ? 1 : -1;
        int win = Math.max(2, r.length / 10);
        double bestSlope = 0;
        int bestI = peakIdx;
        int from = onset ? Math.max(1, peakIdx - win * 2) : peakIdx;
        int to = onset ? peakIdx + 1 : Math.min(r.length - 1, peakIdx + win * 2);
        for (int i = from; i < to; i++) {
            double slope = (r[i] - r[i - 1]) / Math.max(t[i] - t[i - 1], 1e-9);
            if (sign * slope < sign * bestSlope) {
                bestSlope = slope;
                bestI = i;
            }
        }
        double slope = (r[bestI] - r[bestI - 1]) / Math.max(t[bestI] - t[bestI - 1], 1e-9);
        if (Math.abs(slope) < 1e-12) {
            return onset ? t[0] : t[t.length - 1];
        }
        double tIntercept = t[bestI] - r[bestI] / slope;
        if (tIntercept < t[0] || tIntercept > t[t.length - 1]) {
            return onset ? t[0] : t[t.length - 1];
        }
        return tIntercept;
    }

    private static double trapezoidError(double[] t, double[] residual, double massMg) {
        // coarse curvature-based numerical error estimate
        double err = 0;
        for (int i = 1; i < t.length - 1; i++) {
            double d2 = residual[i + 1] - 2 * residual[i] + residual[i - 1];
            err += Math.abs(d2);
        }
        return (err / 1000.0) / massMg * 0.01;
    }
}
