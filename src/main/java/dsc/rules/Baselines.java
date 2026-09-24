package dsc.rules;

import dsc.model.Model.BaselineEval;
import dsc.model.Model.Convention;
import dsc.model.Model.Direction;
import dsc.model.Model.Point;

import java.util.ArrayList;
import java.util.List;

/**
 * Baseline candidates and peak integration.
 *
 * Semantics held here:
 *  - multiple plausible baselines are computed as parallel candidates; none is silently dropped;
 *  - integration is trapezoidal over TIME, so non-uniform temperature spacing is exact;
 *  - cooling segments (reverse heat flow relative to temperature axis) integrate over the
 *    same positive time axis; the sign of the enthalpy follows the instrument convention,
 *    never a file-name guess;
 *  - an empty interval yields no numbers, only a diagnostic (handled by the engine).
 */
public final class Baselines {
    private Baselines() {}

    public record Line(double slope, double intercept) {
        public double at(double t) { return slope * t + intercept; }
    }

    /** Candidate kinds kept side by side. */
    public static final String LINEAR = "LINEAR";
    public static final String TANGENT = "TANGENT";
    public static final String SIGMOIDAL = "SIGMOIDAL";

    /** Least-squares line over a point window (x = temperature). */
    public static Line fitLine(List<Point> pts) {
        int n = pts.size();
        if (n == 0) return new Line(0, 0);
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (Point p : pts) {
            sx += p.tempRaw();
            sy += p.heatFlowRaw();
            sxx += p.tempRaw() * p.tempRaw();
            sxy += p.tempRaw() * p.heatFlowRaw();
        }
        double denom = n * sxx - sx * sx;
        if (Math.abs(denom) < 1e-12) return new Line(0, sy / n);
        double slope = (n * sxy - sx * sy) / denom;
        return new Line(slope, (sy - slope * sx) / n);
    }

    /** Edge lines fitted on the first/last fifth of the window (or user-supplied tangents). */
    public static Line[] edgeLines(List<Point> window, Line userLeft, Line userRight) {
        int n = window.size();
        int k = Math.max(2, n / 5);
        Line left = userLeft != null ? userLeft : fitLine(window.subList(0, Math.min(k, n)));
        Line right = userRight != null ? userRight
                : fitLine(window.subList(Math.max(0, n - k), n));
        return new Line[]{left, right};
    }

    public static double baselineValue(String kind, double t, double t0, double t1,
                                       Line left, Line right) {
        switch (kind) {
            case LINEAR -> {
                double y0 = left.at(t0);
                double y1 = right.at(t1);
                double frac = (t1 - t0) == 0 ? 0 : (t - t0) / (t1 - t0);
                return y0 + frac * (y1 - y0);
            }
            case TANGENT -> {
                double denom = (right.slope() - left.slope());
                double tx = Math.abs(denom) < 1e-12 ? (t0 + t1) / 2
                        : (left.intercept() - right.intercept()) / denom;
                if (tx < t0) tx = t0;
                if (tx > t1) tx = t1;
                return t <= tx ? left.at(t) : right.at(t);
            }
            case SIGMOIDAL -> {
                double frac = (t1 - t0) == 0 ? 0 : (t - t0) / (t1 - t0);
                double s = frac * frac * (3 - 2 * frac); // smoothstep
                return left.at(t) * (1 - s) + right.at(t) * s;
            }
            default -> throw new IllegalArgumentException("unknown baseline kind " + kind);
        }
    }

    /**
     * Analyze one event window against one baseline candidate.
     * enthalpy sign: with ENDO_UP, endothermic deviation is positive; with EXO_UP it is negative.
     * The convention is an input, never guessed.
     */
    public static BaselineEval analyze(String candidateId, String kind, List<Point> window,
                                       double t0, double t1, Line left, Line right,
                                       Direction direction, Convention convention,
                                       double calibrationOffsetC) {
        if (window.isEmpty()) throw new IllegalArgumentException("empty window");
        // Noise estimate from the first fifth of the window residuals vs the left edge line.
        int k = Math.max(2, window.size() / 5);
        double ss = 0;
        List<Point> head = window.subList(0, Math.min(k, window.size()));
        for (Point p : head) {
            double r = p.heatFlowRaw() - left.at(p.tempRaw());
            ss += r * r;
        }
        double noise = Math.sqrt(ss / head.size());
        double threshold = Math.max(3 * noise, 1e-9);

        int n = window.size();
        double[] dev = new double[n];
        for (int i = 0; i < n; i++) {
            Point p = window.get(i);
            dev[i] = p.heatFlowRaw() - baselineValue(kind, p.tempRaw(), t0, t1, left, right);
        }
        // Peak: extremum of deviation. "Up" follows the convention; for EXO_UP the exothermic
        // direction is plotted up, so the dominant peak is the maximum there too only after
        // sign normalization. We report the deviation with the largest magnitude and keep the
        // sign, plus the convention, so direction is reconstructible.
        int peakIdx = 0;
        for (int i = 1; i < n; i++) {
            if (Math.abs(dev[i]) > Math.abs(dev[peakIdx])) peakIdx = i;
        }
        double peak = window.get(peakIdx).tempRaw();

        // Onset/endset: first/last index where |dev| exceeds threshold.
        int onsetIdx = 0;
        while (onsetIdx < n - 1 && Math.abs(dev[onsetIdx]) <= threshold) onsetIdx++;
        int endsetIdx = n - 1;
        while (endsetIdx > 0 && Math.abs(dev[endsetIdx]) <= threshold) endsetIdx--;
        double onset = window.get(onsetIdx).tempRaw();
        double endset = window.get(endsetIdx).tempRaw();

        // Enthalpy: trapezoid of deviation over TIME (seconds) -> mJ. Exact for non-uniform
        // temperature spacing; reverse heat flow (cooling) integrates over the same dt>0.
        double enthalpy = 0;
        for (int i = 1; i < n; i++) {
            double dt = window.get(i).timeS() - window.get(i - 1).timeS();
            enthalpy += 0.5 * (dev[i] + dev[i - 1]) * dt;
        }

        // Glass-transition step: baseline step height between edge lines at onset/endset.
        double deltaCp = right.at(endset) - left.at(onset);

        List<String> errors = new ArrayList<>();
        errors.add("baseline_choice:" + kind);
        errors.add("noise_estimate_mW:" + String.format(java.util.Locale.ROOT, "%.6f", noise));
        errors.add("calibration_offset_C:" + String.format(java.util.Locale.ROOT, "%.4f", calibrationOffsetC));
        if (direction == Direction.COOLING) errors.add("reverse_heat_flow:cooling_segment");
        if (convention == Convention.AMBIGUOUS) errors.add("convention:AMBIGUOUS");
        return new BaselineEval(candidateId, kind, onset, peak, endset, enthalpy, deltaCp,
                errors.toArray(new String[0]));
    }
}
