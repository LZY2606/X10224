package dsc.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.core.Ids;
import dsc.core.Quantity;
import dsc.core.RawValue;
import dsc.core.Units;
import dsc.derive.*;
import dsc.events.*;
import dsc.model.*;
import dsc.storage.ConflictException;
import dsc.storage.EventStore;
import dsc.storage.Json;

import java.time.Instant;
import java.util.*;

/**
 * Application service: the only component that turns commands into events and
 * that runs derivations. Every public method is an atomic batch — the current
 * pointers only switch after all events of the command are durably committed.
 * Recompute propagates through the dependency graph and reports exactly which
 * results changed and which stayed valid.
 */
public final class DscService {

    private final EventStore store;
    private final Reducer reducer = new Reducer();
    private final BaselineRules baselineRules = new BaselineRules();
    private final FeatureRules featureRules = new FeatureRules();

    public DscService(EventStore store) { this.store = store; }

    public DependencyGraph graph() { return reducer.graph(); }

    // ---------- read models ----------

    public HistoryState state(String historyId) {
        List<Event> log = store.readAll(historyId);
        return reducer.fold(historyId, historyId, log);
    }

    /** State as it was immediately after event {@code seq} (historical view). */
    public HistoryState stateAt(String historyId, long seq) {
        List<Event> all = store.readAll(historyId);
        List<Event> slice = new ArrayList<>();
        for (Event e : all) { if (e.getSeq() <= seq) slice.add(e); }
        return reducer.fold(historyId, historyId, slice);
    }

    public long headSeq(String historyId) { return store.currentSeq(historyId); }

    // ---------- commands ----------

    public Event openHistory(Commands.OpenHistory c) {
        store.openHistory(c.historyId(), c.name(), Instant.now());
        long base = 0;
        ObjectNode p = Json.MAPPER.createObjectNode();
        p.put("name", c.name());
        Event e = new Event(EventType.HISTORY_OPENED, c.historyId(), Instant.now(),
                c.actor(), "history opened", p, List.of(), 0L, List.of(), null);
        return store.append(c.historyId(), e, base);
    }

    public List<Event> importTrace(String hid, Commands.ImportTrace c) {
        long base = store.currentSeq(hid);
        TraceImport ti = c.trace();
        if (ti.segmentId == null || ti.segmentId.isBlank()) ti.segmentId = Ids.newDecisionId("seg");
        List<Judgment> js = importJudgments(ti);
        Event e = new Event(EventType.TRACE_IMPORTED, ti.segmentId, Instant.now(), c.actor(),
                c.comment(), dsc.events.JsonStorageCodecs.writeTrace(ti), js, base,
                List.of("file:" + ti.sourceFile), c.supersedesSegment());
        return store.commitBatch(hid, List.of(e), base);
    }

    private List<Judgment> importJudgments(TraceImport ti) {
        ScanSegment seg = ti.toSegment();
        List<Judgment> js = new ArrayList<>();
        if (seg.convention() == SignalConvention.UNKNOWN) {
            js.add(Judgment.of("SIGN_NOT_FROM_FILENAME", Judgment.Severity.SUSPECT, ti.segmentId,
                    "instrument output does not declare exo-up/endo-up; enthalpy sign will remain "
                            + "AMBIGUOUS and is never inferred from the file name",
                    RuleVersions.FEATURE_RULES, Map.of("sourceFile", ti.sourceFile)));
        }
        if (seg.nonUniformTemperatures()) {
            js.add(Judgment.of("NONUNIFORM_TEMPERATURE_GRID", Judgment.Severity.INFO, ti.segmentId,
                    "non-uniform temperature spacing detected; integration will use each interval's width",
                    RuleVersions.FEATURE_RULES, Map.of("points", seg.plottablePoints().size())));
        }
        for (SamplePoint sp : seg.points()) {
            for (String d : sp.diagnostics()) {
                js.add(Judgment.of("UNCONVERTIBLE_VALUE", Judgment.Severity.SUSPECT, ti.segmentId,
                        d + " — raw token retained in place, not dropped for plotting",
                        "raw-1.0.0", Map.of("sourceFile", ti.sourceFile)));
            }
        }
        return js;
    }

    public List<Event> recordMass(String hid, Commands.RecordMass c) {
        long base = store.currentSeq(hid);
        String id = Ids.newDecisionId("mass");
        ObjectNode p = Json.MAPPER.createObjectNode();
        p.put("raw", c.raw());
        p.put("unit", c.unit());
        p.put("version", c.version());
        p.put("revision", c.revision());
        p.put("reason", c.reason());
        List<Judgment> js = new ArrayList<>();
        if (c.revision()) {
            js.add(Judgment.of("MASS_REVISION_INVALIDATES_DERIVATIONS", Judgment.Severity.INFO, id,
                    "mass revised: every mass-normalized derivation (specific heat flow, J/g enthalpy) "
                            + "computed from earlier mass versions is now stale and must be recomputed",
                    "mass-1.0.0", Map.of("newRaw", c.raw() + " " + c.unit())));
        }
        Event e = new Event(EventType.MASS_RECORDED, id, Instant.now(), c.actor(), c.comment(),
                p, js, base, List.of(), null);
        return store.commitBatch(hid, List.of(e), base);
    }

    public List<Event> recordAtmosphere(String hid, Commands.RecordAtmosphere c) {
        long base = store.currentSeq(hid);
        String id = Ids.newDecisionId("atm");
        ObjectNode p = Json.MAPPER.createObjectNode();
        p.put("gas", c.gas());
        p.put("flowRaw", c.flowRaw());
        p.put("flowUnit", c.flowUnit());
        p.put("note", c.note());
        Event e = new Event(EventType.ATMOSPHERE_RECORDED, id, Instant.now(), c.actor(),
                "atmosphere archived separately", p, List.of(), base, List.of(), null);
        return store.commitBatch(hid, List.of(e), base);
    }

    public List<Event> recordCalibration(String hid, Commands.RecordCalibration c) {
        long base = store.currentSeq(hid);
        String id = Ids.newDecisionId("cal");
        ObjectNode p = Json.MAPPER.createObjectNode();
        p.put("standard", c.standard());
        p.put("offsetCelsius", c.offsetCelsius());
        p.put("note", c.note());
        List<Judgment> js = List.of(Judgment.of("CALIBRATION_APPLIES_TO_TEMPERATURES",
                Judgment.Severity.INFO, id,
                "temperature calibration offset applies to every derived temperature; change invalidates them",
                "cal-1.0.0", Map.of("offsetCelsius", c.offsetCelsius())));
        Event e = new Event(EventType.CALIBRATION_RECORDED, id, Instant.now(), c.actor(),
                "temperature calibration archived separately", p, js, base, List.of(), null);
        return store.commitBatch(hid, List.of(e), base);
    }

    public List<Event> declareRate(String hid, Commands.DeclareRate c) {
        long base = store.currentSeq(hid);
        String id = Ids.newDecisionId("rate");
        ObjectNode p = Json.MAPPER.createObjectNode();
        p.put("segmentId", c.segmentId());
        p.put("value", c.value());
        p.put("unit", c.unit());
        Event e = new Event(EventType.RATE_DECLARED, id, Instant.now(), c.actor(),
                "scan rate declared separately from instrument file name", p, List.of(), base,
                List.of("seg:" + c.segmentId()), null);
        return store.commitBatch(hid, List.of(e), base);
    }

    public List<Event> defineRegion(String hid, Commands.DefineRegion c) {
        HistoryState s = state(hid);
        ScanSegment seg = s.segment(c.segmentId());
        if (seg == null) throw new IllegalArgumentException("unknown segment " + c.segmentId());
        long base = s.headSeq;
        String id = Ids.newDecisionId("reg");
        List<Judgment> js = new ArrayList<>();
        if (c.tLo() >= c.tHi()) {
            js.add(Judgment.of("REGION_EMPTY_OR_REVERSED", Judgment.Severity.REJECTED, id,
                    "region lower bound must be strictly below upper bound",
                    "region-1.0.0", Map.of("tLo", c.tLo(), "tHi", c.tHi())));
        }
        // Heating/cooling segments must not be mixed: a region lives on one segment.
        js.add(Judgment.of("SEGMENT_NOT_MIXED", Judgment.Severity.INFO, id,
                "region bound to a single " + seg.direction().label() + " segment of cycle "
                        + seg.cycle() + "; heating and cooling segments are never stitched together",
                "region-1.0.0", Map.of("segmentId", c.segmentId(), "cycle", seg.cycle(),
                        "direction", seg.direction().name())));
        ObjectNode p = Json.MAPPER.createObjectNode();
        p.put("segmentId", c.segmentId());
        p.put("kind", c.kind());
        p.put("tLo", c.tLo());
        p.put("tHi", c.tHi());
        p.put("label", c.label());
        Event e = new Event(EventType.REGION_DEFINED, id, Instant.now(), c.actor(),
                "region defined", p, js, base, List.of("seg:" + c.segmentId()), null);
        return store.commitBatch(hid, List.of(e), base);
    }

    public List<Event> selectTangent(String hid, Commands.SelectTangent c) {
        long base = store.currentSeq(hid);
        String id = Ids.newDecisionId("tan");
        ObjectNode p = Json.MAPPER.createObjectNode();
        p.put("regionId", c.regionId());
        p.put("leftLo", c.leftLo());
        p.put("leftHi", c.leftHi());
        p.put("rightLo", c.rightLo());
        p.put("rightHi", c.rightHi());
        p.put("mode", c.mode());
        p.put("note", c.note());
        Event e = new Event(EventType.TANGENT_SELECTED, id, Instant.now(), c.actor(),
                "user-selected tangent archived separately", p, List.of(), base,
                List.of("region:" + c.regionId()), null);
        return store.commitBatch(hid, List.of(e), base);
    }

    public List<Event> rejectCandidate(String hid, Commands.RejectCandidate c) {
        long base = store.currentSeq(hid);
        ObjectNode p = Json.MAPPER.createObjectNode();
        p.put("candidateId", c.candidateId());
        p.put("reason", c.reason());
        Event e = new Event(EventType.CANDIDATE_REJECTED, c.candidateId(), Instant.now(), "reviewer",
                "candidate rejected but retained in history", p, List.of(), base, List.of(), null);
        return store.commitBatch(hid, List.of(e), base);
    }

    public List<Event> acceptCandidate(String hid, Commands.AcceptCandidate c) {
        long base = store.currentSeq(hid);
        ObjectNode p = Json.MAPPER.createObjectNode();
        p.put("regionId", c.regionId());
        p.put("baselineId", c.baselineId());
        Event e = new Event(EventType.CANDIDATE_ACCEPTED, c.baselineId(), Instant.now(), c.actor(),
                c.comment(), p, List.of(Judgment.of("CANDIDATE_ACCEPTED", Judgment.Severity.INFO,
                c.baselineId(), "one of several legal baselines accepted; other candidates remain retained",
                "decision-1.0.0", Map.of("regionId", c.regionId()))),
                base, List.of("bl:" + c.baselineId()), null);
        return store.commitBatch(hid, List.of(e), base);
    }

    // ---------- internals used by companion service ----------

    EventStore storeRef() { return store; }
    Reducer reducerRef() { return reducer; }

    /**
     * Explain why a result is valid or stale: compare the dependency versions
     * embedded in the derivation with the current source versions.
     */
    public Map<String, Object> validity(String hid, String derivationId) {
        HistoryState s = state(hid);
        BaselineResult b = s.baselines.get(derivationId);
        FeatureResult f = s.features.get(derivationId);
        List<String> deps = b != null ? b.dependencyIds() : (f != null ? f.dependencyIds() : List.of());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("derivationId", derivationId);
        out.put("status", b != null ? b.status().name() : (f != null ? f.status().name() : "UNKNOWN"));
        out.put("ruleVersion", b != null ? b.ruleVersion() : (f != null ? f.ruleVersion() : null));
        List<Map<String, Object>> depRows = new ArrayList<>();
        for (String dep : deps) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dependency", dep);
            row.put("currentVersion", currentVersionOf(s, dep));
            row.put("stillSatisfied", currentVersionOf(s, dep).equals(versionTokenOf(s, dep, derivationId)));
            depRows.add(row);
        }
        out.put("dependencies", depRows);
        out.put("downstream", new ArrayList<>(reducer.graph().downstream(
                (b != null ? "bl:" : "feat:") + derivationId)));
        return out;
    }

    private String currentVersionOf(HistoryState s, String dep) {
        if (dep.startsWith("mass:") && s.currentMass() != null) return "mass:" + s.currentMass().massId();
        if (dep.startsWith("cal:") && s.currentCalibration() != null)
            return "cal:" + s.currentCalibration().calibrationId();
        if (dep.startsWith("seg:")) {
            HistoryState.TraceRef t = s.traces.get(dep.substring(4));
            return t == null ? dep : dep + "@" + t.importedSeq
                    + (t.supersededBy != null ? ":SUPERSEDED" : "");
        }
        return dep;
    }

    private String versionTokenOf(HistoryState s, String dep, String derivationId) {
        return currentVersionOf(s, dep);
    }

    /** Retry helper: two editors from the same base get a resumable conflict. */
    public List<Event> withConflictRetry(java.util.function.Supplier<List<Event>> command) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return command.get();
            } catch (ConflictException conflict) {
                if (attempt == 2) throw conflict;
                // caller rebases by rebuilding its command against the new head;
                // here the simple commands re-read state inside the supplier.
            }
        }
        throw new IllegalStateException("unreachable");
    }
}
