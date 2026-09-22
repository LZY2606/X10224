package dsc.rules;

import dsc.domain.BaselineCandidate;
import dsc.domain.ErrorSource;
import dsc.domain.ScanDirection;
import dsc.domain.ThermalResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Integrates one interval against an accepted baseline and locates onset / peak / endset.
 *
 * Integration is over time so non-uniform temperature spacing is handled correctly.
 * Heat flow is already normalized to W/g and endotherm-positive by {@link PreparedSeries},
 * so positive areas are endothermic (melting, heating) and negative areas exothermic
 * (crystallization, cooling) — the conclusion follows the explicit sign convention, not
 * the file name.
 */
public final class PeakAnalyzer {

    private PeakAnalyzer() {
    }

    public static ThermalResult analyze(String resultId, String intervalId,
                                        String segmentId, String eventType,
                                        ScanDirection direction,
                                        PreparedSeries series,
                                        BaselineCandidate baseline,
                                        double fromT, double toT,
                                        double rateKPerMin, double massG,
                                        double calUncertaintyK,
                                        String fingerprint, String ruleset) {
        ThermalResult r = new ThermalResult();
        r.id = resultId;
        r.intervalId = intervalId;
        r.segmentId = segmentId;
        r.eventType = eventType;
        r.direction = direction;
        r.baselineId = baseline.id;
        r.inputsFingerprint = fingerprint;
        r.rulesetVersion = ruleset;
        r.nonUniformTemperature = series.nonUniformTemperature;

        List<double[]> window = series.window(fromT, toT);
        if (window.size() < 2) {
            r.diagnostics.add("empty-interval: only " + window.size() + " usable samples");
            return r;
        }

        double[] excess = new double[window.size()];
        List<double[]> excessTxy = new ArrayList<>();
        for (int i = 0; i < window.size(); i++) {
            double[] p = window.get(i);
            double b = CurveMath.interp(baseline.curve, p[1], true);
            excess[i] = p[2] - b;
            excessTxy.add(new double[]{p[0], p[1], excess[i]});
        }
        r.integratedSamples = window.size();

        double peakIdx = 0;
        double peakAbs = -1;
        for (int i = 0; i < excess.length; i++) {
            if (Math.abs(excess[i]) > peakAbs) {
                peakAbs = Math.abs(excess[i]);
                peakIdx = i;
            }
        }
        boolean endo = excess[(int) peakIdx] >= 0;
        double areaW_s_per_g = CurveMath.integrateByTime(excessTxy);
        // W*s/g = J/g
        r.enthalpy = areaW_s_per_g;
        r.enthalpyAbs = Math.abs(areaW_s_per_g);
        r.sign = endo ? "endothermic" : "exothermic";
        r.peakT = window.get((int) peakIdx)[1];
        r.peakHeight = excess[(int) peakIdx];

        int onsetIdx = locateOnset(excess, (int) peakIdx, endo);
        int endsetIdx = locateEndset(excess, (int) peakIdx, endo);

        if (onsetIdx >= 0) {
            if (eventType.equals("glass-transition")) {
                r.onsetT = window.get(onsetIdx)[1];
            } else {
                r.onsetT = tangentIntersection(window, excess, onsetIdx, (int) peakIdx, true);
            }
        } else {
            r.onsetT = window.get(0)[1];
            r.diagnostics.add("onset not resolved, reporting interval edge");
        }
        if (endsetIdx >= 0) {
            if (eventType.equals("glass-transition")) {
                r.endsetT = window.get(endsetIdx)[1];
            } else {
                r.endsetT = tangentIntersection(window, excess, endsetIdx, (int) peakIdx, false);
            }
        } else {
            r.endsetT = window.get(window.size() - 1)[1];
            r.diagnostics.add("endset not resolved, reporting interval edge");
        }

        detectOverlap(window, excess, r);
        buildErrorBudget(r, series, baseline, rateKPerMin, massG, calUncertaintyK,
                window, excess, endo);
        return r;
    }

    /** First index moving back from the peak where the excess crosses 5% of peak height. */
    private static int locateOnset(double[] excess, int peakIdx, boolean endo) {
        double threshold = 0.05 * (endo ? excess[peakIdx] : -excess[peakIdx]);
        for (int i = peakIdx - 1; i >= 0; i--) {
            double v = endo ? excess[i] : -excess[i];
            if (v <= threshold) {
                return i;
            }
        }
        return -1;
    }

    private static int locateEndset(double[] excess, int peakIdx, boolean endo) {
        double threshold = 0.05 * (endo ? excess[peakIdx] : -excess[peakIdx]);
        for (int i = peakIdx + 1; i < excess.length; i++) {
            double v = endo ? excess[i] : -excess[i];
            if (v <= threshold) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Tangent intersection: leading/trailing inflection tangent of the excess curve meets
     * the zero line. Slope is estimated over up to 3 samples around the anchor.
     */
    private static double tangentIntersection(List<double[]> window, double[] excess,
                                              int anchor, int peakIdx, boolean leading) {
        int n = window.size();
        int i0 = Math.max(0, anchor - 1);
        int i1 = Math.min(n - 1, anchor + 1);
        double dt = window.get(i1)[1] - window.get(i0)[1];
        if (Math.abs(dt) < 1e-12) {
            return window.get(anchor)[1];
        }
        double slope = (excess[i1] - excess[i0]) / dt;
        if (Math.abs(slope) < 1e-15) {
            return window.get(anchor)[1];
        }
        return window.get(anchor)[1] - excess[anchor] / slope;
    }

    private static void detectOverlap(List<double[]> window, double[] excess,
                                      ThermalResult r) {
        int n = excess.length;
        int signChanges = 0;
        for (int i = 1; i < n; i++) {
            if (excess[i - 1] * excess[i] < 0) {
                signChanges++;
            }
        }
        if (signChanges >= 2) {
            r.overlapping = true;
            r.diagnostics.add("overlapping-transitions: excess signal crosses zero "
                    + signChanges + " times; onset/endset and area are biased");
        }
    }

    private static void buildErrorBudget(ThermalResult r, PreparedSeries series,
                                         BaselineCandidate baseline,
                                         double rateKPerMin, double massG,
                                         double calUncertaintyK,
                                         List<double[]> window, double[] excess,
                                         boolean endo) {
        int k = 0;
        String b = r.id;

        double massFraction = series.absoluteHeatFlow ? 0.001 : 0.0;
        if (series.absoluteHeatFlow) {
            r.errorSources.add(new ErrorSource(b + "-err-mass", "mass",
                    "样品质量（天平 0.1%）", r.enthalpyAbs * 0.001, "J/g",
                    "mass=" + fmt(massG) + " g, absolute heat flow normalized by mass"));
        } else {
            r.errorSources.add(new ErrorSource(b + "-err-mass", "mass",
                    "样品质量（已在仪器侧归一化）", 0.0, "J/g",
                    "heat flow archived as specific signal; mass does not rescale area"));
        }

        double baseSpread = baseline.residualRms;
        double durationSec = window.get(window.size() - 1)[0] - window.get(0)[0];
        double baseErr = baseSpread * durationSec;
        r.errorSources.add(new ErrorSource(b + "-err-baseline", "baseline",
                "基线选择/残差（RMS×时长）", baseErr, "J/g",
                "baseline=" + baseline.id + " rms=" + fmt(baseSpread) + " W/g; "
                        + (baseline.warnings.isEmpty() ? "no warnings" : String.join("; ", baseline.warnings))));

        double rateErr = r.enthalpyAbs * 0.002;
        r.errorSources.add(new ErrorSource(b + "-err-rate", "rate",
                "扫描速率标定（0.2%）", rateErr, "J/g",
                "rate=" + fmt(rateKPerMin) + " K/min, time-based integration"));

        r.errorSources.add(new ErrorSource(b + "-err-calibration", "calibration",
                "温度标定", calUncertaintyK, "K",
                "T_true = " + fmt(series.calA) + "*T + " + fmt(series.calB)));

        double quadrature = 0;
        for (ErrorSource e : r.errorSources) {
            if ("J/g".equals(e.unit)) {
                quadrature += e.contribution * e.contribution;
            }
        }
        r.combinedUncertainty = Math.sqrt(quadrature);
    }

    static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.4g", v);
    }
}
