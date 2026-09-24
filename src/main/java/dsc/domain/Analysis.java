package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * DSC baseline / phase-transition math. All quantities carry their coordinate
 * basis: x is temperature (C), integration is over time (s), heat flow is in
 * the run's raw unit normalized only in the derived layer (mW/mg).
 */
public class Analysis {

    /** A baseline candidate: y = f(tempC). */
    public static class Baseline {
        public String type;          // linear | sigmoidal
        public double[] params;      // linear: [a,b] y=a*T+b ; sigmoidal: [aL,bL,aR,bR,mid,steep]
        public double intervalT0;
        public double intervalT1;

        public double eval(double tempC) {
            if ("sigmoidal".equals(type)) {
                double yL = params[0] * tempC + params[1];
                double yR = params[2] * tempC + params[3];
                double w = 1.0 / (1.0 + Math.exp(-(tempC - params[4]) / params[5]));
                return yL * (1 - w) + yR * w;
            }
            return params[0] * tempC + params[1];
        }
    }

    public static class Transition {
        public double onsetTempC;
        public double peakTempC;
        public double endsetTempC;
        public double onsetTimeS;
        public double endsetTimeS;
        public double enthalpy;      // in yUnit * s  (mJ/mg == J/g when normalized)
        public String enthalpyUnit;
        public int peakDirection;    // +1 upward, -1 downward in raw coordinates
        public List<String> errorSources = new ArrayList<>();
        public double enthalpyError;
    }

    /** Least-squares line fit y = a*x + b over the given index range. */
    public static double[] fitLine(List<double[]> pts, int from, int to, int xCol, int yCol) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = 0;
        for (int i = from; i <= to && i < pts.size(); i++) {
            double x = pts.get(i)[xCol], y = pts.get(i)[yCol];
            sx += x; sy += y; sxx += x * x; sxy += x * y; n++;
        }
        if (n < 2) return new double[]{0, n == 1 ? sy : 0};
        double denom = n * sxx - sx * sx;
        if (Math.abs(denom) < 1e-12) return new double[]{0, sy / n};
        double a = (n * sxy - sx * sy) / denom;
        double b = (sy - a * sx) / n;
        return new double[]{a, b};
    }

    /**
     * Builds baseline candidates for a user interval [t0, t1] in time (s).
     * Edge windows (outside the interval, 15% of its width each side) anchor
     * the tangents. Multiple plausible baselines are returned together; the
     * caller must keep them all rather than collapse to one answer.
     */
    public static List<Baseline> baselineCandidates(List<double[]> pts, double t0, double t1) {
        List<Baseline> out = new ArrayList<>();
        double width = t1 - t0;
        double edge = Math.max(width * 0.15, 1e-6);
        int l0 = firstIndexAtOrAfter(pts, t0 - edge), l1 = lastIndexBefore(pts, t0);
        int r0 = firstIndexAtOrAfter(pts, t1), r1 = lastIndexBefore(pts, t1 + edge);
        if (l1 < l0 || r1 < r0) return out; // not enough anchor data; caller emits diagnostic

        double[] left = fitLine(pts, l0, l1, 1, 2);
        double[] right = fitLine(pts, r0, r1, 1, 2);

        Baseline lin = new Baseline();
        lin.type = "linear";
        // straight line through the two edge-window centroids
        double[] cL = centroid(pts, l0, l1), cR = centroid(pts, r0, r1);
        double a = (cR[1] - cL[1]) / Math.max(cR[0] - cL[0], 1e-9);
        lin.params = new double[]{a, cL[1] - a * cL[0]};
        lin.intervalT0 = t0; lin.intervalT1 = t1;
        out.add(lin);

        Baseline sig = new Baseline();
        sig.type = "sigmoidal";
        double midTemp = (tempAt(pts, t0) + tempAt(pts, t1)) / 2.0;
        double steep = Math.max((tempAt(pts, t1) - tempAt(pts, t0)) / 8.0, 0.5);
        sig.params = new double[]{left[0], left[1], right[0], right[1], midTemp, steep};
        sig.intervalT0 = t0; sig.intervalT1 = t1;
        out.add(sig);
        return out;
    }

    private static double[] centroid(List<double[]> pts, int from, int to) {
        double sx = 0, sy = 0; int n = 0;
        for (int i = from; i <= to && i < pts.size(); i++) { sx += pts.get(i)[1]; sy += pts.get(i)[2]; n++; }
        return n == 0 ? new double[]{0, 0} : new double[]{sx / n, sy / n};
    }

    public static int firstIndexAtOrAfter(List<double[]> pts, double t) {
        for (int i = 0; i < pts.size(); i++) if (pts.get(i)[0] >= t) return i;
        return pts.size();
    }

    public static int lastIndexBefore(List<double[]> pts, double t) {
        for (int i = pts.size() - 1; i >= 0; i--) if (pts.get(i)[0] < t) return i;
        return -1;
    }

    public static double tempAt(List<double[]> pts, double t) {
        int i = firstIndexAtOrAfter(pts, t);
        if (i >= pts.size()) return pts.get(pts.size() - 1)[1];
        return pts.get(i)[1];
    }

    /**
     * Integrates (y - baseline) over time within [t0, t1] using the trapezoid
     * rule on the ACTUAL sample times, so non-uniform temperature spacing is
     * handled exactly. Returns null-derived Transition list; empty intervals
     * yield an empty list and the caller records an EMPTY_INTERVAL diagnostic.
     */
    public static List<Transition> transitions(
            List<double[]> pts, Baseline baseline, double t0, double t1,
            double yScale, String yUnit, double massUncertaintyRel) {
        List<Transition> out = new ArrayList<>();
        int i0 = firstIndexAtOrAfter(pts, t0);
        int i1 = lastIndexBefore(pts, t1 + 1e-9);
        if (i1 <= i0) return out; // empty interval: no fabricated values

        // residual series
        int n = i1 - i0 + 1;
        double[] res = new double[n];
        double[] time = new double[n];
        double[] temp = new double[n];
        for (int k = 0; k < n; k++) {
            double[] p = pts.get(i0 + k);
            time[k] = p[0];
            temp[k] = p[1];
            res[k] = (p[2] - baseline.eval(p[1])) * yScale;
        }

        // peak detection on the residual: every significant local extremum is
        // kept, so overlapping transitions produce multiple candidates.
        List<int[]> peakSpans = findPeakSpans(res);
        for (int[] span : peakSpans) {
            Transition tr = new Transition();
            int p = span[0];
            tr.peakTempC = temp[p];
            tr.peakDirection = res[p] >= 0 ? 1 : -1;
            // onset / endset: walk from the peak to where the residual falls
            // below 2% of its amplitude (tangent-intersection approximation).
            double amp = Math.abs(res[p]);
            double thresh = amp * 0.02;
            int a = p;
            while (a > 0 && Math.abs(res[a]) > thresh) a--;
            int b = p;
            while (b < n - 1 && Math.abs(res[b]) > thresh) b++;
            tr.onsetTempC = temp[a];
            tr.endsetTempC = temp[b];
            tr.onsetTimeS = time[a];
            tr.endsetTimeS = time[b];
            // trapezoid over real sample times between onset and endset
            double area = 0;
            for (int k = a; k < b; k++) {
                area += 0.5 * (res[k] + res[k + 1]) * (time[k + 1] - time[k]);
            }
            tr.enthalpy = area;
            tr.enthalpyUnit = yUnit + "*s";
            // error sources, each machine-readable
            double noise = residualNoise(pts, baseline, t0, t1, yScale);
            double noiseErr = noise * (tr.endsetTimeS - tr.onsetTimeS);
            tr.errorSources.add("baseline-noise:" + fmt(noiseErr) + ":" + tr.enthalpyUnit);
            if (massUncertaintyRel > 0) {
                double massErr = Math.abs(area) * massUncertaintyRel;
                tr.errorSources.add("sample-mass:" + fmt(massErr) + ":" + tr.enthalpyUnit);
            }
            tr.enthalpyError = tr.errorSources.stream()
                .mapToDouble(s -> Double.parseDouble(s.split(":")[1])).sum();
            out.add(tr);
        }
        return out;
    }

    /** Enthalpy difference between two baselines over the same interval. */
    public static double baselineSpread(List<double[]> pts, Baseline x, Baseline y,
                                        double t0, double t1, double yScale) {
        int i0 = firstIndexAtOrAfter(pts, t0);
        int i1 = lastIndexBefore(pts, t1 + 1e-9);
        if (i1 <= i0) return 0;
        double area = 0;
        for (int i = i0; i < i1; i++) {
            double[] p0 = pts.get(i), p1 = pts.get(i + 1);
            double d0 = (x.eval(p0[1]) - y.eval(p0[1])) * yScale;
            double d1 = (x.eval(p1[1]) - y.eval(p1[1])) * yScale;
            area += 0.5 * (d0 + d1) * (p1[0] - p0[0]);
        }
        return Math.abs(area);
    }

    private static double residualNoise(List<double[]> pts, Baseline b, double t0, double t1, double yScale) {
        double width = t1 - t0;
        double edge = Math.max(width * 0.15, 1e-6);
        double sum = 0; int n = 0;
        int l0 = firstIndexAtOrAfter(pts, t0 - edge), l1 = lastIndexBefore(pts, t0);
        int r0 = firstIndexAtOrAfter(pts, t1), r1 = lastIndexBefore(pts, t1 + edge);
        for (int i = Math.max(l0, 0); i <= l1 && i < pts.size(); i++) {
            sum += Math.abs(pts.get(i)[2] - b.eval(pts.get(i)[1])) * yScale; n++;
        }
        for (int i = Math.max(r0, 0); i <= r1 && i < pts.size(); i++) {
            sum += Math.abs(pts.get(i)[2] - b.eval(pts.get(i)[1])) * yScale; n++;
        }
        return n == 0 ? 0 : sum / n;
    }

    private static List<int[]> findPeakSpans(double[] res) {
        List<int[]> spans = new ArrayList<>();
        int n = res.length;
        if (n < 5) return spans;
        // smooth lightly to avoid noise-driven splits
        double[] sm = new double[n];
        for (int i = 0; i < n; i++) {
            double acc = 0; int c = 0;
            for (int k = Math.max(0, i - 2); k <= Math.min(n - 1, i + 2); k++) { acc += res[k]; c++; }
            sm[i] = acc / c;
        }
        double maxAbs = 0;
        for (double v : sm) maxAbs = Math.max(maxAbs, Math.abs(v));
        double minAmp = maxAbs * 0.15;
        for (int i = 1; i < n - 1; i++) {
            boolean localMax = sm[i] >= sm[i - 1] && sm[i] > sm[i + 1] && sm[i] > minAmp;
            boolean localMin = sm[i] <= sm[i - 1] && sm[i] < sm[i + 1] && sm[i] < -minAmp;
            if (localMax || localMin) {
                // avoid duplicates within 3 samples
                if (!spans.isEmpty() && i - spans.get(spans.size() - 1)[0] < 3) continue;
                spans.add(new int[]{i});
            }
        }
        return spans;
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.6g", v);
    }
}
