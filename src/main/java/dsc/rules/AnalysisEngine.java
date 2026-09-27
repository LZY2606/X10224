package dsc.rules;

import dsc.model.CurvePoint;
import dsc.model.Interval;
import dsc.model.ParsedCurve;
import dsc.model.Q;
import dsc.model.Segment;
import dsc.model.Source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs one interval end to end. All calculations are pure functions of the archived
 * sources and a fixed rule version, which makes every result independently
 * recomputable and gives the dependency graph exact inputs.
 */
public final class AnalysisEngine {

    public static final String RULE_VERSION =
            "rules:" + InstrumentParser.RULE_VERSION + "|" + Segmenter.RULE_VERSION
                    + "|" + Baselines.RULE_VERSION + "|" + ThermalAnalysis.RULE_VERSION;

    private AnalysisEngine() {
    }

    public record Extracted(double[] tCalC, double[] timeS, double[] orientedMw,
                            double[] rawTempC, double[] rawFlowMw, List<String> sampleRefs) {
    }

    public record IntervalAnalysis(
            String status,
            String errorCode,
            Extracted extracted,
            List<Baselines.Candidate> candidates,
            List<ThermalAnalysis.Result> results,
            List<String> diagnostics,
            String signConvention,
            String segmentDirection
    ) {
    }

    public static IntervalAnalysis analyze(
            ParsedCurve curve,
            List<Segment> segments,
            Interval interval,
            SourceSnapshot snapshot) {
        List<String> diagnostics = new ArrayList<>(curve.diagnostics());
        Segment segment = segments.stream()
                .filter(s -> s.segmentId().equals(interval.segmentId()))
                .findFirst().orElse(null);
        if (segment == null) {
            return fail("SEGMENT_MISSING", diagnostics, curve);
        }
        double lo = Math.min(interval.startC(), interval.endC());
        double hi = Math.max(interval.startC(), interval.endC());
        if (hi - lo < 1e-9) {
            return fail("EMPTY_INTERVAL", diagnostics, curve, segment);
        }
        if (lo < segment.minC() - 1e-6 || hi > segment.maxC() + 1e-6) {
            diagnostics.add("INTERVAL_CROSSES_SEGMENT_BOUNDARY:" + segment.cycleLabel());
            return fail("CROSS_SEGMENT_INTERVAL", diagnostics, curve, segment);
        }
        if (curve.signConvention().equals("ABSENT")) {
            diagnostics.add("DIRECTION_UNDERDETERMINED:no-sign-convention");
            return fail("DIRECTION_UNDERDETERMINED", diagnostics, curve, segment);
        }

        Double offset = snapshot.calibrationOffsetC();
        if (offset == null) {
            diagnostics.add("TEMPERATURE_CALIBRATION_UNCONVERTIBLE");
            offset = 0.0;
        }
        boolean heating = segment.detectedDirection().equals("HEATING");
        List<CurvePoint> in = new ArrayList<>();
        for (int i = segment.fromIndex(); i < segment.toIndex(); i++) {
            CurvePoint p = curve.points().get(i);
            Double t = p.temperature().canonical();
            if (t == null) {
                continue;
            }
            double tc = t + offset;
            if (tc >= lo - 1e-9 && tc <= hi + 1e-9) {
                in.add(p);
            }
        }
        if (in.size() < 4) {
            diagnostics.add("INSUFFICIENT_POINTS:" + in.size());
            return fail("EMPTY_INTERVAL", diagnostics, curve, segment);
        }
        if (!heating) {
            in = new ArrayList<>(in);
            java.util.Collections.reverse(in);
        }

        int n = in.size();
        double[] tCal = new double[n];
        double[] time = new double[n];
        double[] oriented = new double[n];
        double[] rawT = new double[n];
        double[] rawY = new double[n];
        List<String> refs = new ArrayList<>();
        double prevT = Double.NaN;
        for (int i = 0; i < n; i++) {
            CurvePoint p = in.get(i);
            double tc = p.temperature().canonical() + offset;
            tCal[i] = tc;
            rawT[i] = p.temperature().canonical();
            rawY[i] = p.heatFlow().canonical();
            if (p.time() != null && p.time().canonical() != null) {
                time[i] = p.time().canonical();
            } else if (i > 0) {
                time[i] = time[i - 1] + 60.0 * Math.abs(tc - prevT)
                        / Math.max(snapshot.rateCpm() == null ? 10 : snapshot.rateCpm(), 1e-9);
            }
            prevT = tc;
            // Exothermic-positive orientation. EXO_DOWN files need a flip; reversing
            // the cooling samples to ascending temperature flips the displayed axis, so
            // the same sign factor must NOT be inverted a second time.
            double sign = curve.signConvention().equals("EXO_UP") ? 1.0 : -1.0;
            oriented[i] = sign * p.heatFlow().canonical();
            refs.add(segment.cycleLabel() + "#" + p.index());
        }
        Extracted extracted = new Extracted(tCal, time, oriented, rawT, rawY, refs);

        double[] userLine = readUserTangent(snapshot, lo, hi);
        List<Baselines.Candidate> candidates = Baselines.generate(tCal, oriented, userLine);
        if (candidates.isEmpty()) {
            diagnostics.add("NO_BASELINE_CANDIDATE");
            return fail("EMPTY_INTERVAL", diagnostics, curve, segment);
        }
        double best = candidates.stream().mapToDouble(Baselines.Candidate::anchorRmse).min().orElse(0);
        double worst = candidates.stream().filter(Baselines.Candidate::reasonable)
                .mapToDouble(Baselines.Candidate::anchorRmse).max().orElse(0);
        double spread = spreadArea(tCal, time, oriented, candidates);
        if (spread == 0 && candidates.size() > 1) {
            diagnostics.add("BASELINE_SPREAD_ZERO");
        }
        if (candidates.stream().filter(Baselines.Candidate::reasonable).count() > 1) {
            diagnostics.add("MULTIPLE_REASONABLE_BASELINES:count="
                    + candidates.stream().filter(Baselines.Candidate::reasonable).count()
                    + ",anchorRmseBest=" + fmt(best) + ",worst=" + fmt(worst));
        }

        Double mass = snapshot.massMg();
        if (snapshot.mass() != null && (mass == null || mass <= 0)) {
            diagnostics.add("MASS_UNCONVERTIBLE_NORMALIZED_DERIVED_EXPIRED");
        }
        double rateFrac = snapshot.rateFractionUncertainty();
        double calFrac = snapshot.calibrationFractionUncertainty();

        List<ThermalAnalysis.Result> results = new ArrayList<>();
        for (Baselines.Candidate candidate : candidates) {
            List<String> local = new ArrayList<>();
            ThermalAnalysis.Result r = ThermalAnalysis.analyze(tCal, time, oriented,
                    candidate.values(), mass, rateFrac, calFrac, spread, local);
            results.add(r);
            diagnostics.addAll(local);
        }

        if (snapshot.rate() == null) {
            diagnostics.add("SCAN_RATE_NOT_ARCHIVED:time-from-instrument-column");
        }

        return new IntervalAnalysis("OK", null, extracted, candidates, results,
                diagnostics, curve.signConvention(), segment.detectedDirection());
    }

    private static double spreadArea(double[] t, double[] time, double[] y,
                                     List<Baselines.Candidate> candidates) {
        double minArea = Double.POSITIVE_INFINITY;
        double maxArea = Double.NEGATIVE_INFINITY;
        for (Baselines.Candidate c : candidates) {
            if (!c.reasonable()) {
                continue;
            }
            double area = 0;
            for (int i = 1; i < t.length; i++) {
                double dt = time[i] - time[i - 1];
                double r0 = y[i - 1] - c.values()[i - 1];
                double r1 = y[i] - c.values()[i];
                area += 0.5 * (r0 + r1) * dt / 1000.0;
            }
            minArea = Math.min(minArea, area);
            maxArea = Math.max(maxArea, area);
        }
        if (!Double.isFinite(minArea)) {
            return 0;
        }
        return Math.abs(maxArea - minArea);
    }

    private static double[] readUserTangent(SourceSnapshot snapshot, double lo, double hi) {
        Source tangent = snapshot.tangent();
        if (tangent == null) {
            return null;
        }
        Map<String, String> f = tangent.rawFields();
        Q x0 = SourceSnapshot.readQ(tangent, "tangent.t0C", Q.TEMP_C);
        Q y0 = SourceSnapshot.readQ(tangent, "tangent.y0", Q.HEAT_FLOW_MW);
        Q x1 = SourceSnapshot.readQ(tangent, "tangent.t1C", Q.TEMP_C);
        Q y1 = SourceSnapshot.readQ(tangent, "tangent.y1", Q.HEAT_FLOW_MW);
        if (x0 == null || y0 == null || x1 == null || y1 == null
                || !x0.valid() || !y0.valid() || !x1.valid() || !y1.valid()) {
            return null;
        }
        return new double[] {x0.canonical(), y0.canonical(), x1.canonical(), y1.canonical()};
    }

    private static IntervalAnalysis fail(String code, List<String> diags, ParsedCurve curve) {
        return new IntervalAnalysis("INVALID", code, null, List.of(), List.of(), diags,
                curve.signConvention(), "ABSENT");
    }

    private static IntervalAnalysis fail(String code, List<String> diags, ParsedCurve curve,
                                         Segment segment) {
        return new IntervalAnalysis("INVALID", code, null, List.of(), List.of(), diags,
                curve.signConvention(), segment.detectedDirection());
    }

    private static String fmt(double v) {
        return String.format("%.4g", v);
    }

    public static Map<String, Object> inputs(Interval interval, SourceSnapshot snapshot,
                                             String ruleVersion) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("intervalId", interval.intervalId());
        m.put("segmentId", interval.segmentId());
        m.put("rangeC", List.of(interval.startC(), interval.endC()));
        m.put("sources", snapshot.revisionMap());
        m.put("ruleVersion", ruleVersion);
        return m;
    }
}
