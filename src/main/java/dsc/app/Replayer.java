package dsc.app;

import dsc.core.BaselineEngine;
import dsc.core.DirectionDetector;
import dsc.core.EventCharacterizer;
import dsc.core.Hashes;
import dsc.domain.Atmosphere;
import dsc.domain.BaselineCandidate;
import dsc.domain.Direction;
import dsc.domain.Event;
import dsc.domain.EventType;
import dsc.domain.Interval;
import dsc.domain.Qty;
import dsc.domain.Sample;
import dsc.domain.Segment;
import dsc.domain.TangentSelection;
import dsc.domain.TransitionResult;
import dsc.domain.TransitionType;
import dsc.domain.Versioned;
import dsc.store.RawStore;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds a {@link StateSnapshot} by folding the event log. Because every
 * derived value stores the exact versions it consumed, replaying up to an old
 * event restores that moment's dependency versions and visible conclusions
 * rather than overwriting history with today's recomputation.
 */
public final class Replayer {

    private final Connection conn;

    public Replayer(Connection conn) {
        this.conn = conn;
    }

    public StateSnapshot replay(List<Event> events) {
        Sample sample = null;
        Map<String, Segment> segments = new LinkedHashMap<>();
        Map<String, Segment> importedByEvent = new LinkedHashMap<>();
        Map<String, String> exoBySegment = new LinkedHashMap<>();
        Map<String, String> measuredDirection = new LinkedHashMap<>();

        List<Versioned<Qty>> masses = new ArrayList<>();
        List<Versioned<Qty>> rates = new ArrayList<>();
        List<Versioned<Atmosphere>> atmo = new ArrayList<>();
        List<Versioned<Qty>> calib = new ArrayList<>();
        Map<String, List<Versioned<TangentSelection>>> tangents = new LinkedHashMap<>();

        List<Interval> intervals = new ArrayList<>();
        Map<String, List<BaselineCandidate>> cands = new LinkedHashMap<>();
        Map<String, List<String>> adopted = new LinkedHashMap<>();
        Map<String, List<String>> rejected = new LinkedHashMap<>();
        Map<String, TransitionResult> transitions = new LinkedHashMap<>();
        Map<String, String> intervalSegment = new LinkedHashMap<>();
        Map<String, TransitionType> intervalType = new LinkedHashMap<>();
        Map<String, Boolean> intervalOverlap = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();

        for (Event e : events) {
            switch (e.type()) {
                case SAMPLE_REGISTERED -> sample = new Sample(e.payload("sample_id"),
                        e.payload("code"), e.payload("material"), e.payload("note"));

                case RAW_BATCH_IMPORTED -> {
                    Map<Segment, String> loaded;
                    try {
                        loaded = RawStore.loadForEvents(conn, List.of(e.id()));
                    } catch (Exception ex) {
                        diagnostics.add("BLOCKER: raw rows missing for event " + e.id());
                        break;
                    }
                    for (Map.Entry<Segment, String> en : loaded.entrySet()) {
                        Segment seg = en.getKey();
                        segments.put(seg.id(), seg);
                        exoBySegment.put(seg.id(), en.getValue());
                        DirectionDetector.Result dr = DirectionDetector.detect(
                                seg.points(), seg.declaredDirection());
                        measuredDirection.put(seg.id(), dr.measured().code());
                        if (dr.contradictsDeclared()) {
                            diagnostics.add("WARNING: segment " + seg.label()
                                    + " declared " + seg.declaredDirection().code()
                                    + " but data shows " + dr.measured().code()
                                    + "; measured direction used (file names never decide)");
                        }
                    }
                    importedByEvent.put(e.id(), null);
                }

                case MASS_SET -> masses.add(new Versioned<>(parseVersion(e, masses.size()),
                        Qty.of(Double.parseDouble(e.payload("value")), e.payload("unit")),
                        e.payload("origin"), e.payload("comment")));
                case RATE_SET -> rates.add(new Versioned<>(parseVersion(e, rates.size()),
                        Qty.of(Double.parseDouble(e.payload("value")), e.payload("unit")),
                        e.payload("origin"), e.payload("comment")));
                case CALIBRATION_SET -> calib.add(new Versioned<>(parseVersion(e, calib.size()),
                        Qty.of(Double.parseDouble(e.payload("offset_value")), e.payload("offset_unit")),
                        e.payload("origin"), e.payload("comment")));
                case ATMOSPHERE_SET -> {
                    Qty flow = e.payload("flow_value") == null ? null
                            : Qty.of(Double.parseDouble(e.payload("flow_value")),
                                    e.payload("flow_unit"));
                    atmo.add(new Versioned<>(parseVersion(e, atmo.size()),
                            new Atmosphere(e.payload("gas"), flow, e.payload("note")),
                            e.payload("origin"), e.payload("comment")));
                }

                case SEGMENT_DIRECTION_DETERMINED ->
                        measuredDirection.put(e.payload("segment_id"), e.payload("measured"));

                case TANGENT_SET -> {
                    TangentSelection ts = new TangentSelection(e.payload("tangent_id"),
                            e.payload("segment_id"), e.payload("side"),
                            Integer.parseInt(e.payload("start_index")),
                            Integer.parseInt(e.payload("end_index")));
                    tangents.computeIfAbsent(tangentKey(ts.segmentId(), ts.side()), k -> new ArrayList<>())
                            .add(new Versioned<>(parseVersionTangent(e, tangents, ts),
                                    ts, e.actor(), e.payload("comment")));
                }

                case INTERVAL_DRAWN -> {
                    String segId = e.payload("segment_id");
                    if (Boolean.parseBoolean(e.payload("cross_segment"))) {
                        diagnostics.add("BLOCKER: interval " + e.payload("interval_id")
                                + " rejected: heating and cooling segments are never mixed");
                        break;
                    }
                    double start = Double.parseDouble(e.payload("start_k"));
                    double end = Double.parseDouble(e.payload("end_k"));
                    if (!(end > start)) {
                        diagnostics.add("BLOCKER: interval " + e.payload("interval_id") + " is empty/inverted");
                        break;
                    }
                    Interval iv = new Interval(e.payload("interval_id"), segId, start, end);
                    intervals.removeIf(x -> x.id().equals(iv.id()));
                    intervals.add(iv);
                    intervalSegment.put(iv.id(), segId);
                    intervalType.put(iv.id(), TransitionType.fromCode(e.payload("transition_type")));
                    intervalOverlap.put(iv.id(), Boolean.parseBoolean(e.payload("overlap")));
                }

                case CROSS_SEGMENT_REJECTED ->
                        diagnostics.add("BLOCKER: cross-segment window rejected on segment pair "
                                + e.payload("segment_ids") + ": heating/cooling not mixed");

                case EMPTY_INTERVAL_CHECKED -> {
                    int count = Integer.parseInt(e.payload("point_count"));
                    if (count == 0) {
                        diagnostics.add("BLOCKER: interval " + e.payload("interval_id")
                                + " contains no samples; no baseline or integral produced");
                    }
                }

                case BASELINES_COMPUTED -> {
                    String ivId = e.payload("interval_id");
                    String candidateId = e.payload("candidate_id");
                    if (candidateId != null) {
                        BaselineCandidate stored = decodeCandidate(e);
                        cands.computeIfAbsent(ivId, k -> new ArrayList<>())
                                .removeIf(c -> c.id().equals(candidateId));
                        cands.get(ivId).add(stored);
                    }
                }

                case AMBIGUOUS_BASELINES -> {
                    String ivId = e.payload("interval_id");
                    List<BaselineCandidate> list = cands.getOrDefault(ivId, List.of());
                    if (list.size() > 1) {
                        diagnostics.add("multiple legal baselines retained for " + ivId
                                + " (" + list.size() + " candidates)");
                    }
                }

                case OVERLAP_DETECTED -> intervalOverlap.put(e.payload("interval_id"), true);

                case BASELINE_ADOPTED -> adopted.computeIfAbsent(e.payload("interval_id"),
                        k -> new ArrayList<>()).add(e.payload("candidate_id"));
                case BASELINE_REJECTED -> rejected.computeIfAbsent(e.payload("interval_id"),
                        k -> new ArrayList<>()).add(e.payload("candidate_id"));

                case PARAMETER_ADJUSTED -> {
                    // Parameter adjustment is itself a source revision event
                    // (mass/rate/tangent), handled above; this event records intent.
                }

                case TRANSITION_COMPUTED -> {
                    TransitionResult tr = TransitionCodec.decode(e);
                    transitions.put(tr.id(), tr);
                }

                case DERIVATION_EXPIRED ->
                        diagnostics.add("derivation expired: " + e.payload("derived_id")
                                + " (" + e.payload("reason") + ")");

                case CONFLICTING_DECISION, case CONFLICT_DETECTED, case CASE_NOTE -> {
                    if (e.payload("message") != null) {
                        diagnostics.add("[" + e.type() + "] " + e.payload("message"));
                    }
                }
            }
        }

        return new StateSnapshot(sample, segments, exoBySegment, measuredDirection,
                masses, rates, atmo, calib, flattenTangents(tangents),
                List.copyOf(intervals), cands, adopted, rejected, transitions,
                List.copyOf(events), List.copyOf(diagnostics));
    }

    private static int parseVersion(Event e, int current) {
        String v = e.payload("version");
        return v == null ? current + 1 : Integer.parseInt(v);
    }

    private static int parseVersionTangent(Event e,
                                           Map<String, List<Versioned<TangentSelection>>> all,
                                           TangentSelection ts) {
        var list = all.get(tangentKey(ts.segmentId(), ts.side()));
        return list == null ? 1 : list.size() + 1;
    }

    private static String tangentKey(String segmentId, String side) {
        return segmentId + "|" + side;
    }

    private Map<String, List<Versioned<TangentSelection>>> flattenTangents(
            Map<String, List<Versioned<TangentSelection>>> in) {
        return in;
    }

    private static TangentSelection currentTangent(
            Map<String, List<Versioned<TangentSelection>>> tangents,
            String segmentId, String side) {
        var list = tangents.get(tangentKey(segmentId, side));
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(list.size() - 1).value();
    }

    private static Interval findInterval(List<Interval> intervals, String id) {
        return intervals.stream().filter(i -> i.id().equals(id)).findFirst().orElse(null);
    }

    private static int[] rangeIndices(Segment seg, double startK, double endK) {
        int from = -1;
        int to = -1;
        for (RawPointLike p : points(seg)) {
            if (p.computable && p.tempK >= startK && p.tempK <= endK) {
                if (from < 0) from = p.index;
                to = p.index;
            }
        }
        if (from < 0) {
            return new int[]{0, -1};
        }
        return new int[]{from, to};
    }

    private record RawPointLike(int index, double tempK, boolean computable) {
    }

    private static List<RawPointLike> points(Segment seg) {
        List<RawPointLike> out = new ArrayList<>();
        for (var p : seg.points()) {
            if (p.computable()) {
                out.add(new RawPointLike(p.index(), p.canonicalTempK(), true));
            }
        }
        return out;
    }

    public static String stableTransitionId(String intervalId, String baselineId,
                                            int massVersion, int rateVersion,
                                            int calibVersion, int rawVersion, String tangentSetHash) {
        return Hashes.id("tr", String.join("|", intervalId, baselineId,
                String.valueOf(massVersion), String.valueOf(rateVersion),
                String.valueOf(calibVersion), String.valueOf(rawVersion), tangentSetHash));
    }

    private static BaselineCandidate decodeCandidate(Event e) {
        List<String> diag = e.payload("diagnostics") == null || e.payload("diagnostics").isEmpty()
                ? List.of() : Arrays.asList(e.payload("diagnostics").split(";;", -1));
        return new BaselineCandidate(
                e.payload("candidate_id"), e.payload("interval_id"),
                dsc.domain.BaselineKind.fromCode(e.payload("kind")),
                e.payload("label"), e.payload("pre_tangent_id"), e.payload("post_tangent_id"),
                parse(e, "slope_wk"), parse(e, "offset_w"),
                parse(e, "pre_slope_wk"), parse(e, "post_slope_wk"),
                parse(e, "pre_offset_w"), parse(e, "post_offset_w"),
                diag);
    }

    private static double parse(Event e, String key) {
        String v = e.payload(key);
        return v == null || v.isEmpty() ? 0 : Double.parseDouble(v);
    }
}
