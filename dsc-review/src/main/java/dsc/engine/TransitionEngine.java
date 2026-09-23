package dsc.engine;

import dsc.model.BaselineCandidate;
import dsc.model.BaselineType;
import dsc.model.CalibrationVersion;
import dsc.model.CandidateStatus;
import dsc.model.Diagnostic;
import dsc.model.DiagnosticLevel;
import dsc.model.ErrorContribution;
import dsc.model.Interval;
import dsc.model.MassVersion;
import dsc.model.NormalizedPoint;
import dsc.model.Quantity;
import dsc.model.ScanRateVersion;
import dsc.model.SegmentDirection;
import dsc.model.SegmentLeg;
import dsc.model.TraceView;
import dsc.model.TransitionKind;
import dsc.model.TransitionNature;
import dsc.model.TransitionResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Integrates (signal - baseline) over one interval pinned to one monotone leg.
 * The trapezoid sum uses the signed temperature increment, so it is valid for
 * non-uniform sampling and for cooling legs without any file-name heuristics.
 * Produces onset/peak/endset, raw + normalized enthalpy, nature classification
 * and an itemized enthalpy error budget.
 */
public final class TransitionEngine {

    private TransitionEngine() {}

    public record Integral(double signedAreaMWK, double enthalpyMJ, double enthalpyJPerG,
                           Double onsetC, Double peakC, Double endsetC,
                           TransitionNature nature, List<Diagnostic> diagnostics,
                           List<ErrorContribution> errors, int pointCount) {}

    public static TransitionResult integrate(String transitionId, TraceView trace, Interval interval,
                                             BaselineCandidate baseline, ScanRateVersion rate,
                                             MassVersion mass, CalibrationVersion cal,
                                             TransitionKind kind, int version, long basedOnEventSeq) {
        List<Diagnostic> diags = new ArrayList<>(baseline.diagnostics());
        List<ErrorContribution> errors = new ArrayList<>();
        SegmentLeg leg;
        try {
            leg = BaselineEngine.resolveLeg(trace, interval);
        } catch (IllegalStateException e) {
            return failed(transitionId, baseline, interval, kind, version, basedOnEventSeq,
                    mass, rate, cal, Diagnostic.error("LEG_UNPINNED", e.getMessage()));
        }
        if (leg.direction() == SegmentDirection.MIXED) {
            return failed(transitionId, baseline, interval, kind, version, basedOnEventSeq,
                    mass, rate, cal, Diagnostic.error("MIXED_DIRECTION_INTERVAL",
                            "interval mixes heating and cooling points; integration refused"));
        }
        if (interval.isEmpty()) {
            diags.add(Diagnostic.error("EMPTY_INTERVAL",
                    "empty interval [" + Quantity.trim(interval.startC()) + ", "
                            + Quantity.trim(interval.endC()) + "] degC; nothing to integrate"));
            return new TransitionResult(transitionId, baseline.candidateId(), interval.intervalId(),
                    trace.sequenceId(), TransitionNature.UNRESOLVED, kind, null, null, null,
                    null, null, 0, 0, mass == null ? null : mass.massId(),
                    rate == null ? null : rate.rateId(), cal.calibrationId(),
                    CandidateStatus.ALTERNATIVE, version, basedOnEventSeq, errors, diags);
        }
        if (baseline.diagnostics().stream().anyMatch(d -> d.level() == DiagnosticLevel.ERROR)) {
            return failed(transitionId, baseline, interval, kind, version, basedOnEventSeq,
                    mass, rate, cal, Diagnostic.error("BASELINE_INVALID",
                            "baseline candidate carries errors; integration suppressed"));
        }

        List<NormalizedPoint> pts = BaselineEngine.intervalPoints(trace, interval);
        if (pts.size() < 2) {
            diags.add(Diagnostic.error("INTERVAL_INSUFFICIENT_POINTS",
                    "interval contains " + pts.size() + " point(s); need at least 2 for an integral"));
            return new TransitionResult(transitionId, baseline.candidateId(), interval.intervalId(),
                    trace.sequenceId(), TransitionNature.UNRESOLVED, kind, null, null, null,
                    null, null, 0, pts.size(), mass == null ? null : mass.massId(),
                    rate == null ? null : rate.rateId(), cal.calibrationId(),
                    CandidateStatus.ALTERNATIVE, version, basedOnEventSeq, errors, diags);
        }
        verifySingleDirection(pts, leg, diags);

        double area = 0;
        for (int i = 1; i < pts.size(); i++) {
            double y0 = pts.get(i - 1).hfMW() - BaselineEngine.evaluate(baseline, pts.get(i - 1).tC());
            double y1 = pts.get(i).hfMW() - BaselineEngine.evaluate(baseline, pts.get(i).tC());
            area += (y0 + y1) / 2.0 * (pts.get(i).tC() - pts.get(i - 1).tC());
        }
        if (rate == null || rate.kPerMin() <= 0) {
            diags.add(Diagnostic.error("RATE_MISSING",
                    "scan rate missing or non-positive; time-normalized enthalpy unavailable"));
        }
        double beta = rate == null ? Double.NaN : rate.kPerMin();
        double enthalpyMJ = Double.isNaN(beta) ? Double.NaN : area / beta * 60.0;

        TransitionNature nature = classifyNature(trace.signConvention(), area, kind);
        Double[] ope = onsetPeakEndset(pts, baseline, area, kind);
        if (kind == TransitionKind.GLASS_TRANSITION || baseline.type() == BaselineType.SIGMOID_STEP) {
            nature = TransitionNature.STEP_LIKE;
            ope[1] = baseline.tgWidthC() > 0 ? baseline.tgMidC() : ope[1];
            diags.add(Diagnostic.info("TG_STEP_EVENT",
                    "glass-transition step: Tg(mid) " + (ope[1] == null ? "?" : Quantity.trim(ope[1]))
                            + " degC; onset/endset report step extents"));
        }
        Double jpg = null;
        if (mass == null || mass.mg() <= 0) {
            diags.add(Diagnostic.error("MASS_MISSING",
                    "sample mass missing; normalized enthalpy (J/g) unavailable"));
        } else if (!Double.isNaN(enthalpyMJ)) {
            jpg = enthalpyMJ / mass.mg();
        }

        // ---- itemized enthalpy error budget (each contribution carries its unit) ----
        double absArea = Math.abs(area);
        errors.add(new ErrorContribution("BASELINE_FIT", baseline.candidateId(),
                baseline.fitRmseMW() * temperatureSpan(pts), "mJ*K/min",
                "residual RMSE " + Quantity.trim(baseline.fitRmseMW()) + " mW over interval anchors"));
        if (cal.uncertaintyC() > 0) {
            errors.add(new ErrorContribution("TEMP_CALIBRATION", cal.calibrationId(),
                    cal.uncertaintyC(), "degC",
                    "calibration uncertainty shifts onset/endset and integral bounds"));
        }
        if (mass != null && mass.uncertaintyMg() > 0 && jpg != null) {
            double dH = mass.uncertaintyMg() / mass.mg() * Math.abs(jpg);
            errors.add(new ErrorContribution("MASS_UNCERTAINTY", mass.massId(), dH, "J/g",
                    "balance uncertainty " + Quantity.trim(mass.uncertaintyMg()) + " mg of "
                            + Quantity.trim(mass.mg()) + " mg"));
        }
        if (rate != null && rate.uncertaintyKPerMin() > 0 && !Double.isNaN(enthalpyMJ)) {
            errors.add(new ErrorContribution("RATE_UNCERTAINTY", rate.rateId(),
                    rate.uncertaintyKPerMin() / beta * Math.abs(enthalpyMJ), "mJ",
                    "scan-rate uncertainty " + Quantity.trim(rate.uncertaintyKPerMin()) + " K/min"));
        }
        errors.add(new ErrorContribution("SAMPLING_RESOLUTION", trace.sequenceId(),
                medianGapC(pts), "degC",
                "trapezoid truncation governed by median temperature spacing"));
        return new TransitionResult(transitionId, baseline.candidateId(), interval.intervalId(),
                trace.sequenceId(), nature, kind, ope[0], ope[1], ope[2],
                Double.isNaN(enthalpyMJ) ? null : enthalpyMJ, jpg, area, pts.size(),
                mass == null ? null : mass.massId(), rate == null ? null : rate.rateId(),
                cal.calibrationId(), CandidateStatus.ALTERNATIVE, version, basedOnEventSeq, errors, diags);
    }

    private static TransitionNature classifyNature(dsc.model.SignConvention sign, double area,
                                                   TransitionKind kind) {
        if (sign == dsc.model.SignConvention.UNKNOWN) return TransitionNature.UNRESOLVED;
        if (kind == TransitionKind.GLASS_TRANSITION) return TransitionNature.STEP_LIKE;
        return area >= 0 ? TransitionNature.ENDOTHERMIC : TransitionNature.EXOTHERMIC;
    }

    private static Double[] onsetPeakEndset(List<NormalizedPoint> pts, BaselineCandidate b,
                                           double area, TransitionKind kind) {
        double minDev = Double.POSITIVE_INFINITY, maxDev = Double.NEGATIVE_INFINITY;
        int peakIdx = 0;
        boolean positive = area >= 0;
        double[] dev = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            dev[i] = pts.get(i).hfMW() - BaselineEngine.evaluate(b, pts.get(i).tC());
            if (dev[i] > maxDev) { maxDev = dev[i]; }
            if (dev[i] < minDev) { minDev = dev[i]; }
        }
        double threshold = positive ? maxDev * 0.05 : minDev * 0.05;
        Integer onset = null, endset = null;
        for (int i = 0; i < dev.length; i++) {
            boolean crossed = positive ? dev[i] >= threshold : dev[i] <= threshold;
            if (crossed && onset == null) onset = i;
            if (crossed) endset = i;
        }
        for (int i = 0; i < dev.length; i++) {
            if (positive && dev[i] == maxDev) { peakIdx = i; break; }
            if (!positive && dev[i] == minDev) { peakIdx = i; break; }
        }
        return new Double[]{
                onset == null ? null : pts.get(onset).tC(),
                pts.get(peakIdx).tC(),
                endset == null ? null : pts.get(endset).tC()};
    }

    private static void verifySingleDirection(List<NormalizedPoint> pts, SegmentLeg leg,
                                              List<Diagnostic> diags) {
        int up = 0, down = 0;
        for (int i = 1; i < pts.size(); i++) {
            if (pts.get(i).tC() > pts.get(i - 1).tC()) up++;
            else if (pts.get(i).tC() < pts.get(i - 1).tC()) down++;
        }
        if (up > 0 && down > 0) {
            diags.add(Diagnostic.error("MIXED_DIRECTION_INTERVAL",
                    "interval points contain both increasing (" + up + ") and decreasing ("
                            + down + ") steps on leg " + leg.legId()
                            + "; heating and cooling must not be integrated together"));
        }
    }

    private static double temperatureSpan(List<NormalizedPoint> pts) {
        return Math.abs(pts.get(pts.size() - 1).tC() - pts.get(0).tC());
    }

    private static double medianGapC(List<NormalizedPoint> pts) {
        List<Double> gaps = new ArrayList<>();
        for (int i = 1; i < pts.size(); i++) gaps.add(Math.abs(pts.get(i).tC() - pts.get(i - 1).tC()));
        gaps.sort(Double::compare);
        return gaps.isEmpty() ? 0 : gaps.get(gaps.size() / 2);
    }

    private static TransitionResult failed(String id, BaselineCandidate b, Interval interval,
                                           TransitionKind kind, int version, long seq,
                                           MassVersion mass, ScanRateVersion rate,
                                           CalibrationVersion cal, Diagnostic fatal) {
        return new TransitionResult(id, b.candidateId(), interval.intervalId(),
                b.sequenceId(), TransitionNature.UNRESOLVED, kind, null, null, null,
                null, null, 0, 0, mass == null ? null : mass.massId(),
                rate == null ? null : rate.rateId(), cal == null ? null : cal.calibrationId(),
                CandidateStatus.ALTERNATIVE, version, seq, List.of(), List.of(fatal));
    }
}
