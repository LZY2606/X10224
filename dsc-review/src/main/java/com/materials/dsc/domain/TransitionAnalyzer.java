package com.materials.dsc.domain;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.materials.dsc.model.BaselineCandidate;
import com.materials.dsc.model.Qty;
import com.materials.dsc.model.Transition;

import java.util.ArrayList;
import java.util.List;

/**
 * Onset/peak/endset and enthalpy for a chosen baseline.  Heat flow is
 * endo-up (melting positive, crystallisation negative) after SeriesBuilder;
 * the sign is never inferred from the file name.  Overlapping events are
 * detected and kept separate instead of being merged.
 */
public final class TransitionAnalyzer {

    public static final class Peak {
        int center, onset, endset;
        double areaMj;
        boolean overlap;
    }

    private TransitionAnalyzer() {
    }

    public static List<Transition> analyze(String analysisId, BaselineCandidate base,
                                           double scanRateKPerMin, Qty mass,
                                           double calOffsetC, String calSourceId, int calVersion,
                                           boolean nonUniform, int skippedPoints) {
        List<Transition> out = new ArrayList<>();
        double[] t = base.lineT;
        double[] y = new double[t.length];
        int n = t.length;
        for (int i = 0; i < n; i++) {
            y[i] = valueAt(base, t, i) - base.lineY[i];
        }
        double dt = scanRateKPerMin / 60.0;

        List<int[]> extrema = new ArrayList<>();
        for (int i = 1; i < n - 1; i++) {
            if ((y[i] > y[i - 1] && y[i] >= y[i + 1])
                    || (y[i] < y[i - 1] && y[i] <= y[i + 1])) {
                if (Math.abs(y[i]) > prominence(t, y, i)) {
                    extrema.add(new int[]{i, y[i] >= 0 ? 1 : -1});
                }
            }
        }
        // merge same-sign extrema separated by a shallow valley
        List<Peak> peaks = new ArrayList<>();
        for (int k = 0; k < extrema.size(); k++) {
            int idx = extrema.get(k)[0];
            int pol = extrema.get(k)[1];
            Peak p = new Peak();
            p.center = idx;
            int j = idx - 1;
            while (j >= 0 && pol * y[j] >= 0) {
                j--;
            }
            p.onset = j + 1;
            j = idx + 1;
            while (j < n && pol * y[j] >= 0) {
                j++;
            }
            p.endset = Math.min(n - 1, j);
            peaks.add(p);
        }
        // overlap: opposite-sign or second same-sign center lies within bounds
        for (int a = 0; a < peaks.size(); a++) {
            for (int b = a + 1; b < peaks.size(); b++) {
                Peak pa = peaks.get(a);
                Peak pb = peaks.get(b);
                if (pb.center <= pa.endset + 1) {
                    pa.overlap = true;
                    pb.overlap = true;
                }
            }
        }
        for (int rank = 0; rank < peaks.size(); rank++) {
            Peak p = peaks.get(rank);
            p.areaMj = integrate(t, y, p, dt, p.overlap);
            double onsetT = t[p.onset];
            double peakT = t[p.center];
            double endsetT = t[p.endset];
            String type = y[p.center] > 0 ? "melting/endotherm" : "crystallisation/exotherm";
            Double jg = null;
            if (mass.usable() && mass.value > 0) {
                jg = p.areaMj / mass.value;
            }
            String id = "tr_" + Hashes.stable(analysisId, base.id, Integer.toString(rank));
            Transition tr = new Transition(id, analysisId, type, onsetT, peakT, endsetT,
                    p.areaMj, jg, 0.0, base.id, "active");
            ArrayNode errors = Errors.forTransition(base, jg == null ? p.areaMj / 1000.0 : jg,
                    mass, rateFor(scanRateKPerMin), calOffsetC, calSourceId, calVersion,
                    false, p.overlap, nonUniform, skippedPoints);
            tr.detail.set("errorSources", errors);
            tr.detail.put("overlap", p.overlap);
            tr.detail.put("polarity", y[p.center] > 0 ? "endotherm(+)" : "exotherm(-)");
            tr.detail.put("windowSampleCount", p.endset - p.onset + 1);
            out.add(tr);
        }

        // glass transition from a step baseline (one Tg per step candidate)
        if ("step".equals(base.method) && base.metrics.has("stepTemperatureC")) {
            double stepMw = base.metrics.path("stepMw").asDouble();
            int mid = indexNearest(t, base.metrics.path("stepTemperatureC").asDouble());
            double onsetT = t[Math.max(0, mid - 2)];
            double endsetT = t[Math.min(n - 1, mid + 2)];
            double cpStep = dt > 0 ? (stepMw / 1000.0) / dt / (mass.usable() ? mass.value : 1.0)
                    : Double.NaN;
            String id = "tr_" + Hashes.stable(analysisId, base.id, "tg");
            Transition tg = new Transition(id, analysisId, "glass-transition",
                    onsetT, t[mid], endsetT, 0.0, 0.0, Double.isNaN(cpStep) ? 0 : cpStep,
                    base.id, "active");
            ArrayNode errors = Errors.forTransition(base, 0.0, mass, rateFor(scanRateKPerMin),
                    calOffsetC, calSourceId, calVersion, false, false, nonUniform, skippedPoints);
            tg.detail.set("errorSources", errors);
            tg.detail.put("stepMw", Math.round(stepMw * 1e6) / 1e6);
            tg.detail.put("stepBasis", "two-level step fit; Tg at step midpoint");
            out.add(tg);
        }
        return out;
    }

    private static double valueAt(BaselineCandidate base, double[] t, int i) {
        return base.lineY.length == t.length ? rawAt(base, i) : Double.NaN;
    }

    private static java.util.Map<String, double[]> rawCache = new java.util.HashMap<>();

    private static double rawAt(BaselineCandidate base, int i) {
        // The corrected curve is evaluated on the baseline samples themselves;
        // BaselineEngine builds lines on exactly the inside points, and the
        // original signal is carried via BaselineCandidate.metrics.signalY.
        double[] sig = rawCache.get(base.id);
        return sig == null ? base.lineY[i] : sig[i];
    }

    static void attachSignal(BaselineCandidate base, double[] signalY) {
        rawCache.put(base.id, signalY);
    }

    private static double prominence(double[] t, double[] y, int i) {
        double local = 0.02 * range(y);
        return Math.max(local, 1e-6);
    }

    private static double range(double[] y) {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double v : y) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        return hi - lo;
    }

    private static double integrate(double[] t, double[] y, Peak p, double dt, boolean overlap) {
        double acc = 0;
        for (int i = p.onset; i < p.endset; i++) {
            double dT = t[i + 1] - t[i];
            double mid = (y[i] + y[i + 1]) / 2.0;
            double seconds = dt > 0 ? dT / dt : 0;
            double piece = mid * seconds; // mW * s = mJ
            if (overlap && i < p.endset - 1 && i >= p.onset) {
                // shared area split conservatively; flagged as error source
                piece *= 0.5;
            }
            acc += piece;
        }
        return acc;
    }

    private static int indexNearest(double[] t, double value) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < t.length; i++) {
            double d = Math.abs(t[i] - value);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    private static Qty rateFor(double rateKPerMin) {
        return Qty.parsed(String.format("%.3f K/min", rateKPerMin), rateKPerMin, "K/min");
    }
}
