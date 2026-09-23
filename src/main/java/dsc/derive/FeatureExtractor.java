package dsc.derive;

import dsc.model.BaselineFit;
import dsc.model.Interval;
import dsc.model.Trace;
import dsc.model.TransitionFeature;
import dsc.model.Types;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts onset / peak / endset and enthalpy from a baseline candidate inside an interval.
 *
 * Guarantees:
 *  - heat-flow direction comes from the instrument's explicit convention, never a file name;
 *  - cooling segments are integrated along acquisition time (|dT|/|rate|) but never combined
 *    with heating segments;
 *  - overlapping events are split into lobes and all legal readings are returned;
 *  - non-uniform temperature spacing integrates with the actual dT at every step;
 *  - mass normalisation is reported stale when the mass version changed.
 */
public final class FeatureExtractor {

    public static final class Result {
        public final List<TransitionFeature> features;
        public final List<String> diagnostics;

        public Result(List<TransitionFeature> features, List<String> diagnostics) {
            this.features = features;
            this.diagnostics = diagnostics;
        }
    }

    private static final class Sample {
        final int idx;
        final double t;
        final double excess;

        Sample(int idx, double t, double excess) {
            this.idx = idx;
            this.t = t;
            this.excess = excess;
        }
    }

    private FeatureExtractor() {}

    /**
     * @param rateKPerS  signed scan rate (heating positive, cooling negative); NaN if not archived
     * @param massKg     current sample mass in kg; NaN if unavailable
     * @param massVersion version of the mass source used (for stale-derived diagnostics)
     */
    public static Result extract(String baselineId, Trace trace, Interval interval, BaselineFit baseline,
                                 double rateKPerS, double massKg, int massVersion,
                                 boolean massStale, int calVersion, double calOffsetK) {
        List<String> diag = new ArrayList<>();
        List<Sample> window = new ArrayList<>();
        int badPoints = 0;
        for (int i = 0; i < trace.size(); i++) {
            double t = trace.calibratedTempK(i);
            double h = trace.endoUpHeatFlowW(i);
            if (Double.isNaN(t) || Double.isNaN(h)) {
                if (!interval.contains(t)) badPoints++;
                continue;
            }
            if (interval.contains(t)) {
                window.add(new Sample(i, t, h - baseline.valueAt(t)));
            }
        }
        if (badPoints > 0) diag.add("UNCONVERTIBLE_POINTS:" + badPoints);

        if (window.isEmpty()) {
            diag.add("EMPTY_INTERVAL:no samples between "
                    + dsc.model.Physical.fmt(interval.startK) + " K and "
                    + dsc.model.Physical.fmt(interval.endK) + " K");
            return new Result(List.of(), diag);
        }
        if (window.size() < 3) diag.add("SHORT_INTERVAL:only " + window.size() + " samples");

        double peak = 0;
        for (Sample s : window) peak = Math.max(peak, Math.abs(s.excess));
        double noise = anchorNoise(trace, interval);
        double threshold = Math.max(3 * noise, 0.05 * peak);

        List<int[]> lobes = splitLobes(window, threshold);
        List<TransitionFeature> features = new ArrayList<>();
        boolean overlap = lobes.size() > 1;
        if (overlap) diag.add("OVERLAP:" + lobes.size() + " significant lobes; all legal readings retained");
        if (trace.nonUniformSpacing) diag.add("NONUNIFORM_SPACING:trapezoid dT per step");
        if (Double.isNaN(rateKPerS)) diag.add("RATE_MISSING:energy cannot be integrated without scan rate");
        if (Double.isNaN(massKg)) diag.add("MASS_MISSING:specific enthalpy unavailable");
        if (massStale) diag.add("MASS_STALE:derived from mass v" + massVersion
                + "; all normalised derivatives expired");
        diag.add("SIGN_CONVENTION:" + trace.convention
                + "; endo-up normalised; direction " + trace.direction);
        diag.add("CALIBRATION:offset " + dsc.model.Physical.fmt(calOffsetK) + " K (cal v" + calVersion + ")");

        int lobeNo = 0;
        for (int[] lr : lobes) {
            lobeNo++;
            features.add(buildFeature(baselineId + ":f" + lobeNo, baseline, trace, window,
                    lr[0], lr[1], rateKPerS, massKg, overlap, diag, interval));
        }
        return new Result(features, diag);
    }

    /** RMS noise immediately outside the interval, used to separate real events from noise. */
    private static double anchorNoise(Trace trace, Interval interval) {
        double sum = 0;
        int n = 0;
        for (int pass = 0; pass < 2; pass++) {
            double lo = pass == 0 ? interval.startK - 8 : interval.endK;
            double hi = pass == 0 ? interval.startK : interval.endK + 8;
            for (int i = 0; i < trace.size(); i++) {
                double t = trace.calibratedTempK(i);
                double h = trace.endoUpHeatFlowW(i);
                if (Double.isNaN(t) || Double.isNaN(h)) continue;
                if (t >= lo && t <= hi) { sum += h * h; n++; }
            }
        }
        return n == 0 ? 0 : Math.sqrt(sum / n) * 0 + noiseFromResiduals(trace, interval);
    }

    private static double noiseFromResiduals(Trace trace, Interval interval) {
        List<Double> vals = new ArrayList<>();
        for (int i = 0; i < trace.size(); i++) {
            double t = trace.calibratedTempK(i);
            double h = trace.endoUpHeatFlowW(i);
            if (!Double.isNaN(t) && !Double.isNaN(h)
                    && (t >= interval.startK - 8 && t < interval.startK
                    || t > interval.endK && t <= interval.endK + 8)) {
                vals.add(h);
            }
        }
        if (vals.size() < 3) return 1e-9;
        double mean = vals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var = vals.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / vals.size();
        return Math.sqrt(var);
    }

    /** Splits the window into excursion lobes; indices refer to the acquisition-ordered window. */
    private static List<int[]> splitLobes(List<Sample> window, double threshold) {
        List<int[]> ranges = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < window.size(); i++) {
            boolean active = Math.abs(window.get(i).excess) > threshold;
            if (active && start < 0) start = Math.max(0, i - 1);
            if ((!active || i == window.size() - 1) && start >= 0) {
                int end = active && i == window.size() - 1 ? i : Math.min(window.size() - 1, i);
                if (end > start) ranges.add(new int[]{start, end});
                start = -1;
            }
        }
        if (ranges.isEmpty()) ranges.add(new int[]{0, window.size() - 1});
        return mergeCloseRanges(ranges, 2);
    }

    private static List<int[]> mergeCloseRanges(List<int[]> r, int gap) {
        List<int[]> out = new ArrayList<>();
        for (int[] c : r) {
            if (!out.isEmpty() && c[0] - out.get(out.size() - 1)[1] <= gap) {
                out.get(out.size() - 1)[1] = c[1];
            } else {
                out.add(new int[]{c[0], c[1]});
            }
        }
        return out;
    }

    private static TransitionFeature buildFeature(String id, BaselineFit baseline, Trace trace,
                                                  List<Sample> window, int from, int to,
                                                  double rateKPerS, double massKg,
                                                  boolean overlap, List<String> baseDiag,
                                                  Interval interval) {
        // Acquisition-time ordering inside this segment.
        int peakIdx = from;
        double peakAbs = 0;
        for (int i = from; i <= to; i++) {
            if (Math.abs(window.get(i).excess) > peakAbs) {
                peakAbs = Math.abs(window.get(i).excess);
                peakIdx = i;
            }
        }
        double signPeak = Math.signum(window.get(peakIdx).excess);

        double onsetK = tangentIntersection(window, from, peakIdx, true, trace, interval);
        double endsetK = tangentIntersection(window, peakIdx, to, false, trace, interval);
        double peakK = window.get(peakIdx).t;

        double preLevel = meanExcess(window, from, Math.max(from, peakIdx - 2));
        double postLevel = meanExcess(window, Math.min(to, peakIdx + 2), to);
        boolean stepLike = Math.abs(postLevel - preLevel) > 0.4 * peakAbs
                && netAreaRatio(window, from, to) < 0.35;

        Types.TransitionKind kind;
        if (stepLike) {
            kind = Types.TransitionKind.GLASS;
        } else if (overlap) {
            kind = Types.TransitionKind.UNRESOLVED;
        } else {
            // endo-up: endotherm positive. Melting is endo on heating; crystallisation is exo;
            // an endotherm on a cooling segment is physically unresolved.
            boolean endo = signPeak > 0;
            if (trace.direction == Types.ScanDirection.HEATING) {
                kind = endo ? Types.TransitionKind.MELTING : Types.TransitionKind.CRYSTALLISATION;
            } else if (trace.direction == Types.ScanDirection.COOLING) {
                kind = endo ? Types.TransitionKind.UNRESOLVED : Types.TransitionKind.CRYSTALLISATION;
            } else {
                kind = Types.TransitionKind.UNRESOLVED;
            }
        }

        double energyJ = Double.NaN;
        double specific = Double.NaN;
        List<String> errors = new ArrayList<>(baseDiag);
        if (!Double.isNaN(rateKPerS) && rateKPerS != 0) {
            energyJ = integrateEnergy(window, from, to, Math.abs(rateKPerS));
            if (!Double.isNaN(massKg) && massKg > 0) specific = energyJ / massKg;
        }
        if (kind == Types.TransitionKind.GLASS) {
            errors.add("GLASS_STEP:step feature; value is relaxation energy, not a latent heat");
        }
        if (baseline.kind == BaselineFit.Kind.PIECEWISE_TANGENT) {
            errors.add("PIECEWISE_BASELINE:onset/endset depend on the join temperature");
        }

        Map<String, String> annotated = new LinkedHashMap<>();
        annotated.put("onset", dsc.model.Physical.fmt(onsetK) + " K [ITS-90, calibrated]");
        annotated.put("peak", dsc.model.Physical.fmt(peakK) + " K [ITS-90, calibrated]");
        annotated.put("endset", dsc.model.Physical.fmt(endsetK) + " K [ITS-90, calibrated]");
        annotated.put("energy", Double.isNaN(energyJ) ? "n/a"
                : dsc.model.Physical.fmt(energyJ) + " J [trapezoid over acquisition time]");
        annotated.put("enthalpy", Double.isNaN(specific) ? "n/a"
                : dsc.model.Physical.fmt(specific) + " J/kg [endo positive, mass normalised]");
        annotated.put("kind", kind.name());

        return new TransitionFeature(id, baselineId, kind, onsetK, peakK, endsetK,
                energyJ, specific, errors, annotated);
    }

    /** Tangent intersection with the zero level: leading (onset) or trailing (endset) side. */
    private static double tangentIntersection(List<Sample> w, int a, int b, boolean leading,
                                              Trace trace, Interval interval) {
        if (b <= a) return w.get(a).t;
        int best = a;
        double bestSlope = 0;
        for (int i = a + 1; i <= b; i++) {
            double dt = w.get(i).t - w.get(i - 1).t;
            if (Math.abs(dt) < 1e-12) continue;
            double slope = (w.get(i).excess - w.get(i - 1).excess) / dt;
            if (Math.abs(slope) > Math.abs(bestSlope)) {
                bestSlope = slope;
                best = i;
            }
        }
        double tm = w.get(best).t;
        double em = w.get(best).excess;
        if (Math.abs(bestSlope) < 1e-24) return tm;
        double crossT = tm - em / bestSlope;
        // Clamp only to the segment's observed temperature span.
        double lo = Math.min(w.get(0).t, w.get(w.size() - 1).t);
        double hi = Math.max(w.get(0).t, w.get(w.size() - 1).t);
        return Math.max(lo, Math.min(hi, crossT));
    }

    private static double meanExcess(List<Sample> w, int a, int b) {
        if (b < a) return 0;
        double s = 0;
        int n = 0;
        for (int i = a; i <= b; i++) { s += w.get(i).excess; n++; }
        return s / Math.max(1, n);
    }

    private static double netAreaRatio(List<Sample> w, int a, int b) {
        double net = 0, gross = 0;
        for (int i = a + 1; i <= b; i++) {
            double area = 0.5 * (w.get(i).excess + w.get(i - 1).excess);
            net += area;
            gross += Math.abs(area);
        }
        return gross == 0 ? 0 : Math.abs(net) / gross;
    }

    /** ∫ excess d t with t advanced by |ΔT|/|rate| — identical formula for heating and cooling. */
    private static double integrateEnergy(List<Sample> w, int a, int b, double absRateKPerS) {
        double e = 0;
        for (int i = a + 1; i <= b; i++) {
            double dt = Math.abs(w.get(i).t - w.get(i - 1).t) / absRateKPerS;
            e += 0.5 * (w.get(i).excess + w.get(i - 1).excess) * dt;
        }
        return e;
    }
}
