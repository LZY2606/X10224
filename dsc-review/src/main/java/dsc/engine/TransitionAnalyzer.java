package dsc.engine;

import dsc.domain.Fit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds transitions on a baseline residual: peaks (melting/crystallization)
 * and steps (glass transition). Onset/endset are tangent intersections on the
 * actual (possibly non-uniform) grid. Overlapping peaks are split into separate
 * transitions; each keeps a quality flag.
 */
final class TransitionAnalyzer {

    private TransitionAnalyzer() {
    }

    record Transition(String kind, int peakRow, double peakK, double peakResidualW,
                      Double onsetK, Double endsetK, double heightW,
                      boolean overlapping, double overlapShare,
                      List<Map<String, Object>> diagnostics) {
    }

    static List<Transition> analyze(WindowData w, double[] baseline) {
        List<Transition> out = new ArrayList<>();
        int n = w.tK().length;
        double[] residual = new double[n];
        for (int i = 0; i < n; i++) {
            residual[i] = w.heatFlowW()[i] - baseline[i];
        }
        double[] d = derivative(w.tK(), residual);
        double noise = robustNoise(residual);
        double prominence = Math.max(noise * 4.0, 1e-9);

        // Step (glass transition): a region of sustained derivative with the
        // level before and after differing, i.e. a baseline shift rather than
        // a return-to-zero peak.
        Transition step = detectStep(w, residual, d, prominence);
        if (step != null) {
            out.add(step);
        }

        // Peaks: local extrema of the residual after subtracting a median level.
        double level = median(residual.clone());
        int[] extrema = localExtrema(residual, prominence, level);
        for (int idx : extrema) {
            boolean positive = residual[idx] - level > 0;
            int onsetIdx = tangentCrossing(w.tK(), residual, idx, -1, positive, level);
            int endsetIdx = tangentCrossing(w.tK(), residual, idx, +1, positive, level);
            if (onsetIdx < 0) {
                onsetIdx = positive ? firstAbove(residual, level, 0, idx)
                        : firstBelow(residual, level, 0, idx);
            }
            if (endsetIdx < 0) {
                endsetIdx = positive ? lastAbove(residual, level, idx, n - 1)
                        : lastBelow(residual, level, idx, n - 1);
            }
            boolean overlap = false;
            double share = 1.0;
            List<Map<String, Object>> diags = new ArrayList<>();
            for (int other : extrema) {
                if (other == idx) {
                    continue;
                }
                int lo = Math.min(Math.min(onsetIdx, endsetIdx), other);
                int hi = Math.max(Math.max(onsetIdx, endsetIdx), other);
                boolean sameSign = (residual[other] - level > 0) == positive;
                if (sameSign && Math.abs(other - idx) <= 12) {
                    overlap = true;
                }
            }
            if (overlap) {
                share = overlapShare(residual, idx, extrema, level);
                diags.add(WindowExtractor.diag("OVERLAPPING_TRANSITION",
                        "transition overlaps another same-sign extremum; "
                                + "per-transition enthalpy uses proportional area split"));
            }
            out.add(new Transition(
                    positive ? "peak_endo" : "peak_exo",
                    w.rows()[idx], w.tK()[idx], residual[idx],
                    onsetIdx >= 0 ? w.tK()[onsetIdx] : null,
                    endsetIdx >= 0 ? w.tK()[endsetIdx] : null,
                    residual[idx] - level, overlap, share, diags));
        }
        if (extrema.length == 0 && step == null) {
            // nothing found; caller records EMPTY-like diagnostic at window level
        }
        return out;
    }

    private static double[] derivative(double[] x, double[] y) {
        int n = x.length;
        double[] d = new double[n];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1);
            int b = Math.min(n - 1, i + 1);
            double dx = x[b] - x[a];
            d[i] = Math.abs(dx) < 1e-12 ? 0.0 : (y[b] - y[a]) / dx;
        }
        return d;
    }

    private static double robustNoise(double[] residual) {
        double[] copy = residual.clone();
        double med = median(copy);
        double[] dev = new double[copy.length];
        for (int i = 0; i < copy.length; i++) {
            dev[i] = Math.abs(copy[i] - med);
        }
        return median(dev) * 1.4826;
    }

    private static double median(double[] a) {
        java.util.Arrays.sort(a);
        int mid = a.length / 2;
        if (a.length == 0) {
            return 0.0;
        }
        return a.length % 2 == 0 ? 0.5 * (a[mid - 1] + a[mid]) : a[mid];
    }

    private static int[] localExtrema(double[] r, double prominence, double level) {
        List<Integer> out = new ArrayList<>();
        for (int i = 2; i < r.length - 2; i++) {
            boolean max = r[i] > r[i - 1] && r[i] >= r[i + 1]
                    && r[i] - level > prominence;
            boolean min = r[i] < r[i - 1] && r[i] <= r[i + 1]
                    && level - r[i] > prominence;
            if (max || min) {
                // suppress a weaker neighbour within a 3-point window
                if (!out.isEmpty() && i - out.get(out.size() - 1) < 3) {
                    int prev = out.get(out.size() - 1);
                    if (Math.abs(r[i] - level) > Math.abs(r[prev] - level)) {
                        out.set(out.size() - 1, i);
                    }
                    continue;
                }
                out.add(i);
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Onset/endset: fit a tangent on the steepest flank (window of 5 points
     * around max |d|) and intersect with the fitted level line of the local
     * shoulder.
     */
    private static int tangentCrossing(double[] x, double[] r, int peak, int dir,
                                       boolean positive, double level) {
        int n = x.length;
        int edge = dir < 0 ? 0 : n - 1;
        int steep = peak;
        double best = 0.0;
        for (int i = peak; dir < 0 ? i > 1 : i < n - 2; i += dir) {
            double slope = (r[i] - r[i - dir]) / Math.max(1e-12, x[i] - x[i - dir]);
            double mag = Math.abs(slope);
            if (mag > best) {
                best = mag;
                steep = i;
            }
            if ((dir < 0 && i <= 1) || (dir > 0 && i >= n - 2)) {
                break;
            }
        }
        int a = Math.max(0, steep - 2);
        int b = Math.min(n - 1, steep + 2);
        double[] tx = new double[b - a + 1];
        double[] ty = new double[tx.length];
        for (int i = a; i <= b; i++) {
            tx[i - a] = x[i];
            ty[i - a] = r[i];
        }
        Fit.Line tangent = Fit.linear(tx, ty);
        int shoulderLo = dir < 0 ? edge : Math.min(n - 1, peak + 2);
        int shoulderHi = dir < 0 ? Math.max(0, peak - 2) : edge;
        double[] sx = new double[Math.abs(shoulderHi - shoulderLo) + 1];
        double[] sy = new double[sx.length];
        int k = 0;
        for (int i = Math.min(shoulderLo, shoulderHi);
             i <= Math.max(shoulderLo, shoulderHi) && k < sx.length; i++) {
            sx[k] = x[i];
            sy[k] = r[i];
            k++;
        }
        if (sx.length < 2) {
            return -1;
        }
        Fit.Line shoulder = Fit.linear(sx, sy);
        double denom = tangent.slope() - shoulder.slope();
        if (Math.abs(denom) < 1e-15) {
            return -1;
        }
        double crossX = (shoulder.intercept() - tangent.intercept()) / denom;
        int bestIdx = -1;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            double dist = Math.abs(x[i] - crossX);
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private static Transition detectStep(WindowData w, double[] residual, double[] d,
                                         double prominence) {
        int n = residual.length;
        if (n < 8) {
            return null;
        }
        int lead = Math.max(3, n / 8);
        int tail = Math.max(3, n / 8);
        double before = avg(residual, 0, lead);
        double after = avg(residual, n - tail, n);
        double stepHeight = after - before;
        if (Math.abs(stepHeight) < prominence * 1.5) {
            return null;
        }
        double maxD = 0.0;
        int steep = n / 2;
        for (int i = lead; i < n - tail; i++) {
            if (Math.abs(d[i]) > maxD) {
                maxD = Math.abs(d[i]);
                steep = i;
            }
        }
        // A step must be monotone across the steep region and level outside.
        boolean mono = true;
        int sign = (int) Math.signum(stepHeight);
        for (int i = steep - 1; i <= steep + 1 && i < n - 1; i++) {
            if (i < 1) {
                continue;
            }
            if (Math.signum(residual[i] - residual[i - 1]) != sign
                    && Math.abs(residual[i] - residual[i - 1]) > 1e-12) {
                mono = false;
            }
        }
        if (!mono) {
            return null;
        }
        List<Map<String, Object>> diags = new ArrayList<>();
        diags.add(WindowExtractor.diag("GLASS_TRANSITION_STEP",
                "sustained baseline shift interpreted as Tg step; "
                        + "midpoint, onset and endset are reported"));
        return new Transition("glass_step", w.rows()[steep], w.tK()[steep],
                stepHeight, w.tK()[Math.max(0, steep - 2)],
                w.tK()[Math.min(n - 1, steep + 2)], stepHeight, false, 1.0, diags);
    }

    private static double avg(double[] a, int from, int to) {
        double s = 0.0;
        int c = 0;
        for (int i = from; i < to; i++) {
            s += a[i];
            c++;
        }
        return c == 0 ? 0.0 : s / c;
    }

    private static int firstAbove(double[] r, double level, int from, int to) {
        for (int i = from; i < to; i++) {
            if (r[i] > level) {
                return i;
            }
        }
        return -1;
    }

    private static int firstBelow(double[] r, double level, int from, int to) {
        for (int i = from; i < to; i++) {
            if (r[i] < level) {
                return i;
            }
        }
        return -1;
    }

    private static int lastAbove(double[] r, double level, int from, int to) {
        for (int i = to; i > from; i--) {
            if (r[i] > level) {
                return i;
            }
        }
        return -1;
    }

    private static int lastBelow(double[] r, double level, int from, int to) {
        for (int i = to; i > from; i--) {
            if (r[i] < level) {
                return i;
            }
        }
        return -1;
    }

    private static double overlapShare(double[] r, int idx, int[] all, double level) {
        double mine = Math.abs(r[idx] - level);
        double total = 0.0;
        for (int o : all) {
            total += Math.abs(r[o] - level);
        }
        return total <= 0 ? 1.0 : mine / total;
    }
}
