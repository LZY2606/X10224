package dsc.rules;

import dsc.model.Series;
import java.util.ArrayList;
import java.util.List;

/**
 * Integrates a (possibly normalized) series against a baseline candidate over
 * a draggable interval. Handles non-uniform temperature spacing via per-step
 * trapezoids, reports onset/peak/endset and enthalpy with explicit error
 * sources. Empty intervals produce an invalid result plus diagnostics, never
 * an exception.
 */
public final class Integrator {

    public record Result(
            boolean valid,
            Double onsetC, Double peakC, Double endsetC,
            Double enthalpyJPerG,          // null when not mass-normalized
            double areaMwC,                // raw integrated deviation, mW*C
            String peakLabel,              // endothermic|exothermic|undetermined
            List<String> errorSources,
            List<dsc.model.Diagnostic> diagnostics) {}

    private Integrator() {}

    public static Result integrate(Series s, BaselineEngine.Candidate baseline,
                                   double t0, double t1,
                                   Double scanRateCPerMin, Double massMg,
                                   String signConvention) {
        List<String> errors = new ArrayList<>();
        List<dsc.model.Diagnostic> diags = new ArrayList<>();

        int n = s.countIn(t0, t1);
        if (n == 0) {
            diags.add(new dsc.model.Diagnostic("interval.empty",
                    "interval [" + t0 + ", " + t1 + "] contains no measured points",
                    "interval"));
            errors.add("empty interval: no measured points between " + t0 + " and " + t1 + " C");
            return new Result(false, null, null, null, null, 0.0, "undetermined", errors, diags);
        }

        if (s.spacingNonUniform(0.05)) {
            errors.add("non-uniform temperature spacing: trapezoid integration over per-step dT");
        }
        if (scanRateCPerMin == null) {
            errors.add("scan rate unavailable: enthalpy per gram not computed");
        }
        if (massMg == null) {
            errors.add("sample mass unavailable or unconvertible: enthalpy per gram not computed");
        }

        // Peak: max absolute deviation from baseline inside the interval.
        double peakDev = 0;
        double peakT = Double.NaN;
        for (int i = 0; i < s.size(); i++) {
            double t = s.temp[i];
            if (t < t0 || t > t1) continue;
            double dev = s.heatFlow[i] - baseline.at(t);
            if (Math.abs(dev) > Math.abs(peakDev)) { peakDev = dev; peakT = t; }
        }

        // Onset/endset: steepest-edge tangent intersected with baseline.
        double onset = t0;
        double endset = t1;
        double maxSlope = 0; int maxI = -1;
        double minSlope = 0; int minI = -1;
        for (int i = 0; i + 1 < s.size(); i++) {
            double tA = s.temp[i], tB = s.temp[i + 1];
            if (tB < t0 || tA > t1 || tB == tA) continue;
            double devA = s.heatFlow[i] - baseline.at(tA);
            double devB = s.heatFlow[i + 1] - baseline.at(tB);
            double slope = (devB - devA) / (tB - tA);
            if (!Double.isNaN(peakT) && tB <= peakT && Math.abs(slope) > Math.abs(maxSlope)) { maxSlope = slope; maxI = i; }
            if (!Double.isNaN(peakT) && tA >= peakT && Math.abs(slope) > Math.abs(minSlope)) { minSlope = slope; minI = i; }
        }
        if (maxI >= 0 && maxSlope != 0) {
            double tA = s.temp[maxI];
            double devA = s.heatFlow[maxI] - baseline.at(tA);
            double tX = tA - devA / maxSlope;
            if (tX > t0 && tX < peakT) onset = tX;
        }
        if (minI >= 0 && minSlope != 0) {
            double tA = s.temp[minI];
            double devA = s.heatFlow[minI] - baseline.at(tA);
            double tX = tA - devA / minSlope;
            if (tX > peakT && tX < t1) endset = tX;
        }

        // Area under deviation vs temperature (trapezoid, non-uniform safe).
        double area = 0;
        for (int i = 0; i + 1 < s.size(); i++) {
            double a = Math.max(onset, s.temp[i]);
            double b = Math.min(endset, s.temp[i + 1]);
            if (b <= a) continue;
            double fa = s.lerp(i, a) - baseline.at(a);
            double fb = s.lerp(i, b) - baseline.at(b);
            area += 0.5 * (fa + fb) * (b - a);
        }

        Double enthalpy = null;
        if (scanRateCPerMin != null && scanRateCPerMin != 0 && massMg != null && massMg > 0) {
            // mW*C / (C/min) = mW*min = mJ*60/1000... mW*min = 0.001 J * 60 = 0.06 J
            double joules = area / scanRateCPerMin * 60.0 / 1000.0;
            enthalpy = joules / (massMg / 1000.0); // J/g
        }

        String label = DirectionRule.label(signConvention, peakDev);
        return new Result(true, onset, peakT, endset, enthalpy, area, label, errors, diags);
    }
}
