package dsc.derive;

import dsc.domain.AnalysisInterval;
import dsc.domain.BaselineCandidate;
import dsc.domain.DerivationException;
import dsc.domain.DerivationRecord;
import dsc.domain.ErrorContribution;
import dsc.domain.Ids;
import dsc.domain.SamplePoint;
import dsc.domain.Segment;
import dsc.domain.SourceRefs;
import dsc.domain.TransitionResult;
import dsc.domain.Units;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Core domain rules for DSC baselines and phase-transition integrals.
 *
 * <p>Implements: endo/exo direction from measured sign and instrument convention only (never file
 * name); heating/cooling kept within a single segment; multiple plausible baselines retained;
 * non-uniform temperature grids integrated over measured time; mass revisions invalidate all
 * normalized derivatives via the dependency snapshot; empty intervals rejected with diagnostics.
 */
public final class DerivationEngine {

    /** Sidecar inputs assembled from the separately archived sources. */
    public record Inputs(SourceRefs.Mass mass, SourceRefs.ScanRate scanRate,
                         SourceRefs.TempCalibration calibration,
                         SourceRefs.Tangents tangents) {
    }

    public DerivationOutput derive(Segment segment, AnalysisInterval interval,
                                   Inputs inputs, long producedAtSeq) {
        List<String> diagnostics = new ArrayList<>();
        if (segment.filenameDirectionMismatch()) {
            diagnostics.add("file name '" + segment.filename()
                    + "' suggests a direction contradicting the measured "
                    + segment.observedDirection() + " sequence; measured direction used");
        }
        segment.points().stream().filter(p -> !p.usable()).forEach(p ->
                diagnostics.add("point " + p.index() + " excluded: "
                        + String.join("; ", p.diagnostics())));

        double offsetC = inputs.calibration() == null ? 0.0 : inputs.calibration().offsetC();
        List<SamplePoint> usable = segment.usablePoints();

        List<SamplePoint> inside = new ArrayList<>();
        for (SamplePoint p : usable) {
            double tc = p.temperatureC() + offsetC;
            if (tc >= interval.lowC() - 1e-9 && tc <= interval.highC() + 1e-9) {
                inside.add(p);
            }
        }
        if (inside.isEmpty()) {
            throw new DerivationException("EMPTY_INTERVAL",
                    "interval " + interval.lowC() + "–" + interval.highC()
                            + " °C contains no usable samples on segment " + segment.segmentId());
        }
        if (inside.size() < 3) {
            diagnostics.add("interval contains only " + inside.size()
                    + " usable sample(s); integrals and fits are poorly conditioned");
        }

        detectNonUniform(inside, diagnostics);

        int n = inside.size();
        double[] t = new double[n];
        double[] qEndo = new double[n];
        for (int i = 0; i < n; i++) {
            t[i] = inside.get(i).temperatureC() + offsetC;
            qEndo[i] = segment.endoPositiveMw(inside.get(i));
        }

        List<BaselineCandidate> candidates = buildCandidates(
                segment, interval, inputs, t, qEndo, diagnostics);

        List<Integer> extrema = findExtrema(t, qEndo, candidates.get(0));
        double endoMass = sumPositiveExcursion(t, qEndo, candidates.get(0));
        double exoMass = -sumNegativeExcursion(t, qEndo, candidates.get(0));
        boolean hasStep = detectsStep(t, qEndo, candidates.get(0));
        boolean peakDominated = Math.max(endoMass, exoMass) > 3.0 * Math.abs(extremesRange[0]);
        boolean overlapping = extrema.size() > 1 && peakDominated;
        if (overlapping) {
            diagnostics.add("overlapping transitions: " + extrema.size()
                    + " extrema share the interval; all candidates retained");
        }

        List<DerivationRecord> records = new ArrayList<>();
        for (BaselineCandidate candidate : candidates) {
            records.add(buildRecord(segment, interval, inputs, candidate, t, qEndo, inside,
                    extrema, hasStep, peakDominated, overlapping, producedAtSeq, diagnostics));
        }
        return new DerivationOutput(records, diagnostics);
    }

    private void detectNonUniform(List<SamplePoint> inside, List<String> diagnostics) {
        if (inside.size() < 3) {
            return;
        }
        List<Double> gaps = new ArrayList<>();
        for (int i = 1; i < inside.size(); i++) {
            double dt = Math.abs(inside.get(i).temperatureC() - inside.get(i - 1).temperatureC());
            gaps.add(dt);
        }
        double mean = gaps.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double max = gaps.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (mean > 0 && max > 2.5 * mean) {
            diagnostics.add("non-uniform temperature grid (mean ΔT "
                    + String.format("%.2f", mean) + " °C, max ΔT "
                    + String.format("%.2f", max) + " °C); integration uses measured time");
        }
        for (int i = 1; i < gaps.size(); i++) {
            if (Math.abs(gaps.get(i) - gaps.get(i - 1)) < 1e-9) {
                diagnostics.add("duplicate temperature at samples " + (i - 1) + "," + i);
                break;
            }
        }
    }

    private List<BaselineCandidate> buildCandidates(Segment segment, AnalysisInterval interval,
                                                     Inputs inputs, double[] t, double[] q,
                                                     List<String> diagnostics) {
        List<BaselineCandidate> candidates = new ArrayList<>();
        int window = Math.max(2, Math.min(5, t.length / 3));

        BaselineCandidate chord = chordCandidate(t, q);
        candidates.add(chord);

        if (t.length >= 2 * window) {
            candidates.add(lsTangentCandidate(t, q, window));
        } else {
            diagnostics.add("interval too short for independent tangent least-squares fit");
        }

        if (t.length >= 4) {
            candidates.add(stepTangentCandidate(t, q));
        }

        if (inputs.tangents() != null
                && inputs.tangents().segmentId().equals(segment.segmentId())
                && inputs.tangents().intervalId().equals(interval.intervalId())) {
            BaselineCandidate user = userTangentCandidate(inputs.tangents(), t, q);
            if (user != null) {
                candidates.add(user);
            }
        }
        return candidates;
    }

    private BaselineCandidate chordCandidate(double[] t, double[] q) {
        int last = t.length - 1;
        Fit fit = Fit.leastSquares(t, q, 0, t.length);
        String id = Ids.contentId("bl", Rules.METHOD_CHORD,
                String.valueOf(t[0]), String.valueOf(t[last]),
                String.valueOf(q[0]), String.valueOf(q[last]));
        return new BaselineCandidate(id, Rules.METHOD_CHORD, fit.intercept(), fit.slope(),
                t[0], q[0], t[last], q[last], fit.rms(), fit.rms() / Math.sqrt(t.length),
                "straight line between interval end points", false);
    }

    private BaselineCandidate lsTangentCandidate(double[] t, double[] q, int window) {
        int n = t.length;
        int[] li = indexWindow(t, true, window);
        int[] ri = indexWindow(t, false, window);
        double[] xs = new double[2 * window];
        double[] ys = new double[2 * window];
        for (int i = 0; i < window; i++) {
            xs[i] = t[li[0] + i];
            ys[i] = q[li[0] + i];
            xs[window + i] = t[ri[0] + i];
            ys[window + i] = q[ri[0] + i];
        }
        Fit fit = Fit.leastSquares(xs, ys, 0, xs.length);
        double la = mean(t, li[0], window);
        double lav = mean(q, li[0], window);
        double ra = mean(t, ri[0], window);
        double rav = mean(q, ri[0], window);
        double localRms = windowRms(t, q, fit, li[0], window)
                + windowRms(t, q, fit, ri[0], window);
        String id = Ids.contentId("bl", Rules.METHOD_TANGENT_LS,
                String.valueOf(la), String.valueOf(lav), String.valueOf(ra),
                String.valueOf(rav), String.valueOf(window));
        return new BaselineCandidate(id, Rules.METHOD_TANGENT_LS, fit.intercept(), fit.slope(),
                la, lav, ra, rav, localRms / 2, fit.rms(),
                "least-squares line through pre- and post-transition tangent windows", false);
    }

    private BaselineCandidate stepTangentCandidate(double[] t, double[] q) {
        int third = Math.max(1, t.length / 4);
        double la = mean(t, 0, third);
        double lav = mean(q, 0, third);
        double ra = mean(t, t.length - third, third);
        double rav = mean(q, q.length - third, third);
        double slope = (rav - lav) / (ra - la);
        double intercept = lav - slope * la;
        double sq = 0;
        int count = 0;
        for (int i = 0; i < third; i++) {
            sq += sqr(q[i] - (intercept + slope * t[i]));
            sq += sqr(q[q.length - 1 - i] - (intercept + slope * t[t.length - 1 - i]));
            count += 2;
        }
        double rms = Math.sqrt(sq / count);
        String id = Ids.contentId("bl", Rules.METHOD_STEP_TANGENT,
                String.valueOf(la), String.valueOf(lav), String.valueOf(ra), String.valueOf(rav));
        return new BaselineCandidate(id, Rules.METHOD_STEP_TANGENT, intercept, slope,
                la, lav, ra, rav, rms, rms / Math.sqrt(count),
                "centroids of the outer plateaus; suited to glass-transition steps", false);
    }

    private BaselineCandidate userTangentCandidate(SourceRefs.Tangents tangents,
                                                   double[] t, double[] q) {
        List<double[]> left = tangents.leftTangent();
        List<double[]> right = tangents.rightTangent();
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return null;
        }
        double[] lx = left.get(0);
        double[] ly = left.size() > 1 ? left.get(1) : lx;
        double[] rx = right.get(0);
        double[] ry = right.size() > 1 ? right.get(1) : rx;
        double slope = (ry[1] - ly[1]) / (rx[0] - lx[0]);
        double intercept = ly[1] - slope * lx[0];
        String id = Ids.contentId("bl", "USER_TANGENT", tangents.tangentId(),
                String.valueOf(lx[0]), String.valueOf(ly[1]),
                String.valueOf(rx[0]), String.valueOf(ry[1]));
        double rms = 0;
        for (int i = 0; i < t.length; i++) {
            rms += sqr(q[i] - (intercept + slope * t[i]));
        }
        return new BaselineCandidate(id, "USER_TANGENT", intercept, slope,
                lx[0], ly[1], rx[0], ry[1], Math.sqrt(rms / t.length),
                Math.sqrt(rms / t.length) / Math.sqrt(t.length),
                "reviewer-selected tangent (" + tangents.tangentId() + ")", true);
    }

    private int[] indexWindow(double[] t, boolean left, int window) {
        int n = t.length;
        if (left) {
            double edge = t[0];
            double span = (t[n - 1] - t[0]) * 0.25;
            int end = 1;
            while (end < n && t[end] <= edge + span) {
                end++;
            }
            end = Math.max(window, end);
            return new int[]{0, Math.min(window, end)};
        }
        double edge = t[n - 1];
        double span = (t[n - 1] - t[0]) * 0.25;
        int start = n - 1;
        while (start > 0 && t[start - 1] >= edge - span) {
            start--;
        }
        start = Math.min(n - window, start);
        return new int[]{Math.max(0, start), Math.min(n, start + window)};
    }

    private double windowRms(double[] t, double[] q, Fit fit, int from, int count) {
        double sq = 0;
        for (int i = from; i < from + count; i++) {
            sq += sqr(q[i] - fit.predict(t[i]));
        }
        return Math.sqrt(sq / count);
    }

    private static double mean(double[] values, int from, int count) {
        double sum = 0;
        for (int i = from; i < from + count; i++) {
            sum += values[i];
        }
        return sum / count;
    }

    private static double sqr(double x) {
        return x * x;
    }

    private List<Integer> findExtrema(double[] t, double[] q, BaselineCandidate baseline) {
        List<Integer> extrema = new ArrayList<>();
        for (int i = 1; i < q.length - 1; i++) {
            double e = q[i] - baseline.valueAt(t[i]);
            double ep = q[i - 1] - baseline.valueAt(t[i - 1]);
            double en = q[i + 1] - baseline.valueAt(t[i + 1]);
            if (Math.abs(e) < 1e-6) {
                continue;
            }
            if ((e >= ep && e >= en) || (e <= ep && e <= en)) {
                extrema.add(i);
            }
        }
        return extrema;
    }

    private double sumPositiveExcursion(double[] t, double[] q, BaselineCandidate baseline) {
        double sum = 0;
        for (int i = 0; i < t.length; i++) {
            sum += Math.max(0, q[i] - baseline.valueAt(t[i]));
        }
        return sum;
    }

    private double sumNegativeExcursion(double[] t, double[] q, BaselineCandidate baseline) {
        double sum = 0;
        for (int i = 0; i < t.length; i++) {
            sum += Math.min(0, q[i] - baseline.valueAt(t[i]));
        }
        return sum;
    }

    private boolean detectsStep(double[] t, double[] q, BaselineCandidate baseline) {
        int third = Math.max(1, t.length / 4);
        double leftMean = 0;
        double rightMean = 0;
        for (int i = 0; i < third; i++) {
            leftMean += q[i] - baseline.valueAt(t[i]);
            rightMean += q[q.length - 1 - i]
                    - baseline.valueAt(t[t.length - 1 - i]);
        }
        double step = (rightMean - leftMean) / third;
        double rms = baseline.rmsResidualMw();
        return rms > 0 && Math.abs(step) > 2.5 * rms / Math.sqrt(third);
    }

    private DerivationRecord buildRecord(Segment segment, AnalysisInterval interval, Inputs inputs,
                                         BaselineCandidate candidate, double[] t, double[] q,
                                         List<SamplePoint> inside, List<Integer> extremaAll,
                                         boolean hasStep, boolean peakDominated, boolean overlapping,
                                         long producedAtSeq, List<String> diagnostics) {
        double[] excursion = new double[t.length];
        for (int i = 0; i < t.length; i++) {
            excursion[i] = q[i] - candidate.valueAt(t[i]);
        }
        int peakIndex = 0;
        for (int i = 1; i < excursion.length; i++) {
            if (Math.abs(excursion[i]) > Math.abs(excursion[peakIndex])) {
                peakIndex = i;
            }
        }
        String sense = excursion[peakIndex] >= 0 ? "endotherm" : "exotherm";
        String type = classify(hasStep, peakDominated, sense, segment.observedDirection());

        double threshold = Math.abs(excursion[peakIndex]) * 0.10;
        int onsetIndex = peakIndex;
        int endsetIndex = peakIndex;
        for (int i = peakIndex; i > 0; i--) {
            if (Math.abs(excursion[i - 1]) <= threshold) {
                onsetIndex = i;
                break;
            }
            onsetIndex = i - 1;
        }
        for (int i = peakIndex; i < t.length - 1; i++) {
            if (Math.abs(excursion[i + 1]) <= threshold) {
                endsetIndex = i;
                break;
            }
            endsetIndex = i + 1;
        }

        double areaJ = integrateAreaJ(inside, t, excursion, onsetIndex, endsetIndex);
        double massMg = inputs.mass() == null ? Double.NaN : inputs.mass().valueMg();
        double enthalpyJPerG = Double.isNaN(massMg) || massMg <= 0
                ? 0.0 : areaJ * 1000.0 / massMg;
        double deltaCp = 0.0;
        if ("glass".equals(type) && !Double.isNaN(massMg) && massMg > 0) {
            double stepMw = candidate.valueAt(t[endsetIndex]) - candidate.valueAt(t[onsetIndex]);
            double span = Math.max(1e-9, t[endsetIndex] - t[onsetIndex]);
            double rate = Math.abs(inputs.scanRate() == null
                    ? 10.0 : inputs.scanRate().signedKPerMin());
            double dcpJPerGK = (stepMw / 1000.0) / (rate / 60.0) / (massMg / 1000.0)
                    * span / span;
            deltaCp = dcpJPerGK / span * span;
        }

        ErrorBudget budget = buildErrorBudget(inside, t, excursion, candidate, inputs,
                onsetIndex, endsetIndex, areaJ, massMg);
        if ("exotherm".equals(sense)) {
            enthalpyJPerG = -Math.abs(enthalpyJPerG);
        } else {
            enthalpyJPerG = Math.abs(enthalpyJPerG);
        }

        List<String> localDiag = new ArrayList<>(diagnostics);
        if (Double.isNaN(massMg) || massMg <= 0) {
            localDiag.add("no valid sample mass; enthalpy normalization unavailable");
        }
        if (overlapping) {
            localDiag.add("ambiguous decomposition: reported integral covers "
                    + extremaAll.size() + " overlapping extrema");
        }

        String derivationId = Ids.contentId("drv", segment.segmentId(), interval.intervalId(),
                candidate.candidateId(), Rules.VERSION,
                inputs.mass() == null ? "no-mass" : inputs.mass().massId(),
                inputs.calibration() == null ? "no-cal" : inputs.calibration().calibrationId(),
                inputs.tangents() == null ? "no-tan" : inputs.tangents().tangentId());
        String resultId = Ids.contentId("res", derivationId);

        List<ErrorContribution> contributions = budget.contributions;
        TransitionResult result = new TransitionResult(
                resultId, candidate.candidateId(), type, sense,
                t[onsetIndex], t[endsetIndex], t[peakIndex], q[peakIndex],
                areaJ, enthalpyJPerG, deltaCp, budget.combinedJPerG,
                contributions, localDiag);

        Map<String, String> deps = new TreeMap<>();
        deps.put("segment", segment.segmentId());
        if (inputs.mass() != null) {
            deps.put("mass", inputs.mass().massId());
        }
        if (inputs.scanRate() != null) {
            deps.put("scanRate", inputs.scanRate().rateId());
        }
        if (inputs.calibration() != null) {
            deps.put("tempCalibration", inputs.calibration().calibrationId());
        }
        if (inputs.tangents() != null) {
            deps.put("tangents", inputs.tangents().tangentId());
        }
        deps.put("rules", Rules.VERSION);

        String status = overlapping || inside.size() < 3
                ? DerivationRecord.STATUS_AMBIGUOUS : DerivationRecord.STATUS_VALID;
        return new DerivationRecord(derivationId, interval.intervalId(), candidate.candidateId(),
                resultId, Rules.VERSION, producedAtSeq, deps, candidate, result, status, List.of());
    }

    private String classify(boolean hasStep, boolean peakDominated, String sense, String direction) {
        if (hasStep && !peakDominated) {
            return "glass";
        }
        if ("exotherm".equals(sense) && "cooling".equals(direction)) {
            return "crystallization";
        }
        if ("endotherm".equals(sense) && "heating".equals(direction)) {
            return "melting";
        }
        return "generic";
    }

    /**
     * Integrates the excursion over measured time via the trapezoidal rule. On non-uniform
     * temperature grids Δt comes from the instrument timestamps rather than an assumed scan rate.
     */
    private double integrateAreaJ(List<SamplePoint> inside, double[] t, double[] excursion,
                                  int onsetIndex, int endsetIndex) {
        double areaMwMin = 0;
        for (int i = onsetIndex + 1; i <= endsetIndex; i++) {
            double dtMin = inside.get(i).timeMin() - inside.get(i - 1).timeMin();
            if (dtMin < 0) {
                dtMin = Math.abs(dtMin);
            }
            areaMwMin += 0.5 * (excursion[i] + excursion[i - 1]) * dtMin;
        }
        return areaMwMin / 60000.0; // mW*min -> J
    }

    private record ErrorBudget(List<ErrorContribution> contributions, double combinedJPerG) {
    }

    private ErrorBudget buildErrorBudget(List<SamplePoint> inside, double[] t, double[] excursion,
                                         BaselineCandidate candidate, Inputs inputs,
                                         int onsetIndex, int endsetIndex, double areaJ,
                                         double massMg) {
        List<ErrorContribution> contributions = new ArrayList<>();
        double fitJ = candidate.fitUncertaintyMw()
                * (inside.get(endsetIndex).timeMin() - inside.get(onsetIndex).timeMin()) / 60000.0;
        contributions.add(new ErrorContribution(
                Ids.contentId("err", candidate.candidateId(), "baselineFit"),
                "baseline", "baseline fit residual (" + candidate.method() + ")",
                Math.abs(fitJ), "J", null));

        double tempU = inputs.calibration() == null ? 0.0 : inputs.calibration().uncertaintyC();
        if (tempU > 0) {
            double slope = Math.abs(candidate.slopeMwPerC());
            double calJ = tempU * slope
                    * (inside.get(endsetIndex).timeMin() - inside.get(onsetIndex).timeMin()) / 60000.0;
            contributions.add(new ErrorContribution(
                    Ids.contentId("err", candidate.candidateId(), "tempCalibration"),
                    "temperatureCalibration",
                    "temperature calibration uncertainty (±" + tempU + " °C)",
                    calJ, "J",
                    inputs.calibration() == null ? null : inputs.calibration().calibrationId()));
        }
        double gridJ = gridTimeJitterJ(inside, excursion, onsetIndex, endsetIndex);
        if (gridJ > 0) {
            contributions.add(new ErrorContribution(
                    Ids.contentId("err", candidate.candidateId(), "nonUniformGrid"),
                    "sampling", "non-uniform sampling / timestamp jitter",
                    gridJ, "J", null));
        }
        double massRel = inputs.mass() == null ? 0.0 : inputs.mass().relativeUncertainty();
        if (massRel > 0 && massMg > 0) {
            contributions.add(new ErrorContribution(
                    Ids.contentId("err", candidate.candidateId(), "mass"),
                    "mass", "sample mass uncertainty (±" + (massRel * 100) + "%)",
                    massRel, "rel", inputs.mass().massId()));
        }

        double sumSqJ = 0;
        for (ErrorContribution c : contributions) {
            if ("J".equals(c.unit())) {
                sumSqJ += c.magnitude() * c.magnitude();
            }
        }
        double combinedJ = Math.sqrt(sumSqJ);
        double combinedRel = massRel;
        double jPerG = 0;
        if (massMg > 0) {
            double base = Math.abs(areaJ) * 1000.0 / massMg;
            jPerG = Math.sqrt(combinedJ * combinedJ * (1000.0 / massMg)
                    * (combinedJ * 1000.0 / massMg) + (base * combinedRel) * (base * combinedRel));
        } else {
            jPerG = combinedJ * 1000.0;
        }
        return new ErrorBudget(contributions, jPerG);
    }

    private double gridTimeJitterJ(List<SamplePoint> inside, double[] excursion,
                                   int onsetIndex, int endsetIndex) {
        if (endsetIndex - onsetIndex < 2) {
            return 0;
        }
        double meanDt = (inside.get(endsetIndex).timeMin() - inside.get(onsetIndex).timeMin())
                / (endsetIndex - onsetIndex);
        double sumSq = 0;
        for (int i = onsetIndex + 1; i <= endsetIndex; i++) {
            double dt = Math.abs(inside.get(i).timeMin() - inside.get(i - 1).timeMin());
            sumSq += (dt - meanDt) * (dt - meanDt);
        }
        double sigmaT = Math.sqrt(sumSq / (endsetIndex - onsetIndex));
        double meanExcursion = 0;
        for (int i = onsetIndex; i <= endsetIndex; i++) {
            meanExcursion += Math.abs(excursion[i]);
        }
        meanExcursion /= (endsetIndex - onsetIndex + 1);
        return meanExcursion * sigmaT * Math.sqrt(endsetIndex - onsetIndex) / 60000.0;
    }
}
