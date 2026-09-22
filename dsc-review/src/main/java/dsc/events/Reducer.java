package dsc.events;

import com.fasterxml.jackson.databind.JsonNode;
import dsc.core.Ids;
import dsc.core.Quantity;
import dsc.core.RawValue;
import dsc.core.Units;
import dsc.derive.*;
import dsc.model.*;

import java.time.Instant;
import java.util.*;

/**
 * Pure fold over the event log. Replaying the same log always yields the same
 * state — including retained (superseded/rejected) derivations — which is what
 * makes history time travel and export/import lossless.
 */
public final class Reducer {

    private final BaselineRules baselineRules = new BaselineRules();
    private final FeatureRules featureRules = new FeatureRules();
    private final DependencyGraph graph = new DependencyGraph();

    public DependencyGraph graph() { return graph; }

    public HistoryState fold(String historyId, String name, List<Event> log) {
        HistoryState s = new HistoryState(historyId);
        s.name = name;
        for (Event e : log) apply(s, e);
        s.headSeq = log.isEmpty() ? 0 : log.get(log.size() - 1).getSeq();
        return s;
    }

    private void apply(HistoryState s, Event e) {
        s.events.add(e);
        JsonNode p = e.getPayload();
        switch (e.getType()) {
            case HISTORY_OPENED -> s.name = p.path("name").asText(s.name);
            case TRACE_IMPORTED -> applyTrace(s, e);
            case MASS_RECORDED -> applyMass(s, e);
            case ATMOSPHERE_RECORDED -> applyAtmosphere(s, e);
            case CALIBRATION_RECORDED -> applyCalibration(s, e);
            case RATE_DECLARED -> applyRate(s, e);
            case REGION_DEFINED -> applyRegion(s, e);
            case TANGENT_SELECTED -> applyTangent(s, e);
            case BASELINE_DERIVED -> applyBaseline(s, e);
            case FEATURES_DERIVED -> applyFeatures(s, e);
            case CANDIDATE_ACCEPTED -> applyAccept(s, e);
            case CANDIDATE_REJECTED -> applyReject(s, e);
            case PARAMETER_ADJUSTED -> applyAdjust(s, e);
            case SOURCE_AMENDED -> applyAmend(s, e);
            case JUDGMENT_RECORDED -> { /* judgments live on the event itself */ }
        }
        s.headSeq = e.getSeq();
    }

    private void applyTrace(HistoryState s, Event e) {
        TraceImport ti = JsonStorageCodecs.readTrace(e.getPayload());
        ScanSegment seg = ti.toSegment();
        HistoryState.TraceRef ref = new HistoryState.TraceRef();
        ref.segmentId = ti.segmentId;
        ref.sourceFile = ti.sourceFile;
        ref.cycle = ti.cycle;
        ref.convention = seg.convention();
        ref.segment = seg;
        ref.importedSeq = e.getSeq();
        ref.rawDescriptor = "cycles=" + ti.cycle + ";convention=" + ti.conventionRaw
                + ";rate=" + ti.declaredRate + ti.rateUnit.symbol()
                + ";rows=" + ti.rows.size();
        for (SamplePoint sp : seg.points()) ref.parseDiagnostics.addAll(sp.diagnostics());
        if (e.getSupersedes() != null) {
            HistoryState.TraceRef old = s.traces.get(e.getSupersedes());
            if (old != null) old.supersededBy = ti.segmentId;
        }
        s.traces.put(ti.segmentId, ref);
        if (!s.traceOrder.contains(ti.segmentId)) s.traceOrder.add(ti.segmentId);
        invalidateDownstream(s, "seg:" + ti.segmentId);
    }

    private void applyMass(HistoryState s, Event e) {
        JsonNode p = e.getPayload();
        String id = e.getEntityId();
        String token = p.path("raw").asText();
        RawValue raw = new RawValue(token, Units.bySymbol(p.path("unit").asText("mg")));
        var parsed = raw.parse();
        Quantity grams = parsed.ok() ? parsed.quantity().to(Units.G) : new Quantity(Double.NaN, Units.G);
        MassVersion mv = new MassVersion(id, p.path("version").asInt(1), raw, grams,
                p.path("reason").asText(""), Instant.parse(p.path("recordedAt").asText(e.getAt().toString())));
        s.masses.put(id, mv);
        boolean revision = p.path("revision").asBoolean(false);
        s.currentMassId = id;
        if (revision) {
            invalidateDownstream(s, "mass:" + id);
            // Every specific enthalpy computed from any earlier mass expires too.
            for (FeatureResult f : new ArrayList<>(s.features.values())) {
                if (f.massIdUsed() != null && !f.massIdUsed().equals(id)) {
                    markFeatureStale(s, f.id());
                }
            }
        }
    }

    private void applyAtmosphere(HistoryState s, Event e) {
        JsonNode p = e.getPayload();
        RawValue flow = new RawValue(p.path("flowRaw").asText(""), Units.bySymbol(p.path("flowUnit").asText("mL/min")));
        var parsed = flow.parse();
        s.atmospheres.put(e.getEntityId(), new Atmosphere(e.getEntityId(),
                p.path("gas").asText(""), flow,
                parsed.ok() ? parsed.quantity() : null, p.path("note").asText("")));
    }

    private void applyCalibration(HistoryState s, Event e) {
        JsonNode p = e.getPayload();
        String id = e.getEntityId();
        Calibration c = new Calibration(id, p.path("standard").asText(""),
                new Quantity(p.path("offsetCelsius").asDouble(0), Units.C),
                Instant.parse(p.path("calibratedAt").asText(e.getAt().toString())),
                p.path("note").asText(""));
        s.calibrations.put(id, c);
        if (s.currentCalibrationId != null && !s.currentCalibrationId.equals(id)) {
            invalidateDownstream(s, "cal:" + s.currentCalibrationId);
        }
        s.currentCalibrationId = id;
        invalidateDownstream(s, "cal:" + id);
    }

    private void applyRate(HistoryState s, Event e) {
        JsonNode p = e.getPayload();
        String id = e.getEntityId();
        double v = p.path("value").asDouble();
        Quantity q = new Quantity(v, Units.bySymbol(p.path("unit").asText("°C/min")));
        s.rates.put(p.path("segmentId").asText(),
                new HistoryState.QuantityRef(id, q, v + q.unit().symbol(), e.getSeq()));
        invalidateDownstream(s, "rate:" + dsc.core.Units.fmt(v));
        invalidateDownstream(s, "seg:" + p.path("segmentId").asText());
    }

    private void applyRegion(HistoryState s, Event e) {
        JsonNode p = e.getPayload();
        String rid = e.getEntityId();
        String segmentId = p.path("segmentId").asText();
        Region r = new Region(rid, segmentId,
                TransitionKind.parse(p.path("kind").asText("generic")),
                p.path("tLo").asDouble(), p.path("tHi").asDouble(),
                p.path("label").asText(""), p.path("createdBy").asText(e.getActor()),
                (int) e.getSeq());
        HistoryState.RegionRef ref = new HistoryState.RegionRef();
        ref.region = r;
        ref.definedSeq = e.getSeq();
        s.regions.put(rid, ref);
    }

    private void applyTangent(HistoryState s, Event e) {
        JsonNode p = e.getPayload();
        TangentSelection t = new TangentSelection(e.getEntityId(), p.path("regionId").asText(),
                p.path("createdBy").asText(e.getActor()),
                p.path("leftLo").asDouble(), p.path("leftHi").asDouble(),
                p.path("rightLo").asDouble(), p.path("rightHi").asDouble(),
                p.path("mode").asText(TangentSelection.MODE_TWO_SIDED),
                p.path("note").asText(""));
        HistoryState.TangentRef ref = new HistoryState.TangentRef();
        ref.tangent = t;
        ref.selectedSeq = e.getSeq();
        s.tangents.put(t.tangentId(), ref);
        invalidateDownstream(s, "tan:" + t.tangentId());
        // older tangent for the same region is superseded
        for (HistoryState.TangentRef old : s.tangents.values()) {
            if (old.tangent.regionId().equals(t.regionId()) && !old.tangent.tangentId().equals(t.tangentId())
                    && old.selectedSeq < e.getSeq()) {
                invalidateDownstream(s, "tan:" + old.tangent.tangentId());
            }
        }
    }

    private void applyBaseline(HistoryState s, Event e) {
        BaselineResult b = JsonStorageCodecs.readBaseline(e);
        if (e.getDependencies() != null) graph.record(b.id(), e.getDependencies());
        if (e.getSupersedes() != null) {
            BaselineResult old = s.baselines.get(e.getSupersedes());
            if (old != null) s.baselines.put(old.id(), withStatus(old, NodeStatus.SUPERSEDED));
            invalidateDownstream(s, "bl:" + e.getSupersedes());
        }
        s.baselines.put(b.id(), b);
    }

    private void applyFeatures(HistoryState s, Event e) {
        FeatureResult f = JsonStorageCodecs.readFeature(e);
        if (e.getDependencies() != null) graph.record(f.id(), e.getDependencies());
        if (e.getSupersedes() != null) {
            FeatureResult old = s.features.get(e.getSupersedes());
            if (old != null) s.features.put(old.id(), withStatus(old, NodeStatus.STALE));
            invalidateDownstream(s, "feat:" + e.getSupersedes());
        }
        s.features.put(f.id(), f);
    }

    private void applyAccept(HistoryState s, Event e) {
        JsonNode p = e.getPayload();
        String regionId = p.path("regionId").asText();
        String baselineId = p.path("baselineId").asText();
        s.acceptedBaseline.put(regionId, baselineId);
    }

    private void applyReject(HistoryState s, Event e) {
        JsonNode p = e.getPayload();
        String id = p.path("candidateId").asText();
        s.rejected.put(id, p.path("reason").asText(""));
        if (s.baselines.containsKey(id)) {
            s.baselines.put(id, withStatus(s.baselines.get(id), NodeStatus.SUPERSEDED));
        }
        if (s.features.containsKey(id)) {
            s.features.put(id, withStatus(s.features.get(id), NodeStatus.SUPERSEDED));
        }
    }

    private void applyAdjust(HistoryState s, Event e) {
        // A parameter adjustment (moved handles/changed tangent) is followed by
        // new derivation events; here it only invalidates the affected region's
        // existing results, which remain in the log for time travel.
        JsonNode p = e.getPayload();
        invalidateDownstream(s, "tan:" + p.path("tangentId").asText(""));
        invalidateDownstream(s, "region:" + p.path("regionId").asText(""));
    }

    private void applyAmend(HistoryState s, Event e) {
        // Local source correction: only the named fragment changes; downstream
        // invalidation is scoped to that fragment only.
        JsonNode p = e.getPayload();
        invalidateDownstream(s, "seg:" + p.path("segmentId").asText(""));
    }

    private void invalidateDownstream(HistoryState s, String sourceNode) {
        for (String id : graph.downstream(sourceNode)) markStale(s, id);
    }

    private void markStale(HistoryState s, String nodeId) {
        if (nodeId.startsWith("bl:")) {
            String id = nodeId.substring(3);
            BaselineResult b = s.baselines.get(id);
            if (b != null && b.status() != NodeStatus.SUPERSEDED) s.baselines.put(id, withStatus(b, NodeStatus.STALE));
        } else if (nodeId.startsWith("feat:")) {
            markFeatureStale(s, nodeId.substring(5));
        }
    }

    private void markFeatureStale(HistoryState s, String id) {
        FeatureResult f = s.features.get(id);
        if (f != null && f.status() != NodeStatus.SUPERSEDED) s.features.put(id, withStatus(f, NodeStatus.STALE));
    }

    private BaselineResult withStatus(BaselineResult b, NodeStatus st) {
        return new BaselineResult(b.id(), b.regionId(), b.mode(), b.slopeMilliwattPerCelsius(),
                b.interceptMilliwatt(), b.leftAnchorCelsius(), b.rightAnchorCelsius(), b.rmseMilliwatt(),
                st, b.ruleVersion(), b.dependencyIds(), b.judgments(), b.derivedAtSeq());
    }

    private FeatureResult withStatus(FeatureResult f, NodeStatus st) {
        return new FeatureResult(f.id(), f.regionId(), f.baselineId(), f.kind(), f.onset(), f.peak(),
                f.endset(), f.deltaHTotal(), f.deltaHSpecific(), f.sign(), st, f.massIdUsed(),
                f.calibrationIdUsed(), f.ruleVersion(), f.errorBudget(), f.judgments(),
                f.dependencyIds(), f.derivedAtSeq());
    }

    /** Convenience for services that need a fresh id without storing it first. */
    public static String newId(String prefix) { return Ids.newDecisionId(prefix); }
}
