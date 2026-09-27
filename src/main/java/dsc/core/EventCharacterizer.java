package dsc.core;

import dsc.domain.BaselineCandidate;
import dsc.domain.EnthalpyErrorSources;
import dsc.domain.RawPoint;
import dsc.domain.ThermalSign;
import dsc.domain.TransitionResult;
import dsc.domain.TransitionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Characterizes a transition against one baseline candidate: onset/peak/endset
 * (peaks), step height/midpoint (glass), specific enthalpy and endo/exothermic
 * sign. The sign is derived from the excess signal combined with the
 * instrument's exotherm convention recorded at import time.
 */
public final class EventCharacterizer {

    /** Convention for which direction of the raw heat-flow axis is exothermic. */
    public enum ExoConvention { EXO_UP, EXO_DOWN, UNKNOWN }

    private EventCharacterizer() {
    }

    public record Features(
            Double onsetK, Double peakK, Double endsetK, Double peakFlowW,
            Double stepHeightWK, Double midpointK,
            double specificEnthalpyJG, ThermalSign sign,
            List<String> diagnostics) {
    }

    public static Features characterize(List<RawPoint> points, int from, int to,
                                        BaselineCandidate baseline, double junctionK,
                                        TransitionType type, double massKg,
                                        ExoConvention exo) {
        int n = to - from + 1;
        double[] timeS = new double[n];
        double[] tempK = new double[n];
        double[] flowW = new double[n];
        double[] baseW = new double[n];
        for (int i = 0; i < n; i++) {
            RawPoint p = points.get(from + i);
            timeS[i] = p.canonicalTimeS();
            tempK[i] = p.canonicalTempK();
            flowW[i] = p.canonicalFlowW();
            baseW[i] = baseline.kind() == dsc.domain.BaselineKind.STEP_TANGENT
                    ? baseline.stepAtK(tempK[i], junctionK)
                    : baseline.baselineAtK(tempK[i]);
        }
        double[] excess = new double[n];
        for (int i = 0; i < n; i++) {
            excess[i] = flowW[i] - baseW[i];
        }

        List<String> diagnostics = new ArrayList<>();
        double energyJ = Integration.trapTime(timeS, excess, 0, n - 1);
        if (massKg <= 0) {
            diagnostics.add("BLOCKER: non-positive mass; cannot normalize enthalpy");
        }
        double massG = massKg * 1000.0;
        double specificJG = massG <= 0 ? Double.NaN : energyJ / massG;

        ThermalSign sign = decideSign(excess, energyJ, exo, diagnostics);

        Double onset = null;
        Double peak = null;
        Double endset = null;
        Double peakFlow = null;
        Double stepHeight = null;
        Double midpoint = null;

        if (type == TransitionType.GLASS) {
            double[] pre = tangentSegment(baseline, true, junctionK, tempK, excess);
            double[] post = tangentSegment(baseline, false, junctionK, tempK, excess);
            stepHeight = mean(post) - mean(pre);
            // midpoint temperature: halfway between the pre and post tangents at junction
            double half = 0.5 * (baseline.preSlopeWK() + baseline.postSlopeWK()) * junctionK
                    + 0.5 * (baseline.preOffsetW() + baseline.postOffsetW());
            midpoint = tempK[0];
            double best = Double.POSITIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                double d = Math.abs(flowW[i] - half);
                if (d < best) {
                    best = d;
                    midpoint = tempK[i];
                }
            }
        } else {
            int peakIdx = 0;
            double maxAbs = -1;
            for (int i = 0; i < n; i++) {
                if (Math.abs(excess[i]) > maxAbs) {
                    maxAbs = Math.abs(excess[i]);
                    peakIdx = i;
                }
            }
            peak = tempK[peakIdx];
            peakFlow = flowW[peakIdx];
            double threshold = 0.05 * maxAbs;
            int onIdx = peakIdx;
            while (onIdx > 0 && Math.abs(excess[onIdx - 1]) > threshold) {
                onIdx--;
            }
            int offIdx = peakIdx;
            while (offIdx < n - 1 && Math.abs(excess[offIdx + 1]) > threshold) {
                offIdx++;
            }
            onset = tangentIntersection(tempK, flowW, baseW, onIdx, peakIdx, true);
            endset = tangentIntersection(tempK, flowW, baseW, peakIdx, offIdx, false);
            if (onset == null) onset = tempK[onIdx];
            if (endset == null) endset = tempK[offIdx];
        }

        if (nonUniform(tempK)) {
            diagnostics.add("non-uniform temperature grid: integrated against time with trapezoid rule");
        }
        return new Features(onset, peak, endset, peakFlow, stepHeight, midpoint, specificJG, sign, diagnostics);
    }

    private static double[] tangentSegment(BaselineCandidate b, boolean pre, double junctionK,
                                           double[] tempK, double[] excess) {
        List<Double> vals = new ArrayList<>();
        for (int i = 0; i < tempK.length; i++) {
            boolean left = tempK[i] < junctionK;
            if (left == pre) {
                vals.add(excess[i]);
            }
        }
        return vals.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static double mean(double[] xs) {
        if (xs.length == 0) return 0;
        double s = 0;
        for (double x : xs) s += x;
        return s / xs.length;
    }

    private static Double tangentIntersection(double[] tempK, double[] flowW, double[] baseW,
                                              int edgeIdx, int peakIdx, boolean leading) {
        // tangent at 20% and 80% of the rise, intersected with the local baseline
        int i1, i2;
        if (leading) {
            i1 = edgeIdx;
            i2 = edgeIdx + Math.max(1, (int) Math.round(0.6 * (peakIdx - edgeIdx)));
        } else {
            i1 = peakIdx + Math.max(1, (int) Math.round(0.6 * (edgeIdx - peakIdx)));
            i2 = edgeIdx;
        }
        if (i2 <= i1 || i2 >= tempK.length) return null;
        double m = (flowW[i2] - flowW[i1]) / (tempK[i2] - tempK[i1]);
        double c = flowW[i1] - m * tempK[i1];
        double baseAt = 0.5 * (baseW[i1] + baseW[i2]);
        return (baseAt - c) / m;
    }

    private static ThermalSign decideSign(double[] excess, double energyJ, ExoConvention exo,
                                          List<String> diagnostics) {
        if (exo == ExoConvention.UNKNOWN) {
            diagnostics.add("BLOCKER: exotherm direction convention unknown; sign cannot be decided");
            return ThermalSign.UNDETERMINED;
        }
        double signedEnergy = energyJ;
        boolean endo;
        if (exo == ExoConvention.EXO_UP) {
            // positive raw heat-flow excess => exothermic
            endo = signedEnergy < 0;
        } else {
            endo = signedEnergy > 0;
        }
        return endo ? ThermalSign.ENDOTHERMIC : ThermalSign.EXOTHERMIC;
    }

    private static boolean nonUniform(double[] tempK) {
        if (tempK.length < 3) return false;
        double d0 = tempK[1] - tempK[0];
        for (int i = 2; i < tempK.length; i++) {
            if (Math.abs((tempK[i] - tempK[i - 1]) - d0) > 1e-6 * Math.max(1, Math.abs(d0))) {
                return true;
            }
        }
        return false;
    }

    /** Error budget combining mass/rate versions, calibration and baseline spread. */
    public static EnthalpyErrorSources errorBudget(double massRel, double rateRel,
                                                   double calibK, double spreadJG,
                                                   double samplingJG, List<String> notes) {
        return new EnthalpyErrorSources(massRel, rateRel, calibK, spreadJG, samplingJG, notes);
    }
}
