package dsc.graph;

import dsc.model.BaselineFit;
import dsc.model.Interval;
import dsc.model.Sample;
import dsc.model.Segment;
import dsc.model.SourceRecord;
import dsc.model.Trace;
import dsc.model.TransitionFeature;
import dsc.model.Types;
import dsc.derive.BaselineEngine;
import dsc.derive.FeatureExtractor;
import dsc.store.StateRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dependency-graph propagation.
 *
 * Graph (edges "depends on"):
 *   feature  -> baseline -> { interval, tangent pre, tangent post,
 *                             instrument output, scan rate, temperature calibration, mass }
 *
 * A revision marks downstream nodes either STILL_VALID (the changed input is outside their
 * closure) or EXPIRED. Mass revisions invalidate normalised (specific-enthalpy) readings only,
 * while raw integrated energy stays valid — that is encoded as a per-feature normalised flag.
 */
public final class RecomputeEngine {

    public static final class Derived {
        public final List<BaselineFit> baselines;
        public final List<FeatureExtractor.Result> featureResults;
        public final List<String> diagnostics;

        public Derived(List<BaselineFit> baselines, List<FeatureExtractor.Result> featureResults,
                       List<String> diagnostics) {
            this.baselines = baselines;
            this.featureResults = featureResults;
            this.diagnostics = diagnostics;
        }
    }

    private RecomputeEngine() {}

    /** Parses the instrument output of a segment under its archived convention/calibration. */
    public static Trace traceFor(StateRepository state, Segment segment) {
        SourceRecord raw = state.currentSourcesOf(segment.sampleId, segment.id,
                Types.SourceKind.INSTRUMENT_OUTPUT);
        SourceRecord cal = state.currentSourcesOf(segment.sampleId, segment.id,
                Types.SourceKind.TEMPERATURE_CALIBRATION);
        double offset = 0;
        if (cal != null) {
            Double v = cal.metadata.get("offsetK") == null ? null
                    : Double.valueOf(cal.metadata.get("offsetK"));
            if (v != null) offset = v;
        }
        Types.HeatFlowConvention convention = Types.HeatFlowConvention.valueOf(
                raw.metadata.getOrDefault("convention", Types.HeatFlowConvention.ENDO_UP.name()));
        String tempUnit = raw.metadata.getOrDefault("tempUnit", "degC");
        String hfUnit = raw.metadata.getOrDefault("hfUnit", "mW");
        return Trace.parse(segment.id, raw.rawText, tempUnit, hfUnit,
                convention, segment.direction, offset);
    }

    /** Builds every legal baseline candidate and all feature readings for an interval. */
    public static Derived derive(StateRepository state, String intervalId, int baselineVersion) {
        Interval interval = state.currentInterval(intervalId);
        Segment segment = state.segment(interval.segmentId);
        Trace trace = traceFor(state, segment);
        SourceRecord preT = tangent(state, intervalId, "pre");
        SourceRecord postT = tangent(state, intervalId, "post");
        List<String> diag = new ArrayList<>(trace.diagnostics);

        BaselineEngine.Window pre = window(preT);
        BaselineEngine.Window post = window(postT);
        List<BaselineFit> baselines;
        boolean empty = pre == null || post == null
                || windowSampleCount(trace, pre) == 0 && windowSampleCount(trace, post) == 0;
        if (empty) {
            baselines = List.of();
            diag.add("EMPTY_INTERVAL:tangent anchors contain no samples; no unique baseline exists");
        } else {
            baselines = BaselineEngine.candidates(intervalId, trace, interval, pre, post,
                    intervalId, baselineVersion);
            if (baselines.size() > 1) {
                diag.add("PARALLEL_BASELINES:" + baselines.size()
                        + " legitimate candidates retained with their divergence");
            }
        }

        SourceRecord rateR = state.currentSourcesOf(segment.sampleId, segment.id,
                Types.SourceKind.SCAN_RATE);
        SourceRecord massR = state.currentSourcesOf(segment.sampleId, null, Types.SourceKind.SAMPLE_MASS);
        double rate = Double.NaN;
        int rateVersion = 0;
        if (rateR != null) {
            rate = rateR.physical().toCanonical().orElse(Double.NaN);
            rateVersion = rateR.version;
            if (segment.direction == Types.ScanDirection.COOLING && rate > 0) rate = -rate;
        }
        double mass = Double.NaN;
        int massVersion = 0;
        if (massR != null) {
            mass = massR.physical().toCanonical().orElse(Double.NaN);
            massVersion = massR.version;
        }
        SourceRecord cal = state.currentSourcesOf(segment.sampleId, segment.id,
                Types.SourceKind.TEMPERATURE_CALIBRATION);
        int calVersion = cal == null ? 0 : cal.version;
        double calOffset = cal == null || cal.metadata.get("offsetK") == null
                ? 0 : Double.parseDouble(cal.metadata.get("offsetK"));

        List<FeatureExtractor.Result> results = new ArrayList<>();
        for (BaselineFit b : baselines) {
            FeatureExtractor.Result r = FeatureExtractor.extract(
                    b.id, trace, interval, b, rate, mass, massVersion, false,
                    calVersion, calOffset);
            results.add(r);
            diag.addAll(r.diagnostics.stream().filter(d -> !diag.contains(d)).toList());
        }
        return new Derived(baselines, results, diag);
    }

    private static SourceRecord tangent(StateRepository state, String intervalId, String side) {
        for (SourceRecord r : state.currentSourcesOfSample(seg(state, intervalId).sampleId)) {
            if (r.kind == Types.SourceKind.TANGENT_SELECTION
                    && intervalId.equals(r.metadata.get("intervalId"))
                    && side.equals(r.metadata.get("side"))) return r;
        }
        return null;
    }

    private static Segment seg(StateRepository state, String intervalId) {
        return state.segment(state.currentInterval(intervalId).segmentId);
    }

    private static BaselineEngine.Window window(SourceRecord tangent) {
        if (tangent == null) return null;
        double lo = Double.parseDouble(tangent.metadata.get("loK"));
        double hi = Double.parseDouble(tangent.metadata.get("hiK"));
        return new BaselineEngine.Window(lo, hi);
    }

    private static int windowSampleCount(Trace trace, BaselineEngine.Window w) {
        int n = 0;
        for (int i = 0; i < trace.size(); i++) {
            double t = trace.calibratedTempK(i);
            if (!Double.isNaN(t) && w.contains(t)) n++;
        }
        return n;
    }

    /**
     * Determines feature status after a source revision.
     * Returns feature id -> "EXPIRED_NORMALISED" | "EXPIRED_FULL" | "STILL_VALID" with a reason.
     */
    public static Map<String, String> propagation(StateRepository state,
                                                  Types.SourceKind changedKind,
                                                  String changedSegmentId,
                                                  int oldVersion, int newVersion) {
        Map<String, String> out = new LinkedHashMap<>();
        for (StateRepository.FeatureRow row : state.allFeatures()) {
            TransitionFeature f = row.feature;
            String intervalId = intervalOf(state, f.baselineId);
            String segId = state.currentInterval(intervalId).segmentId;
            boolean sameSegment = changedSegmentId == null || changedSegmentId.equals(segId);
            String status = switch (changedKind) {
                case SAMPLE_MASS -> "EXPIRED_NORMALISED:mass v" + oldVersion + "->v" + newVersion
                        + "; raw energy valid, specific enthalpy stale";
                case ATMOSPHERE -> "STILL_VALID:atmosphere does not enter numerical derivation";
                case INSTRUMENT_OUTPUT, SCAN_RATE, TEMPERATURE_CALIBRATION ->
                        sameSegment ? "EXPIRED_FULL:" + changedKind + " revised"
                                : "STILL_VALID:revision scoped to another segment";
                case TANGENT_SELECTION -> "EXPIRED_FULL:tangent anchors revised";
            };
            out.put(f.id, status);
        }
        return out;
    }

    private static String intervalOf(StateRepository state, String baselineId) {
        for (Interval iv : allIntervals(state)) {
            if (state.baselinesOfInterval(iv.id).stream().anyMatch(b -> b.id.equals(baselineId))) {
                return iv.id;
            }
        }
        throw new IllegalStateException("orphan baseline " + baselineId);
    }

    private static List<Interval> allIntervals(StateRepository state) {
        List<Interval> out = new ArrayList<>();
        for (Sample s : state.samples()) out.addAll(state.segmentsOf(s.id).stream()
                .flatMap(seg -> state.intervalsOf(seg.id).stream()).toList());
        return out;
    }
}
