package dsc.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes onset / peak / endset from a working curve and a fitted baseline.
 *
 * Canonical sign: positive heat flow is endothermic. Melting therefore gives a
 * positive residual; crystallization a negative residual. Peak direction is taken
 * from the declared transition type, never from the file name.
 */
public final class TransitionEvaluator {

    private TransitionEvaluator() {}

    public record Geometry(
            TransitionMetrics metrics,
            double[] residualW,
            int[] indices,
            double areaJ,
            double areaSign
    ) {}

    public static Geometry evaluate(WorkingCurve curve, AnalysisSpec spec, BaselineModel model) {
        List<Integer> idxList = new ArrayList<>();
        for (int i = 0; i < curve.length(); i++) {
            if (spec.interval().covers(curve.t(i))) {
                idxList.add(i);
            }
        }
        int[] indices = idxList.stream().mapToInt(Integer::intValue).toArray();
        double[] residual = new double[indices.length];
        for (int k = 0; k < indices.length; k++) {
            residual[k] = curve.hf(indices[k]) - BaselineFitter.at(model, indices[k]);
        }

        if (spec.type() == TransitionType.GLASS || "step".equals(model.kind())) {
            double tg = BaselineFitter.midpointK(model);
            double height = BaselineFitter.stepHeightW(model);
            TransitionMetrics m = new TransitionMetrics(
                    spec.analysisId() + "#metrics",
                    "step", Double.NaN, tg, Double.NaN, tg,
                    Double.NaN, indices.length >= 6,
                    indices.length < 6 ? "点数不足，S 形拟合不可靠" : "");
            return new Geometry(m, residual, indices, 0, 0);
        }

        int wantSign = spec.type() == TransitionType.MELTING ? +1 : -1;
        int peakIdx = -1;
        double best = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < residual.length; k++) {
            double v = wantSign * residual[k];
            if (v > best) {
                best = v;
                peakIdx = k;
            }
        }

        int onsetIdx = tangentIntersection(curve, indices, residual, peakIdx, -1, wantSign);
        int endsetIdx = tangentIntersection(curve, indices, residual, peakIdx, +1, wantSign);

        double onsetK = onsetIdx >= 0 ? curve.t(indices[onsetIdx]) : Double.NaN;
        double endsetK = endsetIdx >= 0 ? curve.t(indices[endsetIdx]) : Double.NaN;
        double peakK = peakIdx >= 0 ? curve.t(indices[peakIdx]) : Double.NaN;

        double[] area = signedArea(curve, indices, residual);
        TransitionMetrics m = new TransitionMetrics(
                spec.analysisId() + "#metrics",
                "peak", onsetK, peakK, endsetK, Double.NaN, Double.NaN,
                peakIdx >= 0,
                (onsetIdx < 0 || endsetIdx < 0) ? "切线交点落在区间外" : "");
        return new Geometry(m, residual, indices, area[0], area[1]);
    }

    /**
     * Finds the intersection of the steepest tangent on the chosen flank with the
     * zero-residual baseline. {@code dir} -1 scans left (onset), +1 right (endset).
     */
    private static int tangentIntersection(WorkingCurve curve, int[] indices,
                                           double[] residual, int peakIdx,
                                           int dir, int wantSign) {
        if (peakIdx < 0) {
            return -1;
        }
        int flankStart = dir < 0 ? 0 : peakIdx;
        int flankEnd = dir < 0 ? peakIdx : indices.length - 1;
        int steepest = -1;
        double steepestSlope = Double.NEGATIVE_INFINITY;
        int lo = Math.min(flankStart + 1, indices.length - 1);
        int hi = dir < 0 ? flankEnd : Math.max(flankEnd - 1, lo);
        for (int k = lo; k <= hi; k++) {
            double dt = curve.t(indices[k]) - curve.t(indices[k - 1]);
            if (Math.abs(dt) < 1e-12) {
                continue;
            }
            double slope = wantSign * (residual[k] - residual[k - 1]) / dt;
            if (slope > steepestSlope) {
                steepestSlope = slope;
                steepest = k;
            }
        }
        if (steepest <= 0) {
            return -1;
        }
        double x1 = curve.t(indices[steepest]);
        double y1 = wantSign * residual[steepest];
        double x0 = curve.t(indices[steepest - 1]);
        double y0 = wantSign * residual[steepest - 1];
        double slope = (y1 - y0) / Math.max(1e-12, x1 - x0);
        if (Math.abs(slope) < 1e-12) {
            return -1;
        }
        double xCross = x1 - y1 / slope;
        return nearestIndex(curve, indices, xCross);
    }

    private static int nearestIndex(WorkingCurve curve, int[] indices, double tK) {
        int best = -1;
        double bd = Double.POSITIVE_INFINITY;
        for (int k = 0; k < indices.length; k++) {
            double d = Math.abs(curve.t(indices[k]) - tK);
            if (d < bd) {
                bd = d;
                best = k;
            }
        }
        return best;
    }

    /**
     * Trapezoidal integral of residual over time: A = ∫(hf - baseline) dt.
     * Returns [area with observed sign, sign multiplier for the declared event].
     * Works for both uniform and non-uniform temperature sampling.
     */
    private static double[] signedArea(WorkingCurve curve, int[] indices, double[] residual) {
        double area = 0;
        for (int k = 1; k < indices.length; k++) {
            double dt = curve.raw().dtS()[indices[k]] - curve.raw().dtS()[indices[k - 1]];
            if (!Double.isFinite(dt) || dt < 0) {
                double rateGuess = 0.1667;
                dt = Math.abs(curve.t(indices[k]) - curve.t(indices[k - 1])) / rateGuess;
            }
            area += 0.5 * (residual[k] + residual[k - 1]) * dt;
        }
        double sgn = area >= 0 ? +1 : -1;
        return new double[]{area, sgn};
    }
}
