package dsc.derive;

import dsc.core.Ids;
import dsc.core.Quantity;
import dsc.core.Units;
import dsc.events.Judgment;
import dsc.model.*;

import java.util.*;

/**
 * Onset / peak / endset and enthalpy rules.
 *
 * <p>Sign policy: the exo/endo direction is taken only from the archived
 * instrument convention. If it is UNKNOWN, or the deflection contradicts the
 * transition kind and direction, the magnitude is still reported but the sign
 * is AMBIGUOUS. Sweep direction (heating/cooling) is computed from data; file
 * names never decide anything.
 *
 * <p>Integration uses trapezoids over actual temperature increments, so
 * non-uniform grids integrate exactly with their own spacing.
 */
public class FeatureRules {

    private static final double ONSET_THRESHOLD = 0.10; // 10% of excess-height rise

    public FeatureResult derive(Region region, ScanSegment segment, BaselineResult baseline,
                                MassVersion mass, Calibration calibration, SignalConvention convention) {
        List<Judgment> js = new ArrayList<>();
        if (baseline == null || !Double.isFinite(baseline.slopeMilliwattPerCelsius())) {
            return indeterminate(region, baseline, "BASELINE_UNUSABLE",
                    "no usable baseline candidate; features not integrated", js);
        }
        double offset = calibration == null ? 0.0 : calibration.offsetCelsius().value();
        List<double[]> pts = new ArrayList<>();
        for (SamplePoint p : segment.plottablePoints()) {
            double t = p.tCelsius().value() + offset;
            if (region.contains(t)) pts.add(new double[]{t, p.hfMilliwatts().value()});
        }
        if (pts.size() < 3) {
            return indeterminate(region, baseline, "EMPTY_REGION",
                    "fewer than three measured points inside the interval", js);
        }
        pts.sort(Comparator.comparingDouble(a -> a[0]));
        double[] t = pts.stream().mapToDouble(a -> a[0]).toArray();
        double[] h = pts.stream().mapToDouble(a -> a[1]).toArray();
        double[] excess = new double[t.length];
        for (int i = 0; i < t.length; i++) excess[i] = h[i] - baseline.baselineAt(t[i]).value();

        int peakIdx = peakIndex(excess);
        double peakT = t[peakIdx];
        double peakHeight = excess[peakIdx];
        int onsetIdx = crossingIndex(t, excess, peakIdx, true, peakHeight * ONSET_THRESHOLD);
        int endIdx = crossingIndex(t, excess, peakIdx, false, peakHeight * ONSET_THRESHOLD);
        double onsetT = interpolateCrossing(t, excess, onsetIdx, peakHeight * ONSET_THRESHOLD, true);
        double endT = interpolateCrossing(t, excess, endIdx, peakHeight * ONSET_THRESHOLD, false);

        double areaMilliwattCelsius = Numeric.signedPeakArea(t, h,
                baseline.slopeMilliwattPerCelsius(), baseline.interceptMilliwatt());
        double rateCpm = segment.nominalRateCelsiusPerMin();
        if (!Double.isFinite(rateCpm) || rateCpm == 0.0) {
            js.add(Judgment.of("RATE_UNUSABLE", Judgment.Severity.REJECTED, region.regionId(),
                    "scan rate missing or zero; enthalpy cannot be integrated against time",
                    RuleVersions.FEATURE_RULES, Map.of()));
            return indeterminate(region, baseline, "RATE_UNUSABLE",
                    "enthalpy needs a declared, non-zero scan rate", js);
        }
        double rateCps = rateCpm / 60.0;
        double deltaHMilliJoule = areaMilliwattCelsius / rateCps;
        double magnitudeMilliJoule = Math.abs(deltaHMilliJoule);

        EnthalpySign sign = decideSign(convention, peakHeight, region.kind(), segment.direction(), js);
        Quantity total = FeatureResult.milliJoule(sign.deltaHSign() == 0
                ? magnitudeMilliJoule : sign.deltaHSign() * magnitudeMilliJoule);
        Quantity specific = null;
        if (mass == null) {
            js.add(Judgment.of("MASS_MISSING", Judgment.Severity.SUSPECT, region.regionId(),
                    "no sample mass version archived; specific enthalpy unavailable",
                    RuleVersions.FEATURE_RULES, Map.of()));
        } else {
            specific = FeatureResult.joulePerGram(sign.deltaHSign() == 0
                    ? magnitudeMilliJoule / 1000.0 / mass.gramsValue()
                    : sign.deltaHSign() * magnitudeMilliJoule / 1000.0 / mass.gramsValue());
        }
        if (convention == SignalConvention.UNKNOWN) {
            js.add(Judgment.of("SIGN_NOT_FROM_FILENAME", Judgment.Severity.SUSPECT, region.regionId(),
                    "heat-flow convention not declared; enthalpy sign reported AMBIGUOUS "
                            + "rather than inferred from file name or sweep direction",
                    RuleVersions.FEATURE_RULES, Map.of("sourceFile", segment.sourceFile())));
        }
        if (segment.nonUniformTemperatures()) {
            js.add(Judgment.of("NONUNIFORM_TEMPERATURE_GRID", Judgment.Severity.INFO, region.regionId(),
                    "temperature steps are non-uniform; integration used each interval's own width",
                    RuleVersions.FEATURE_RULES, Map.of("points", t.length)));
        }

        List<FeatureResult.ErrorContribution> errors = errorBudget(
                baseline, mass, calibration, segment, magnitudeMilliJoule);

        List<String> deps = new ArrayList<>(List.of(
                "seg:" + segment.segmentId(),
                "bl:" + baseline.id(),
                "rate:" + dsc.core.Units.fmt(rateCpm)));
        if (mass != null) deps.add("mass:" + mass.massId());
        if (calibration != null) deps.add("cal:" + calibration.calibrationId());

        String id = Ids.derived("feat", RuleVersions.FEATURE_RULES, region.regionId(),
                baseline.id(), mass == null ? "no-mass" : mass.massId(),
                calibration == null ? "no-cal" : calibration.calibrationId(),
                convention.code(), dsc.core.Units.fmt(rateCpm));
        return new FeatureResult(id, region.regionId(), baseline.id(), region.kind(),
                FeatureResult.celsius(onsetT), FeatureResult.celsius(peakT), FeatureResult.celsius(endT),
                total, specific, sign, NodeStatus.VALID,
                mass == null ? null : mass.massId(),
                calibration == null ? null : calibration.calibrationId(),
                RuleVersions.FEATURE_RULES, errors, js, deps, -1);
    }

    private EnthalpySign decideSign(SignalConvention convention, double peakHeight,
                                    TransitionKind kind, Direction direction, List<Judgment> js) {
        if (convention == SignalConvention.UNKNOWN) return EnthalpySign.AMBIGUOUS;
        boolean deflectionUp = peakHeight > 0;
        boolean exoUp = convention == SignalConvention.EXO_UP;
        boolean exothermic = exoUp == deflectionUp;
        // Physical consistency: melts are endothermic on heating, crystallization
        // exothermic on cooling. Mismatch is flagged, not "corrected".
        if (kind == TransitionKind.MELT && direction == Direction.HEATING && exothermic) {
            js.add(Judgment.of("SIGN_CONTRADICTS_KIND", Judgment.Severity.SUSPECT, "",
                    "melting on a heating segment shows an exothermic deflection; "
                            + "check instrument convention wiring",
                    RuleVersions.FEATURE_RULES, Map.of("peakHeight", peakHeight)));
            return EnthalpySign.AMBIGUOUS;
        }
        if (kind == TransitionKind.CRYSTALLIZATION && direction == Direction.COOLING && !exothermic) {
            js.add(Judgment.of("SIGN_CONTRADICTS_KIND", Judgment.Severity.SUSPECT, "",
                    "crystallization on a cooling segment shows an endothermic deflection; "
                            + "check instrument convention wiring",
                    RuleVersions.FEATURE_RULES, Map.of("peakHeight", peakHeight)));
            return EnthalpySign.AMBIGUOUS;
        }
        return exothermic ? EnthalpySign.EXOTHERMIC : EnthalpySign.ENDOTHERMIC;
    }

    private List<FeatureResult.ErrorContribution> errorBudget(BaselineResult bl, MassVersion mass,
                                                              Calibration cal, ScanSegment seg,
                                                              double magnitudeMj) {
        List<FeatureResult.ErrorContribution> out = new ArrayList<>();
        double baselineMj = Math.abs(bl.rmseMilliwatt());
        out.add(new FeatureResult.ErrorContribution("baseline-fit-rmse", baselineMj, "mW",
                "RMSE of signal against the accepted baseline"));
        double rateErr = 0.005; // 0.5% declared rate uncertainty
        out.add(new FeatureResult.ErrorContribution("scan-rate", rateErr * magnitudeMj, "mJ",
                "0.5% of enthalpy from declared scan-rate calibration"));
        if (mass != null) {
            out.add(new FeatureResult.ErrorContribution("sample-mass",
                    0.001 / mass.gramsValue() * magnitudeMj / 1000.0 * 1000.0, "mJ",
                    "0.1 mg balance resolution propagated to total enthalpy"));
        }
        if (cal != null) {
            out.add(new FeatureResult.ErrorContribution("temperature-calibration",
                    Math.abs(cal.offsetCelsius().value()), "\u00b0C",
                    "archived calibration offset applied to onset/endset"));
        }
        if (seg.nonUniformTemperatures()) {
            out.add(new FeatureResult.ErrorContribution("nonuniform-grid", 0.0, "mJ",
                    "no grid-spacing error: trapezoids use measured interval widths"));
        }
        return List.copyOf(out);
    }

    private int peakIndex(double[] excess) {
        int idx = 0;
        double best = Math.abs(excess[0]);
        for (int i = 1; i < excess.length; i++) {
            if (Math.abs(excess[i]) > best) { best = Math.abs(excess[i]); idx = i; }
        }
        return idx;
    }

    private int crossingIndex(double[] t, double[] e, int peak, boolean before, double level) {
        if (before) {
            for (int i = peak; i > 0; i--) if (Math.abs(e[i - 1]) <= level) return i - 1;
            return 0;
        }
        for (int i = peak; i < e.length - 1; i++) if (Math.abs(e[i + 1]) <= level) return i + 1;
        return e.length - 1;
    }

    private double interpolateCrossing(double[] t, double[] e, int idx, double level, boolean before) {
        int other = before ? Math.min(idx + 1, t.length - 1) : Math.max(idx - 1, 0);
        double a = Math.abs(e[idx]), b = Math.abs(e[other]);
        if (a == b) return t[idx];
        double f = (level - a) / (b - a);
        return t[idx] + f * (t[other] - t[idx]);
    }

    private FeatureResult indeterminate(Region region, BaselineResult bl, String code, String msg,
                                        List<Judgment> js) {
        js.add(Judgment.of(code, Judgment.Severity.REJECTED, region.regionId(), msg,
                RuleVersions.FEATURE_RULES, Map.of()));
        String id = Ids.derived("feat", RuleVersions.FEATURE_RULES, region.regionId(),
                bl == null ? "none" : bl.id(), code);
        return new FeatureResult(id, region.regionId(), bl == null ? null : bl.id(), region.kind(),
                null, null, null, null, null, EnthalpySign.AMBIGUOUS,
                NodeStatus.INDETERMINATE, null, null, RuleVersions.FEATURE_RULES,
                List.of(), js, List.of("seg:" + region.segmentId()), -1);
    }
}
