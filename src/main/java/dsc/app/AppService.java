package dsc.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.domain.*;
import dsc.engine.Recompute;
import dsc.engine.State;
import dsc.store.Store;

import java.util.*;

/**
 * Application orchestration: builds source/review events, commits them
 * atomically, replays the affected dependency graph, and exposes branch/
 * history/archive operations. Every commit stores both the fact events and the
 * generated derivations and judgments, so re-import rebuilds identical ids.
 */
public class AppService {
    private final Store store;
    public static final String MAIN = "main";

    public AppService(Store store) {
        this.store = store;
        store.ensureBranch(MAIN);
    }

    public Store store() { return store; }

    // ---- ingest ------------------------------------------------------------

    public Store.CommitResult createSample(String expectedHead, String code, String material,
                                           String actor) {
        String id = Ids.sha("sample", code, material);
        ObjectNode p = State.M.createObjectNode();
        p.put("id", id); p.put("code", code); p.put("material", material);
        p.put("createdAt", iso());
        return commit(MAIN, head(MAIN, expectedHead),
                List.of(pending(Event.SAMPLE_CREATED, Ids.sha("evt", "S", id), id, p, actor)));
    }

    public Store.CommitResult ingestCurve(String expectedHead, String sampleId, String fileName,
                                          String content, String actor) {
        DscCsv.Parsed parsed = DscCsv.parse(content);
        Map<String, String> h = parsed.headers();
        String runLabel = h.getOrDefault("run", fileName);
        String seqDigits = h.getOrDefault("sequence", "0").replaceAll("[^0-9]", "");
        int seqNo = seqDigits.isBlank() ? 0 : Integer.parseInt(seqDigits);
        String segId = Ids.sha("seg", sampleId, fileName, h.getOrDefault("direction", ""),
                h.getOrDefault("sign_convention", ""), String.valueOf(pointsHash(parsed.points())));

        ObjectNode p = State.M.createObjectNode();
        p.put("id", segId);
        p.put("sampleId", sampleId);
        p.put("fileName", fileName);
        p.put("runLabel", runLabel);
        p.put("sequenceNo", seqNo);
        p.put("directionRaw", h.getOrDefault("direction", ""));
        p.put("direction", Direction.parse(h.get("direction")).name());
        p.put("signConventionRaw", h.getOrDefault("sign_convention", ""));
        p.put("signConvention", SignConvention.parse(h.get("sign_convention")).name());
        p.put("tempUnitRaw", h.getOrDefault("temp_unit", ""));
        p.put("hfUnitRaw", h.getOrDefault("hf_unit", ""));
        Quant rate = Quant.parse(h.getOrDefault("scan_rate", ""), Quant.RATE);
        p.put("scanRateRaw", rate.raw());
        p.put("scanRateCanonical", rate.valid() ? rate.canonical() : (Double) null);
        p.put("scanRateUnit", rate.valid() ? rate.canonicalUnit() : "K/min");
        if (!rate.valid()) p.put("scanRateDiagnostic", rate.diagnostic());
        p.put("atmosphereHeader", h.getOrDefault("atmosphere", ""));
        p.put("curveVersion", 1);
        p.put("pointsHash", pointsHash(parsed.points()));

        ArrayNode pts = p.putArray("points");
        for (Sources.DataPoint dp : parsed.points()) {
            ObjectNode q = pts.addObject();
            q.put("index", dp.index());
            q.put("tempRaw", dp.tempRaw());
            q.put("hfRaw", dp.hfRaw());
            if (dp.tempC() != null) q.put("tempC", dp.tempC());
            if (dp.hfMW() != null) q.put("hfMW", dp.hfMW());
        }
        ArrayNode gaps = p.putArray("gaps");
        for (Sources.Gap g : parsed.gaps()) {
            ObjectNode gn = gaps.addObject();
            gn.put("afterIndex", g.afterIndex());
            gn.put("tempDelta", g.tempDelta());
            gn.put("medianDelta", g.medianDelta());
            gn.put("ratio", g.ratio());
        }
        ArrayNode diags = p.putArray("importDiagnostics");
        parsed.diagnostics().forEach(diags::add);

        Store.CommitResult cr = commit(MAIN, head(MAIN, expectedHead),
                List.of(pending(Event.CURVE_INGESTED, Ids.sha("evt", "C", segId), segId, p, actor)));
        return cr;
    }

    public Store.CommitResult setMass(String expectedHead, String sampleId, String rawMass,
                                      String note, String actor) {
        int v = nextVersion(loadState(MAIN).masses.values(), sampleId) + 1;
        Quant m = Quant.parse(rawMass, Quant.MASS);
        String id = Ids.sha("mass", sampleId, String.valueOf(v), rawMass);
        ObjectNode p = State.M.createObjectNode();
        p.put("id", id); p.put("sampleId", sampleId); p.put("version", v);
        p.put("massRaw", m.raw());
        if (m.valid()) p.put("massCanonical", m.canonical());
        p.put("massUnit", m.valid() ? m.canonicalUnit() : "g");
        if (!m.valid()) p.put("massDiagnostic", m.diagnostic());
        p.put("note", note == null ? "" : note);
        p.put("createdAt", iso());
        return commit(MAIN, head(MAIN, expectedHead),
                List.of(pending(Event.MASS_SET, Ids.sha("evt", "M", id), id, p, actor)));
    }

    public Store.CommitResult recordAtmosphere(String expectedHead, String sampleId, String gas,
                                               String rawFlow, String note, String actor) {
        Quant f = Quant.parse(rawFlow, Quant.FLOW);
        String id = Ids.sha("atm", sampleId, gas, rawFlow);
        ObjectNode p = State.M.createObjectNode();
        p.put("id", id); p.put("sampleId", sampleId);
        p.put("gasRaw", gas);
        p.put("flowRaw", f.raw());
        if (f.valid()) p.put("flowCanonical", f.canonical());
        if (!f.valid()) p.put("flowDiagnostic", f.diagnostic());
        p.put("note", note == null ? "" : note);
        return commit(MAIN, head(MAIN, expectedHead),
                List.of(pending(Event.ATMOSPHERE_RECORDED, Ids.sha("evt", "A", id), id, p, actor)));
    }

    public Store.CommitResult setCalibration(String expectedHead, String sampleId, String rawOffset,
                                             String standard, String note, String actor) {
        int v = nextVersion(loadState(MAIN).calibrations.values(), sampleId) + 1;
        Quant o = Quant.parse(rawOffset, Quant.TEMPERATURE);
        String id = Ids.sha("cal", sampleId, String.valueOf(v), rawOffset);
        ObjectNode p = State.M.createObjectNode();
        p.put("id", id); p.put("sampleId", sampleId); p.put("version", v);
        p.put("offsetRaw", o.raw());
        if (o.valid()) p.put("offsetCanonical", o.canonical());
        p.put("standardRaw", standard == null ? "" : standard);
        p.put("note", note == null ? "" : note);
        if (!o.valid()) p.put("offsetDiagnostic", o.diagnostic());
        return commit(MAIN, head(MAIN, expectedHead),
                List.of(pending(Event.CALIBRATION_SET, Ids.sha("evt", "K", id), id, p, actor)));
    }

    public Store.CommitResult pickTangent(String expectedHead, String segmentId, String intervalId,
                                          int leftIndex, int rightIndex, String actor) {
        String id = Ids.sha("tan", segmentId, intervalId, String.valueOf(leftIndex), String.valueOf(rightIndex));
        ObjectNode p = State.M.createObjectNode();
        p.put("id", id); p.put("segmentId", segmentId); p.put("intervalId", intervalId);
        p.put("leftIndex", leftIndex); p.put("rightIndex", rightIndex);
        p.put("createdAt", iso());
        return commit(MAIN, head(MAIN, expectedHead),
                List.of(pending(Event.TANGENT_PICKED, Ids.sha("evt", "T", id), id, p, actor)));
    }

    private int nextVersion(Collection<ObjectNode> vals, String sampleId) {
        int v = 0;
        for (ObjectNode n : vals) {
            if (n.path("sampleId").asText().equals(sampleId)) v = Math.max(v, n.path("version").asInt());
        }
        return v;
    }

    // ---- review actions from the curve page --------------------------------

    public Store.CommitResult drawInterval(String expectedHead, String branch, String segmentId,
                                           int loIndex, int hiIndex, int anchorWindow,
                                           List<String> segmentIds, String actor) {
        State st = loadState(branch);
        int version = intervalVersion(st, segmentId) + 1;
        String id = Ids.sha("iv", segmentId, "user", String.valueOf(version));
        ObjectNode p = intervalNode(id, segmentId, loIndex, hiIndex, anchorWindow, version, false);
        if (segmentIds != null && segmentIds.size() > 1) {
            ArrayNode a = p.putArray("segments");
            segmentIds.forEach(a::add);
        }
        Store.CommitResult cr = commit(branch, head(branch, expectedHead),
                List.of(pending(Event.INTERVAL_DRAWN, Ids.sha("evt", "I", id, String.valueOf(version)),
                        id, p, actor)));
        return cr;
    }

    public Store.CommitResult refitInterval(String expectedHead, String branch, String intervalId,
                                            int loIndex, int hiIndex, int anchorWindow, String actor) {
        State st = loadState(branch);
        ObjectNode old = st.intervals.get(intervalId);
        if (old == null) throw new IllegalArgumentException("unknown interval " + intervalId);
        int version = old.path("version").asInt() + 1;
        String segId = old.path("segmentId").asText();
        ObjectNode p = intervalNode(intervalId, segId, loIndex, hiIndex, anchorWindow, version, false);
        return commit(branch, head(branch, expectedHead),
                List.of(pending(Event.INTERVAL_REFIT,
                        Ids.sha("evt", "IR", intervalId, String.valueOf(version)), intervalId, p, actor)));
    }

    public Store.CommitResult decide(String expectedHead, String branch, String intervalId,
                                     String candidateId, String action, String actor) {
        State st = loadState(branch);
        ObjectNode iv = st.intervals.get(intervalId);
        if (iv == null) throw new IllegalArgumentException("unknown interval " + intervalId);
        String type = switch (action) {
            case "adoptBaseline" -> Event.BASELINE_ADOPTED;
            case "rejectBaseline" -> Event.BASELINE_REJECTED;
            case "acceptFeature" -> Event.FEATURE_ACCEPTED;
            case "rejectFeature" -> Event.FEATURE_REJECTED;
            default -> throw new IllegalArgumentException(action);
        };
        ObjectNode p = State.M.createObjectNode();
        p.put("intervalId", intervalId);
        p.put("segmentId", iv.path("segmentId").asText());
        p.put("candidateId", candidateId);
        p.put("createdAt", iso());
        return commit(branch, head(branch, expectedHead),
                List.of(pending(type,
                        Ids.sha("evt", "D", type, intervalId, candidateId,
                                String.valueOf(System.identityHashCode(p))),
                        intervalId, p, actor)));
    }

    private ObjectNode intervalNode(String id, String segmentId, int lo, int hi, int win,
                                    int version, boolean automatic) {
        ObjectNode p = State.M.createObjectNode();
        p.put("id", id); p.put("segmentId", segmentId);
        p.put("loIndex", lo); p.put("hiIndex", hi);
        p.put("anchorWindow", win); p.put("version", version);
        p.put("automatic", automatic);
        p.put("createdAt", iso());
        return p;
    }

    private int intervalVersion(State st, String segmentId) {
        int v = 0;
        for (ObjectNode n : st.intervals.values()) {
            if (n.path("segmentId").asText().equals(segmentId)) {
                v = Math.max(v, n.path("version").asInt());
            }
        }
        return v;
    }

    // ---- one direct source patched: only local dependency subtree ----------

    public Store.CommitResult patchCurve(String expectedHead, String segmentId, String newContent,
                                         String actor) {
        State st = loadState(MAIN);
        ObjectNode old = st.curves.get(segmentId);
        if (old == null) throw new IllegalArgumentException("unknown segment " + segmentId);
        DscCsv.Parsed parsed = DscCsv.parse(newContent);
        int newVersion = old.path("curveVersion").asInt(1) + 1;
        ObjectNode p = State.M.createObjectNode();
        p.put("sourceId", segmentId);
        p.put("previousPointsHash", old.path("pointsHash").asText());
        p.put("curveVersion", newVersion);
        p.put("pointsHash", pointsHash(parsed.points()));
        ArrayNode pts = p.putArray("points");
        for (Sources.DataPoint dp : parsed.points()) {
            ObjectNode q = pts.addObject();
            q.put("index", dp.index());
            q.put("tempRaw", dp.tempRaw());
            q.put("hfRaw", dp.hfRaw());
            if (dp.tempC() != null) q.put("tempC", dp.tempC());
            if (dp.hfMW() != null) q.put("hfMW", dp.hfMW());
        }
        ArrayNode gaps = p.putArray("gaps");
        for (Sources.Gap g : parsed.gaps()) {
            ObjectNode gn = gaps.addObject();
            gn.put("afterIndex", g.afterIndex());
            gn.put("tempDelta", g.tempDelta());
            gn.put("medianDelta", g.medianDelta());
            gn.put("ratio", g.ratio());
        }
        Store.CommitResult cr = commit(MAIN, head(MAIN, expectedHead),
                List.of(pending(Event.SOURCE_PATCHED,
                        Ids.sha("evt", "P", segmentId, String.valueOf(newVersion)),
                        segmentId, p, actor)));
        return cr;
    }

    // ---- branching ---------------------------------------------------------

    public Store.CommitResult fork(String expectedHead, String newBranch, long fromSeq,
                                   String fromBranch, String actor) {
        ObjectNode p = State.M.createObjectNode();
        p.put("branch", newBranch);
        p.put("parentBranch", fromBranch == null ? MAIN : fromBranch);
        p.put("baseSeq", fromSeq);
        p.put("createdAt", iso());
        long expected = head(MAIN, expectedHead);
        Store.PendingEvent pe = new Store.PendingEvent(Event.BRANCH_FORKED,
                Ids.sha("evt", "F", newBranch, String.valueOf(fromSeq)), null, -1, actor,
                newBranch, p);
        return store.commit(MAIN, expected, List.of(pe), List.of(), Map.of());
    }

    public Store.CommitResult chooseBranch(String expectedHead, String branch, String decision,
                                           String actor) {
        ObjectNode p = State.M.createObjectNode();
        p.put("branch", branch);
        p.put("decision", decision); // CHOSEN / REJECTED
        p.put("createdAt", iso());
        String type = "CHOSEN".equals(decision) ? Event.BRANCH_CHOSEN : Event.BRANCH_REJECTED;
        return commit(MAIN, head(MAIN, expectedHead),
                List.of(pending(type, Ids.sha("evt", "B", type, branch, String.valueOf(System.nanoTime())),
                        branch, p, actor)));
    }

    // ---- commit + incremental derivation -----------------------------------

    private long head(String branch, String expectedHead) {
        return expectedHead == null || expectedHead.isBlank()
                ? store.headSeq(branch) : Long.parseLong(expectedHead);
    }

    private Store.PendingEvent pending(String type, String id, String targetId,
                                      ObjectNode payload, String actor) {
        return new Store.PendingEvent(type, id, null, -1, actor, targetId, payload);
    }

    private Store.CommitResult commit(String branch, long expectedHead, List<Store.PendingEvent> events) {
        // Tentatively apply facts to an in-memory state for derivation.
        State tentative = loadState(branch);
        Set<String> dirtySegments = new LinkedHashSet<>();
        Set<String> dirtyIntervals = new LinkedHashSet<>();
        Set<String> massSamples = new LinkedHashSet<>();
        Set<String> calibrationSamples = new LinkedHashSet<>();
        long seq = expectedHead;
        for (Store.PendingEvent pe : events) {
            seq++;
            Event synthetic = new Event(pe.id(), seq, pe.type(), branch, pe.parentId(),
                    pe.baseSeq(), pe.actor(), System.currentTimeMillis(), pe.targetId(), pe.payload());
            tentative.applyFacts(synthetic);
            collectDirty(synthetic, tentative, dirtySegments, dirtyIntervals, massSamples, calibrationSamples);
        }
        Recompute.Result rr = Recompute.incremental(tentative, branch, dirtySegments, dirtyIntervals,
                massSamples, calibrationSamples);

        List<Store.PendingEvent> all = new ArrayList<>(events);
        for (Event j : rr.judgments()) {
            ObjectNode jp = (ObjectNode) j.payload();
            all.add(new Store.PendingEvent(Event.JUDGMENT,
                    j.id(), null, -1, "engine", j.targetId(), jp));
        }
        List<Derivation> ders = rr.derivations();
        for (Derivation d : ders) {
            // remember at which generation this version was computed
            all.add(new Store.PendingEvent(Event.DERIVED,
                    Ids.sha("evt", "V", d.id(), String.valueOf(seq)),
                    null, -1, "engine", d.key(), derivationPayload(d, seq)));
        }
        Store.CommitResult cr = store.commit(branch, expectedHead, all, ders, rr.affectedReasons());
        return cr;
    }

    private ObjectNode derivationPayload(Derivation d, long seq) {
        ObjectNode p = State.M.createObjectNode();
        p.put("derivationId", d.id());
        p.put("key", d.key());
        p.put("kind", d.kind());
        p.put("status", d.status());
        p.put("ruleVersion", d.ruleVersion());
        p.put("inputEventSeq", seq);
        p.set("deps", State.M.valueToTree(Json.readDeps(d.deps())));
        return p;
    }

    private void collectDirty(Event e, State st, Set<String> segments, Set<String> intervals,
                              Set<String> massSamples, Set<String> calibrationSamples) {
        ObjectNode p = (ObjectNode) e.payload();
        switch (e.type()) {
            case Event.CURVE_INGESTED -> segments.add(p.path("id").asText());
            case Event.SOURCE_PATCHED -> segments.add(p.path("sourceId").asText());
            case Event.MASS_SET -> {
                String sid = p.path("sampleId").asText();
                massSamples.add(sid);
                for (ObjectNode c : st.curvesOf(sid)) segments.add(c.path("id").asText());
            }
            case Event.CALIBRATION_SET -> {
                String sid = p.path("sampleId").asText();
                calibrationSamples.add(sid);
                for (ObjectNode c : st.curvesOf(sid)) segments.add(c.path("id").asText());
            }
            case Event.INTERVAL_DRAWN, Event.INTERVAL_REFIT, Event.BASELINE_ADOPTED,
                 Event.BASELINE_REJECTED, Event.FEATURE_ACCEPTED, Event.FEATURE_REJECTED -> {
                String ivId = e.type().startsWith("INTERVAL") ? p.path("id").asText()
                        : p.path("intervalId").asText();
                intervals.add(ivId);
                ObjectNode iv = st.intervals.get(ivId);
                if (iv != null) segments.add(iv.path("segmentId").asText());
            }
            default -> { }
        }
    }

    /** Materialize a branch state by replaying all its facts (deterministic). */
    public State loadState(String branch) {
        State st = new State();
        for (Event e : store.eventsOnBranch(branch)) {
            st.applyFacts(e);
        }
        for (Derivation d : store.currentDerivations(branch)) {
            st.current.put(d.key(), d);
            st.allDerivations.put(d.id(), d);
        }
        return st;
    }

    /**
     * Re-derive the exact visible state as of a historical event seq on a branch.
     * Returns facts + freshly recomputed derivations using only events up to
     * that seq - never overwriting history with the newest numbers.
     */
    public State stateAt(String branch, long atSeq) {
        State st = new State();
        List<Event> events = store.eventsOnBranch(branch);
        for (Event e : events) {
            if (e.seq() > atSeq) break;
            st.applyFacts(e);
        }
        Recompute.Result rr = Recompute.full(st, branch);
        for (Derivation d : rr.derivations()) st.current.put(d.key(), d);
        return st;
    }

    /** Recompute a branch fully from its event log (used after import / delete-restore). */
    public Recompute.Result rebuildBranch(String branch) {
        State st = new State();
        for (Event e : store.eventsOnBranch(branch)) st.applyFacts(e);
        return Recompute.full(st, branch);
    }

    public List<Event> events(String branch) {
        return store.eventsOnBranch(branch);
    }

    private long pointsHash(List<Sources.DataPoint> pts) {
        long h = 1469598103934665603L;
        for (Sources.DataPoint p : pts) {
            for (byte b : (p.tempRaw() + "|" + p.hfRaw()).getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
                h ^= b; h *= 1099511628211L;
            }
        }
        return h;
    }

    private String iso() {
        return java.time.Instant.now().toString();
    }
}
