package dsc;

import dsc.Model.*;
import java.util.*;

/** Pure DSC math. No I/O, fully testable. */
public final class Dsc {
    private Dsc() {}

    /** Direction from the actual temperature trajectory; mixed sign => UNKNOWN, never guessed. */
    public static Direction detectDirection(List<Point> pts) {
        int pos = 0, neg = 0;
        for (int i = 1; i < pts.size(); i++) {
            double dT = pts.get(i).tempC() - pts.get(i - 1).tempC();
            if (dT > 1e-9) pos++;
            else if (dT < -1e-9) neg++;
        }
        if (pos > 0 && neg == 0) return Direction.HEATING;
        if (neg > 0 && pos == 0) return Direction.COOLING;
        return Direction.UNKNOWN;
    }

    /** Points of the segment inside [tLow,tHigh] (temperature coords, direction-agnostic). */
    public static List<Point> inInterval(Segment seg, Interval iv) {
        Interval n = iv.normalized();
        List<Point> out = new ArrayList<>();
        for (Point p : seg.points())
            if (p.tempC() >= n.tLow() && p.tempC() <= n.tHigh()) out.add(p);
        return out;
    }

    /**
     * Baseline candidates for an event interval. All plausible baselines are kept;
     * differences are preserved, never collapsed into a single answer.
     */
    public static List<BaselineCandidate> baselineCandidates(Segment seg, Interval iv) {
        Interval n = iv.normalized();
        List<Point> inside = inInterval(seg, n);
        List<BaselineCandidate> out = new ArrayList<>();
        if (inside.size() < 2) return out;
        Point a = inside.get(0), b = inside.get(inside.size() - 1);
        double y1 = a.heatFlowMw(), y2 = b.heatFlowMw();
        double x1 = a.tempC(), x2 = b.tempC();
        out.add(new BaselineCandidate("linear",
            t -> y1 + (y2 - y1) * (t - x1) / (x2 - x1),
            "straight line between interval endpoints"));
        out.add(new BaselineCandidate("sigmoidal",
            t -> {
                double s = (t - x1) / (x2 - x1);
                return y1 + (y2 - y1) * (s * s * (3 - 2 * s));
            },
            "smoothstep blend, suits step-like glass transitions"));
        double[] leftTan = edgeTangent(inside, true);
        double[] rightTan = edgeTangent(inside, false);
        double lf = leftTan[0], li = leftTan[1];
        double rf = rightTan[0], ri = rightTan[1];
        out.add(new BaselineCandidate("left-tangent",
            t -> li + lf * (t - x1),
            "pre-transition tangent extrapolated across the event"));
        out.add(new BaselineCandidate("right-tangent",
            t -> ri + rf * (t - x2),
            "post-transition tangent extrapolated back across the event"));
        return out;
    }

    /** Least-squares tangent over the first/last 20% of points in the interval. */
    static double[] edgeTangent(List<Point> inside, boolean left) {
        int n = inside.size();
        int k = Math.max(2, n / 5);
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = 0; i < k; i++) {
            Point p = inside.get(left ? i : n - 1 - i);
            sx += p.tempC(); sy += p.heatFlowMw();
            sxx += p.tempC() * p.tempC(); sxy += p.tempC() * p.heatFlowMw();
        }
        double denom = k * sxx - sx * sx;
        double slope = Math.abs(denom) < 1e-12 ? 0 : (k * sxy - sx * sy) / denom;
        double xbar = sx / k, ybar = sy / k;
        return new double[]{slope, ybar - slope * xbar};
    }

    /**
     * Trapezoid integral of (heatFlow - baseline) over time (seconds) using the
     * actual, possibly non-uniform, sample times. Returns mJ (mW * s).
     * Empty interval => Optional.empty (caller records a diagnostic, never fabricates).
     */
    public static Optional<Double> integrateMilliJoule(Segment seg, Interval iv, BaselineFn base) {
        List<Point> pts = inInterval(seg, iv);
        if (pts.size() < 2) return Optional.empty();
        double sum = 0;
        for (int i = 1; i < pts.size(); i++) {
            Point p0 = pts.get(i - 1), p1 = pts.get(i);
            double dt = Math.abs(p1.timeS() - p0.timeS());
            double f0 = p0.heatFlowMw() - base.valueAt(p0.tempC());
            double f1 = p1.heatFlowMw() - base.valueAt(p1.tempC());
            sum += 0.5 * (f0 + f1) * dt;
        }
        return Optional.of(sum / 1000.0); // mW*s = mJ
    }

    /** Signed onset/peak/endset. Sign convention must be explicit; UNKNOWN keeps magnitude only. */
    public static Optional<EventResult> onsetPeakEndset(Segment seg, Interval iv, BaselineFn base,
                                                        Convention conv, double massMg, double massUncMg,
                                                        double calibUncFrac, double baselineSpreadJPerG) {
        List<Point> pts = inInterval(seg, iv);
        if (pts.size() < 3) return Optional.empty();
        // peak: extremum of (HF - baseline)
        int peakIdx = 0;
        double peakAbs = -1;
        double[] dev = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            dev[i] = pts.get(i).heatFlowMw() - base.valueAt(pts.get(i).tempC());
            if (Math.abs(dev[i]) > peakAbs) { peakAbs = Math.abs(dev[i]); peakIdx = i; }
        }
        // steepest leading/trailing tangents around the peak
        double onset = tangentCross(pts, dev, peakIdx, true, base);
        double endset = tangentCross(pts, dev, peakIdx, false, base);
        double peakC = pts.get(peakIdx).tempC();
        Optional<Double> mj = integrateMilliJoule(seg, iv, base);
        if (mj.isEmpty()) return Optional.empty();
        double jPerG = mj.get() / massMg; // mJ/mg == J/g
        double sign = switch (conv) {
            case EXO_UP -> -1.0;   // exotherm plotted up => thermodynamic sign negative
            case ENDO_UP -> 1.0;
            case UNKNOWN -> 1.0;   // magnitude only; diagnostic recorded by caller
        };
        List<String> errors = new ArrayList<>();
        errors.add("mass ±" + massUncMg + " mg -> ±" + pct(massUncMg / massMg));
        errors.add("calibration ±" + pct(calibUncFrac));
        errors.add("baseline spread across candidates ±" + String.format(Locale.ROOT, "%.3f", Math.abs(baselineSpreadJPerG)) + " J/g");
        List<String> diag = new ArrayList<>();
        if (conv == Convention.UNKNOWN)
            diag.add("heat-flow sign convention not declared in instrument metadata; magnitude reported, sign undetermined");
        return Optional.of(new EventResult(onset, peakC, endset, sign * jPerG, "J/g", errors, diag));
    }

    private static String pct(double f) { return String.format(Locale.ROOT, "%.1f%%", Math.abs(f) * 100); }

    /** Intersection of the steepest edge tangent with the baseline, on one side of the peak. */
    private static double tangentCross(List<Point> pts, double[] dev, int peakIdx, boolean leading, BaselineFn base) {
        int lo = leading ? 0 : peakIdx, hi = leading ? peakIdx : pts.size() - 1;
        int best = -1;
        double bestSlope = 0;
        for (int i = Math.max(lo, 1); i <= hi; i++) {
            double dT = pts.get(i).tempC() - pts.get(i - 1).tempC();
            if (Math.abs(dT) < 1e-9) continue;
            double s = (dev[i] - dev[i - 1]) / dT;
            if (best < 0 || Math.abs(s) > Math.abs(bestSlope)) { best = i; bestSlope = s; }
        }
        if (best < 0 || Math.abs(bestSlope) < 1e-12) return pts.get(leading ? lo : hi).tempC();
        Point p = pts.get(best);
        double devAt = p.heatFlowMw() - base.valueAt(p.tempC());
        // tangent line of deviation: dev(T) = devAt + bestSlope*(T - p.T); zero crossing vs baseline
        return p.tempC() - devAt / bestSlope;
    }

    /** Split a raw point stream into segments wherever the scan direction switches (cycle switching). */
    public static List<List<Point>> splitByDirectionSwitch(List<Point> pts) {
        List<List<Point>> segs = new ArrayList<>();
        List<Point> cur = new ArrayList<>();
        int lastSign = 0;
        for (int i = 0; i < pts.size(); i++) {
            if (i > 0) {
                double dT = pts.get(i).tempC() - pts.get(i - 1).tempC();
                int sign = dT > 1e-9 ? 1 : (dT < -1e-9 ? -1 : lastSign);
                if (lastSign != 0 && sign != 0 && sign != lastSign && !cur.isEmpty()) {
                    cur.add(pts.get(i)); // keep switch point in both for continuity
                    segs.add(cur);
                    cur = new ArrayList<>();
                    cur.add(pts.get(i));
                } else {
                    cur.add(pts.get(i));
                }
                if (sign != 0) lastSign = sign;
            } else cur.add(pts.get(i));
        }
        if (!cur.isEmpty()) segs.add(cur);
        return segs;
    }
}
