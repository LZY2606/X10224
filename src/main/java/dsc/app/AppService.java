package dsc.app;

import dsc.core.BaselineEngine;
import dsc.core.DependencyGraph;
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
import dsc.domain.RawPoint;
import dsc.domain.Sample;
import dsc.domain.Segment;
import dsc.domain.TangentSelection;
import dsc.domain.TransitionResult;
import dsc.domain.TransitionType;
import dsc.domain.Versioned;
import dsc.store.EventStore;
import dsc.store.RawStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application facade: every command appends events atomically and records the
 * machine-readable judgments (reversed flow, non-uniform grid, overlap, cycle
 * switch, mass revision expiry, parallel baselines, empty interval). Reads
 * replay the log; dependency-graph propagation marks only affected derivations.
 */
public final class AppService {

    public static final String MAIN_BRANCH = "main";

    private final EventStore store;

    public AppService(EventStore store) {
        this.store = store;
    }

    public EventStore store() {
        return store;
    }

    private String now() {
        return Instant.now().toString();
    }

    private String newId(String prefix, String content) {
        return Hashes.id(prefix, content + "|" + store.headSeq(MAIN_BRANCH) + "|" + now());
    }

    private EventStore.EventDraft draft(String id, EventType type, String actor,
                                        Map<String, String> payload) {
        return EventStore.EventDraft.of(id, type, actor, now(), payload);
    }

    private EventStore.AppendResult append(String actor, long expectedHead,
                                           List<EventStore.EventDraft> drafts,
                                           EventStore.WriteCallback writes) {
        return store.append(MAIN_BRANCH, expectedHead, drafts, writes);
    }

    public StateSnapshot current() {
        return at(store.headSeq(MAIN_BRANCH));
    }

    public StateSnapshot at(long seq) {
        List<Event> events = store.readAllUpTo(MAIN_BRANCH, seq);
        return new Replayer(store.connection()).replay(events);
    }

    // ---- sample ----------------------------------------------------------

    public EventStore.AppendResult registerSample(String sampleId, String code,
                                                  String material, String note,
                                                  String actor, long expectedHead) {
        String id = "ev_sample_" + sampleId;
        Map<String, String> p = new LinkedHashMap<>();
        p.put("sample_id", sampleId);
        p.put("code", nz(code));
        p.put("material", nz(material));
        p.put("note", nz(note));
        return append(actor, expectedHead, List.of(draft(id, EventType.SAMPLE_REGISTERED, actor, p)), null);
    }

    // ---- raw import ------------------------------------------------------

    public EventStore.AppendResult importRaw(String batchLabel, Segment segment,
                                             String exoConvention, String actor, long expectedHead) {
        String eventId = Hashes.id("ev_raw", segment.id() + "|" + batchLabel
                + "|" + store.headSeq(MAIN_BRANCH) + "|" + now());
        Map<String, String> p = new LinkedHashMap<>();
        p.put("batch_label", nz(batchLabel));
        p.put("segment_id", segment.id());
        p.put("source_file", nz(segment.sourceFile()));
        p.put("cycle_index", String.valueOf(segment.cycleIndex()));
        p.put("declared_direction", segment.declaredDirection().code());
        p.put("exo_convention", nz(exoConvention));
        p.put("point_count", String.valueOf(segment.points().size()));

        DirectionDetector.Result dr = DirectionDetector.detect(
                segment.points(), segment.declaredDirection());
        List<EventStore.EventDraft> drafts = new ArrayList<>();
        drafts.add(draft(eventId, EventType.RAW_BATCH_IMPORTED, actor, p));

        Map<String, String> dp = new LinkedHashMap<>();
        dp.put("segment_id", segment.id());
        dp.put("declared", segment.declaredDirection().code());
        dp.put("measured", dr.measured().code());
        dp.put("slope_k_per_s", String.valueOf(dr.slopeKPerS()));
        dp.put("r2", String.valueOf(dr.r2()));
        dp.put("confident", String.valueOf(dr.confident()));
        dp.put("rule", "direction derived from temperature(time); filename never used");
        drafts.add(draft(Hashes.id("ev_dir", eventId),
                EventType.SEGMENT_DIRECTION_DETERMINED, actor, dp));

        if (dr.contradictsDeclared()) {
            drafts.add(noteDraft(actor, EventType.CASE_NOTE,
                    "segment " + segment.label() + " header says " + segment.declaredDirection().code()
                            + " but data is " + dr.measured().code() + "; measured value wins"));
        }
        // reversed heat-flow convention check recorded as machine-readable fact
        if ("EXO_DOWN".equalsIgnoreCase(exoConvention)) {
            drafts.add(noteDraft(actor, EventType.CASE_NOTE,
                    "reversed heat-flow axis on " + segment.label()
                            + ": sign assignment uses exo-down convention, not file name"));
        }
        return append(actor, expectedHead, drafts, conn -> RawStore.writeSegment(conn, eventId, segment, exoConvention));
    }

    private EventStore.EventDraft noteDraft(String actor, EventType type, String message) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("message", message);
        return draft(Hashes.id("ev_note", message + "|" + now()), type, actor, p);
    }

    // ---- direct sources, independently versioned -------------------------

    public EventStore.AppendResult setMass(double value, String unit, String origin,
                                           String comment, String actor, long expectedHead) {
        StateSnapshot before = current();
        int version = before.massVersions().size() + 1;
        String id = Hashes.id("ev_mass", value + unit + "|" + version + "|" + now());
        Map<String, String> p = new LinkedHashMap<>();
        p.put("version", String.valueOf(version));
        p.put("value", String.valueOf(value));
        p.put("unit", unit);
        p.put("origin", nz(origin));
        p.put("comment", nz(comment));
        List<EventStore.EventDraft> drafts = new ArrayList<>();
        drafts.add(draft(id, EventType.MASS_SET, actor, p));
        // Mass revision expires every normalized derivation built on the old mass.
        for (TransitionResult tr : before.transitions().values()) {
            Map<String, String> ep = new LinkedHashMap<>();
            ep.put("derived_id", tr.id());
            ep.put("reason", "mass revision v" + tr.massVersion() + "->v" + version
                    + ": all mass-normalized derivations are expired");
            ep.put("scope", "normalized_derivations");
            drafts.add(draft(Hashes.id("ev_exp", tr.id() + "|" + version + "|" + now()),
                    EventType.DERIVATION_EXPIRED, actor, ep));
        }
        drafts.add(noteDraft(actor, EventType.CASE_NOTE,
                "mass revision archived as v" + version + "; previous versions retained"));
        return append(actor, expectedHead, drafts, null);
    }

    public EventStore.AppendResult setRate(double value, String unit, String origin,
                                           String comment, String actor, long expectedHead) {
        StateSnapshot before = current();
        int version = before.rateVersions().size() + 1;
        String id = Hashes.id("ev_rate", value + unit + "|" + version + "|" + now());
        Map<String, String> p = new LinkedHashMap<>();
        p.put("version", String.valueOf(version));
        p.put("value", String.valueOf(value));
        p.put("unit", unit);
        p.put("origin", nz(origin));
        p.put("comment", nz(comment));
        return append(actor, expectedHead, List.of(draft(id, EventType.RATE_SET, actor, p)), null);
    }

    public EventStore.AppendResult setAtmosphere(String gas, Double flowValue, String flowUnit,
                                                 String note, String actor, long expectedHead) {
        StateSnapshot before = current();
        int version = before.atmosphereVersions().size() + 1;
        String id = Hashes.id("ev_atmo", gas + "|" + version + "|" + now());
        Map<String, String> p = new LinkedHashMap<>();
        p.put("version", String.valueOf(version));
        p.put("gas", nz(gas));
        if (flowValue != null) {
            p.put("flow_value", String.valueOf(flowValue));
            p.put("flow_unit", nz(flowUnit));
        }
        p.put("note", nz(note));
        return append(actor, expectedHead, List.of(draft(id, EventType.ATMOSPHERE_SET, actor, p)), null);
    }

    public EventStore.AppendResult setCalibration(double offsetValue, String offsetUnit,
                                                  String origin, String comment,
                                                  String actor, long expectedHead) {
        StateSnapshot before = current();
        int version = before.calibrationVersions().size() + 1;
        String id = Hashes.id("ev_cal", offsetValue + offsetUnit + "|" + version + "|" + now());
        Map<String, String> p = new LinkedHashMap<>();
        p.put("version", String.valueOf(version));
        p.put("offset_value", String.valueOf(offsetValue));
        p.put("offset_unit", offsetUnit);
        p.put("origin", nz(origin));
        p.put("comment", nz(comment));
        return append(actor, expectedHead, List.of(draft(id, EventType.CALIBRATION_SET, actor, p)), null);
    }

    public EventStore.AppendResult setTangent(String segmentId, String side,
                                              int startIndex, int endIndex,
                                              String comment, String actor, long expectedHead) {
        StateSnapshot snap = current();
        var list = snap.tangentVersions().getOrDefault(segmentId + "|" + side, List.of());
        int version = list.size() + 1;
        String tangentId = Hashes.id("tan", segmentId + "|" + side + "|"
                + startIndex + "-" + endIndex);
        String id = Hashes.id("ev_tan", tangentId + "|v" + version + "|" + now());
        Map<String, String> p = new LinkedHashMap<>();
        p.put("version", String.valueOf(version));
        p.put("tangent_id", tangentId);
        p.put("segment_id", segmentId);
        p.put("side", side);
        p.put("start_index", String.valueOf(startIndex));
        p.put("end_index", String.valueOf(endIndex));
        p.put("comment", nz(comment));
        return append(actor, expectedHead, List.of(draft(id, EventType.TANGENT_SET, actor, p)), null);
    }

    // ---- interval and analysis ------------------------------------------

    /**
     * Draw / drag an interval. Temperature window is expressed on a single
     * segment's coordinate basis; a cross-segment request is rejected with a
     * machine-readable event instead of silently joining heating and cooling.
     */
    public EventStore.AppendResult drawInterval(String segmentIdA, String segmentIdBOrNull,
                                                double startK, double endK,
                                                TransitionType type, boolean overlap,
                                                String actor, long expectedHead) {
        StateSnapshot snap = current();
        String intervalId = Hashes.id("iv", segmentIdA + "|" + startK + "|" + endK + "|" + type);
        List<EventStore.EventDraft> drafts = new ArrayList<>();

        boolean cross = segmentIdBOrNull != null && !segmentIdBOrNull.equals(segmentIdA);
        boolean cycleSwitch = false;
        if (cross) {
            Segment a = snap.segments().get(segmentIdA);
            Segment b = snap.segments().get(segmentIdBOrNull);
            String da = snap.segmentMeasuredDirection().get(segmentIdA);
            String db = snap.segmentMeasuredDirection().get(segmentIdBOrNull);
            cycleSwitch = a != null && b != null && a.cycleIndex() != b.cycleIndex();
            Map<String, String> rp = new LinkedHashMap<>();
            rp.put("interval_id", intervalId);
            rp.put("segment_ids", segmentIdA + "," + segmentIdBOrNull);
            rp.put("directions", nz(da) + "," + nz(db));
            rp.put("cycle_switch", String.valueOf(cycleSwitch));
            rp.put("rule", "heating and cooling segments are never mixed in one integral");
            drafts.add(draft(Hashes.id("ev_xseg", intervalId + "|" + now()),
                    EventType.CROSS_SEGMENT_REJECTED, actor, rp));
            return append(actor, expectedHead, drafts, null);
        }

        Segment seg = snap.segments().get(segmentIdA);
        int pointCount = 0;
        boolean nonUniform = false;
        if (seg != null) {
            double lastDelta = Double.NaN;
            Double prev = null;
            for (RawPoint pt : seg.points()) {
                if (!pt.computable()) continue;
                double t = pt.canonicalTempK();
                if (t >= startK && t <= endK) pointCount++;
                if (prev != null) {
                    double d = t - prev;
                    if (!Double.isNaN(lastDelta) && Math.abs(d - lastDelta) > 1e-6 * Math.max(1, Math.abs(lastDelta))) {
                        nonUniform = true;
                    }
                    lastDelta = d;
                }
                prev = t;
            }
        }

        Map<String, String> ip = new LinkedHashMap<>();
        ip.put("interval_id", intervalId);
        ip.put("segment_id", segmentIdA);
        ip.put("start_k", String.valueOf(startK));
        ip.put("end_k", String.valueOf(endK));
        ip.put("transition_type", type.code());
        ip.put("overlap", String.valueOf(overlap));
        ip.put("cross_segment", "false");
        ip.put("point_count", String.valueOf(pointCount));
        ip.put("non_uniform", String.valueOf(nonUniform));
        drafts.add(draft(Hashes.id("ev_iv", intervalId + "|" + now()),
                EventType.INTERVAL_DRAWN, actor, ip));

        Map<String, String> ep = new LinkedHashMap<>();
        ep.put("interval_id", intervalId);
        ep.put("point_count", String.valueOf(pointCount));
        ep.put("rule", "empty interval yields no baseline and no integral");
        drafts.add(draft(Hashes.id("ev_empty", intervalId + "|" + pointCount + "|" + now()),
                EventType.EMPTY_INTERVAL_CHECKED, actor, ep));

        if (overlap) {
            Map<String, String> op = new LinkedHashMap<>();
            op.put("interval_id", intervalId);
            op.put("rule", "overlapping transitions keep all legal baseline partitions");
            drafts.add(draft(Hashes.id("ev_ovl", intervalId + "|" + now()),
                    EventType.OVERLAP_DETECTED, actor, op));
        }
        if (nonUniform) {
            drafts.add(noteDraft(actor, EventType.CASE_NOTE,
                    "non-uniform temperature grid in " + intervalId
                            + ": integration uses time abscissa"));
        }
        EventStore.AppendResult res = append(actor, expectedHead, drafts, null);
        if (res.ok() && pointCount > 0) {
            return computeBaselines(intervalId, actor, res.headSeq());
        }
        return res;
    }

    public EventStore.AppendResult computeBaselines(String intervalId, String actor, long expectedHead) {
        StateSnapshot snap = current();
        Interval iv = snap.intervals().stream().filter(i -> i.id().equals(intervalId)).findFirst().orElse(null);
        if (iv == null) {
            return expectedHead == store.headSeq(MAIN_BRANCH)
                    ? EventStore.AppendResult.conflict(store.headSeq(MAIN_BRANCH))
                    : EventStore.AppendResult.conflict(store.headSeq(MAIN_BRANCH));
        }
        Segment seg = snap.segments().get(iv.segmentId());
        TangentSelection pre = currentTangent(snap, iv.segmentId(), "pre");
        TangentSelection post = currentTangent(snap, iv.segmentId(), "post");
        int[] idx = rangeIndices(seg, iv.startK(), iv.endK());
        TransitionType type = snap.intervals().stream()
                .filter(i -> i.id().equals(intervalId)).findFirst().isEmpty()
                ? TransitionType.UNRESOLVED : typeOf(snap, intervalId);
        boolean overlap = isOverlap(snap, intervalId);
        List<BaselineCandidate> list = (pre == null || post == null || idx[1] <= idx[0])
                ? List.of()
                : BaselineEngine.candidates(new BaselineEngine.Inputs(intervalId, type,
                        seg.points(), idx[0], idx[1], pre, post, overlap));

        List<EventStore.EventDraft> drafts = new ArrayList<>();
        for (BaselineCandidate c : list) {
            Map<String, String> bp = new LinkedHashMap<>();
            bp.put("interval_id", intervalId);
            bp.put("candidate_id", c.id());
            bp.put("kind", c.kind().code());
            bp.put("label", c.label());
            bp.put("pre_tangent_id", nz(c.preTangentId()));
            bp.put("post_tangent_id", nz(c.postTangentId()));
            bp.put("slope_wk", String.valueOf(c.slopeWK()));
            bp.put("offset_w", String.valueOf(c.offsetW()));
            bp.put("pre_slope_wk", String.valueOf(c.preSlopeWK()));
            bp.put("post_slope_wk", String.valueOf(c.postSlopeWK()));
            bp.put("pre_offset_w", String.valueOf(c.preOffsetW()));
            bp.put("post_offset_w", String.valueOf(c.postOffsetW()));
            bp.put("diagnostics", String.join(";;", c.diagnostics() == null ? List.of() : c.diagnostics()));
            drafts.add(draft(Hashes.id("ev_bl", c.id() + "|" + now()),
                    EventType.BASELINES_COMPUTED, actor, bp));
        }
        if (list.size() > 1) {
            Map<String, String> ap = new LinkedHashMap<>();
            ap.put("interval_id", intervalId);
            ap.put("candidate_ids", String.join(",", list.stream().map(BaselineCandidate::id).toList()));
            ap.put("rule", "multiple reasonable baselines retained; adoption is not deletion");
            drafts.add(draft(Hashes.id("ev_amb", intervalId + "|" + now()),
                    EventType.AMBIGUOUS_BASELINES, actor, ap));
        }
        return append(actor, expectedHead, drafts, null);
    }

    public EventStore.AppendResult adoptBaseline(String intervalId, String candidateId,
                                                 String actor, long expectedHead, boolean conflicting) {
        List<EventStore.EventDraft> drafts = new ArrayList<>();
        Map<String, String> p = new LinkedHashMap<>();
        p.put("interval_id", intervalId);
        p.put("candidate_id", candidateId);
        p.put("adopted", "true");
        drafts.add(draft(Hashes.id("ev_adopt", intervalId + "|" + candidateId + "|" + now()),
                EventType.BASELINE_ADOPTED, actor, p));
        if (conflicting) {
            Map<String, String> cp = new LinkedHashMap<>();
            cp.put("interval_id", intervalId);
            cp.put("candidate_id", candidateId);
            cp.put("message", "conflicting human decision recorded; prior candidate retained, not overwritten");
            drafts.add(draft(Hashes.id("ev_conflict_dec", intervalId + "|" + now()),
                    EventType.CONFLICTING_DECISION, actor, cp));
        }
        EventStore.AppendResult res = append(actor, expectedHead, drafts, null);
        if (res.ok()) {
            return computeTransition(intervalId, candidateId, actor, res.headSeq());
        }
        return res;
    }

    public EventStore.AppendResult rejectBaseline(String intervalId, String candidateId,
                                                  String actor, long expectedHead) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("interval_id", intervalId);
        p.put("candidate_id", candidateId);
        p.put("rejected", "true");
        return append(actor, expectedHead, List.of(
                draft(Hashes.id("ev_rej", intervalId + "|" + candidateId + "|" + now()),
                        EventType.BASELINE_REJECTED, actor, p)), null);
    }

    // ---- transition computation -----------------------------------------

    public EventStore.AppendResult computeTransition(String intervalId, String candidateId,
                                                     String actor, long expectedHead) {
        StateSnapshot snap = current();
        Interval iv = snap.intervals().stream().filter(i -> i.id().equals(intervalId))
                .findFirst().orElse(null);
        if (iv == null) {
            return EventStore.AppendResult.conflict(store.headSeq(MAIN_BRANCH));
        }
        Segment seg = snap.segments().get(iv.segmentId());
        BaselineCandidate chosen = snap.candidatesByInterval().getOrDefault(intervalId, List.of())
                .stream().filter(c -> c.id().equals(candidateId)).findFirst().orElse(null);
        if (chosen == null) {
            return EventStore.AppendResult.conflict(store.headSeq(MAIN_BRANCH));
        }
        Versioned<Qty> mass = snap.currentMass();
        Versioned<Qty> rate = snap.currentRate();
        Versioned<Qty> calib = snap.currentCalibration();
        if (mass == null) {
            throw new IllegalStateException("sample mass required before enthalpy normalization");
        }
        int[] idx = rangeIndices(seg, iv.startK(), iv.endK());
        double junctionK = 0.5 * (iv.startK() + iv.endK());
        double massKg = mass.value().convertible() ? mass.value().canonical() : Double.NaN;
        EventCharacterizer.ExoConvention exo = parseExo(snap.segmentExoConvention().get(seg.id()));
        EventCharacterizer.Features f = EventCharacterizer.characterize(
                seg.points(), idx[0], idx[1], chosen, junctionK,
                typeOf(snap, intervalId), massKg, exo);

        // baseline spread across parallel candidates drives the error budget
        double spread = baselineSpreadJG(snap, intervalId, idx, seg, junctionK, typeOf(snap, intervalId), massKg);
        List<String> notes = new ArrayList<>();
        notes.add("mass " + mass.version() + " (" + mass.value().format() + ")");
        notes.add("rate " + (rate == null ? "unset" : rate.version()));
        notes.add("calibration " + (calib == null ? "unset" : calib.version()));
        var errs = EventCharacterizer.errorBudget(0.002, 0.001,
                calib == null ? 0 : Math.abs(calib.value().canonical()), spread,
                samplingUncertainty(seg, idx), notes);

        int rawVersion = rawBatchVersion(snap, seg.id());
        int tangentSet = tangentSetHash(snap, seg.id());
        String dataHash = Hashes.sha256(dataContent(seg, idx));
        String trId = Replayer.stableTransitionId(intervalId, candidateId,
                mass.version(), rate == null ? 0 : rate.version(),
                calib == null ? 0 : calib.version(), rawVersion, String.valueOf(tangentSet));

        TransitionResult tr = new TransitionResult(trId, intervalId, seg.id(), candidateId,
                typeOf(snap, intervalId), f.onsetK(), f.peakK(), f.endsetK(), f.peakFlowW(),
                f.stepHeightWK(), f.midpointK(), f.specificEnthalpyJG(), f.sign(), errs,
                f.diagnostics(), mass.version(), rate == null ? 0 : rate.version(),
                calib == null ? 0 : calib.version(), rawVersion, tangentSet, dataHash);

        Map<String, String> p = TransitionCodec.encode(tr);
        return append(actor, expectedHead, List.of(
                draft(Hashes.id("ev_tr", trId + "|" + now()),
                        EventType.TRANSITION_COMPUTED, actor, p)), null);
    }

    /** Recompute only derivations affected by a given source (dependency propagation). */
    public List<String> affectedBy(String sourceNode) {
        DependencyGraph g = buildGraph(current());
        return g.affectedBy(sourceNode);
    }

    public String explainStatus(String derivedId) {
        return buildGraph(current()).explain(derivedId);
    }

    public DependencyGraph buildGraph(StateSnapshot snap) {
        DependencyGraph g = new DependencyGraph();
        int massV = snap.currentMass() == null ? 0 : snap.currentMass().version();
        int rateV = snap.currentRate() == null ? 0 : snap.currentRate().version();
        int calibV = snap.currentCalibration() == null ? 0 : snap.currentCalibration().version();
        g.setCurrentVersion("mass", massV);
        g.setCurrentVersion("rate", rateV);
        g.setCurrentVersion("calibration", calibV);
        for (Segment seg : snap.segments().values()) {
            g.setCurrentVersion("raw:" + seg.id(), rawBatchVersion(snap, seg.id()));
        }
        snap.tangentVersions().forEach((key, list) -> {
            if (!list.isEmpty()) {
                g.setCurrentVersion("tangent:" + key, list.get(list.size() - 1).version());
            }
        });
        for (TransitionResult tr : snap.transitions().values()) {
            g.consumes(tr.id(), "mass", tr.massVersion());
            g.consumes(tr.id(), "rate", tr.rateVersion());
            g.consumes(tr.id(), "calibration", tr.calibrationVersion());
            g.consumes(tr.id(), "raw:" + tr.segmentId(), tr.rawBatchVersion());
            g.consumes(tr.id(), "tangentset:" + tr.segmentId(), tr.tangentVersionSet());
        }
        return g;
    }

    private double baselineSpreadJG(StateSnapshot snap, String intervalId, int[] idx, Segment seg,
                                    double junctionK, TransitionType type, double massKg) {
        List<BaselineCandidate> all = snap.candidatesByInterval().getOrDefault(intervalId, List.of());
        if (all.size() < 2 || !(massKg > 0)) return 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (BaselineCandidate c : all) {
            EventCharacterizer.ExoConvention exo = parseExo(snap.segmentExoConvention().get(seg.id()));
            EventCharacterizer.Features f = EventCharacterizer.characterize(
                    seg.points(), idx[0], idx[1], c, junctionK, type, massKg, exo);
            double v = Math.abs(f.specificEnthalpyJG());
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return max - min;
    }

    private double samplingUncertainty(Segment seg, int[] idx) {
        int n = idx[1] - idx[0] + 1;
        // halving trapezoid error scales ~1/n^2 for smooth curves; conservative floor
        return Math.max(0.01, 2.0 / (n * n));
    }

    private String dataContent(Segment seg, int[] idx) {
        StringBuilder sb = new StringBuilder();
        for (int i = idx[0]; i <= idx[1]; i++) {
            RawPoint p = seg.points().get(i);
            if (p.computable()) {
                sb.append(String.format("%.8g/%.8g;", p.canonicalTempK(), p.canonicalFlowW()));
            }
        }
        return sb.toString();
    }

    private int rawBatchVersion(StateSnapshot snap, String segmentId) {
        return (int) snap.events().stream()
                .filter(e -> e.type() == EventType.RAW_BATCH_IMPORTED
                        && segmentId.equals(e.payload("segment_id"))).count();
    }

    private int tangentSetHash(StateSnapshot snap, String segmentId) {
        int h = 0;
        for (String side : new String[]{"pre", "post"}) {
            var list = snap.tangentVersions().get(segmentId + "|" + side);
            if (list != null && !list.isEmpty()) {
                h = 31 * h + list.get(list.size() - 1).version();
            }
        }
        return Math.abs(h) + 1;
    }

    private EventCharacterizer.ExoConvention parseExo(String code) {
        if (code == null) return EventCharacterizer.ExoConvention.UNKNOWN;
        return switch (code.toUpperCase()) {
            case "EXO_UP" -> EventCharacterizer.ExoConvention.EXO_UP;
            case "EXO_DOWN" -> EventCharacterizer.ExoConvention.EXO_DOWN;
            default -> EventCharacterizer.ExoConvention.UNKNOWN;
        };
    }

    private static TangentSelection currentTangent(StateSnapshot snap, String segmentId, String side) {
        var list = snap.tangentVersions().get(segmentId + "|" + side);
        return list == null || list.isEmpty() ? null : list.get(list.size() - 1).value();
    }

    private static TransitionType typeOf(StateSnapshot snap, String intervalId) {
        // Stored on the most recent INTERVAL_DRAWN event for this id.
        for (int i = snap.events().size() - 1; i >= 0; i--) {
            Event e = snap.events().get(i);
            if (e.type() == EventType.INTERVAL_DRAWN && intervalId.equals(e.payload("interval_id"))) {
                return TransitionType.fromCode(e.payload("transition_type"));
            }
        }
        return TransitionType.UNRESOLVED;
    }

    private static boolean isOverlap(StateSnapshot snap, String intervalId) {
        for (int i = snap.events().size() - 1; i >= 0; i--) {
            Event e = snap.events().get(i);
            if (intervalId.equals(e.payload("interval_id")) && e.type() == EventType.OVERLAP_DETECTED) {
                return true;
            }
        }
        return false;
    }

    private static int[] rangeIndices(Segment seg, double startK, double endK) {
        int from = -1;
        int to = -1;
        if (seg == null) return new int[]{0, -1};
        for (RawPoint p : seg.points()) {
            if (p.computable() && p.canonicalTempK() >= startK && p.canonicalTempK() <= endK) {
                if (from < 0) from = p.index();
                to = p.index();
            }
        }
        return new int[]{from < 0 ? 0 : from, from < 0 ? -1 : to};
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public long headSeq() {
        return store.headSeq(MAIN_BRANCH);
    }

    public Optional<Event> eventAt(long seq) {
        return store.readAllUpTo(MAIN_BRANCH, seq).stream()
                .filter(e -> e.seq() == seq).findFirst();
    }
}
