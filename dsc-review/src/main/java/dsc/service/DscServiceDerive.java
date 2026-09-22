package dsc.service;

import dsc.core.Ids;
import dsc.derive.BaselineResult;
import dsc.derive.FeatureResult;
import dsc.events.*;
import dsc.model.*;
import dsc.storage.Json;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.*;

/** Derivation + amendment commands, split out of {@link DscService} for size. */
public final class DscServiceDerive {

    private final DscService svc;
    private final dsc.storage.EventStore store;

    public DscServiceDerive(DscService svc) {
        this.svc = svc;
        this.store = svc.storeRef();
    }

    /**
     * Recompute one region: all legal baseline candidates, then the feature set
     * under the currently accepted baseline (or every candidate when none is
     * accepted yet). The whole derivation is one atomic batch — pointers only
     * move after every event of the batch is durable.
     */
    public RecomputeReport recompute(String hid, String regionId) {
        HistoryState before = svc.state(hid);
        long base = before.headSeq;
        HistoryState.RegionRef rr = before.regions.get(regionId);
        if (rr == null) throw new IllegalArgumentException("unknown region " + regionId);
        Region region = rr.region;
        ScanSegment seg = before.segment(region.segmentId());
        TangentSelection tan = latestTangent(before, regionId);
        MassVersion mass = before.currentMass();
        Calibration cal = before.currentCalibration();

        BaselineRulesAccess rules = new BaselineRulesAccess();
        List<BaselineResult> candidates = rules.derive(regionId, region, seg, tan, cal);
        List<Event> batch = new ArrayList<>();

        List<String> newBaselineIds = new ArrayList<>();
        Set<String> previous = new LinkedHashSet<>();
        for (BaselineResult old : before.baselinesForRegion(regionId)) previous.add(old.id());

        for (BaselineResult b0 : candidates) {
            BaselineResult b = new BaselineResult(b0.id(), b0.regionId(), b0.mode(),
                    b0.slopeMilliwattPerCelsius(), b0.interceptMilliwatt(), b0.leftAnchorCelsius(),
                    b0.rightAnchorCelsius(), b0.rmseMilliwatt(),
                    previous.contains(b0.id()) ? NodeStatus.VALID
                            : (candidates.size() > 1 ? NodeStatus.MULTIPLE : NodeStatus.VALID),
                    b0.ruleVersion(), withVersions(b0.dependencyIds(), before, tan, cal),
                    b0.judgments(), -1);
            newBaselineIds.add(b.id());
            Event e = new Event(EventType.BASELINE_DERIVED, b.id(), Instant.now(), "rules",
                    "baseline candidate derived (one of possibly several)",
                    JsonStorageCodecs.writeBaseline(b), b.judgments(), base,
                    b.dependencyIds(), null);
            batch.add(e);
        }

        // Features under the accepted baseline, or under every usable candidate.
        List<FeatureResult> features = new ArrayList<>();
        String accepted = before.acceptedBaseline.get(regionId);
        List<BaselineResult> chosen = new ArrayList<>();
        for (BaselineResult b : candidates) {
            if (accepted != null) { if (b.id().equals(accepted)) chosen.add(b); }
            else if (b.acceptedEligible()) chosen.add(b);
        }
        FeatureRulesAccess fr = new FeatureRulesAccess();
        for (BaselineResult b : chosen) {
            FeatureResult f = fr.derive(region, seg, b, mass, cal, seg.convention());
            List<String> deps = new ArrayList<>(f.dependencyIds());
            if (tan != null) deps.add("tan:" + tan.tangentId());
            deps.add("calver:" + (cal == null ? "none" : cal.calibrationId()));
            FeatureResult stamped = new FeatureResult(f.id(), f.regionId(), f.baselineId(), f.kind(),
                    f.onset(), f.peak(), f.endset(), f.deltaHTotal(), f.deltaHSpecific(), f.sign(),
                    f.status(), f.massIdUsed(), f.calibrationIdUsed(), f.ruleVersion(),
                    f.errorBudget(), f.judgments(), deps, -1);
            features.add(stamped);
            Event e = new Event(EventType.FEATURES_DERIVED, stamped.id(), Instant.now(), "rules",
                    "onset/peak/endset and enthalpy derived",
                    JsonStorageCodecs.writeFeature(stamped), stamped.judgments(), base,
                    deps, null);
            batch.add(e);
        }

        store.commitBatch(hid, batch, base);
        HistoryState after = svc.state(hid);

        Set<String> affected = new LinkedHashSet<>();
        for (Event e : batch) affected.addAll(svc.graph().downstream(
                e.getType() == EventType.BASELINE_DERIVED ? "bl:" + e.getEntityId()
                        : "feat:" + e.getEntityId()));
        affected.addAll(newBaselineIds);
        return new RecomputeReport(regionId, newBaselineIds,
                features.stream().map(FeatureResult::id).toList(), affected,
                unaffectedRegions(before, regionId), batch.stream().map(Event::getSeq).toList());
    }

    /** Move tangent handles, then recompute: a parameter adjustment, not an overwrite. */
    public RecomputeReport adjust(String hid, Commands.AdjustParameter c) {
        HistoryState s = svc.state(hid);
        long base = s.headSeq;
        String tangentId = c.tangentId() != null ? c.tangentId() : Ids.newDecisionId("tan");
        ObjectNode p = Json.MAPPER.createObjectNode();
        p.put("regionId", c.regionId());
        p.put("tangentId", tangentId);
        p.put("leftLo", c.leftLo());
        p.put("leftHi", c.leftHi());
        p.put("rightLo", c.rightLo());
        p.put("rightHi", c.rightHi());
        p.put("note", c.note());
        Event adj = new Event(EventType.PARAMETER_ADJUSTED, tangentId, Instant.now(), c.actor(),
                "tangent handles moved; old derivation retained", p, List.of(), base,
                List.of("region:" + c.regionId(), "tan:" + tangentId), null);
        store.commitBatch(hid, List.of(adj), base);

        ObjectNode tp = Json.MAPPER.createObjectNode();
        tp.put("regionId", c.regionId());
        tp.put("leftLo", c.leftLo());
        tp.put("leftHi", c.leftHi());
        tp.put("rightLo", c.rightLo());
        tp.put("rightHi", c.rightHi());
        tp.put("mode", TangentSelection.MODE_TWO_SIDED);
        tp.put("note", c.note());
        Event sel = new Event(EventType.TANGENT_SELECTED, tangentId, Instant.now(), c.actor(),
                "replacement tangent selection", tp, List.of(), base + 1,
                List.of("region:" + c.regionId()), null);
        store.commitBatch(hid, List.of(sel), base + 1);
        return recompute(hid, c.regionId());
    }

    /**
     * Local source correction: only the named fragment is replaced; propagation
     * is scoped to descendants of that one segment, and the old fragment stays
     * in the log (superseded) for audit.
     */
    public RecomputeReport amendSource(String hid, Commands.AmendSource c, List<String> regionIdsToRecompute) {
        HistoryState s = svc.state(hid);
        long base = s.headSeq;
        TraceImport rep = c.replacement();
        rep.segmentId = c.segmentId() == null ? Ids.newDecisionId("seg") : Ids.newDecisionId("seg");
        Event e = new Event(EventType.SOURCE_AMENDED, rep.segmentId, Instant.now(), c.actor(),
                c.reason(), JsonStorageCodecs.writeTrace(rep), List.of(
                        Judgment.of("LOCAL_SOURCE_AMENDMENT", Judgment.Severity.INFO, rep.segmentId,
                                "only this instrument fragment changed; recompute is scoped to its descendants; "
                                        + "other segments' results remain valid",
                                "amend-1.0.0", Map.of("segmentId", c.segmentId()))),
                base, List.of("seg:" + c.segmentId()), c.segmentId());
        store.commitBatch(hid, List.of(e), base);
        List<String> baselineIds = new ArrayList<>();
        List<String> featureIds = new ArrayList<>();
        List<String> affectedRegions = new ArrayList<>();
        for (String rid : regionIdsToRecompute) {
            RecomputeReport r = recompute(hid, rid);
            baselineIds.addAll(r.baselineIds());
            featureIds.addAll(r.featureIds());
            affectedRegions.add(r.regionId());
        }
        return new RecomputeReport(String.join(",", affectedRegions), baselineIds, featureIds,
                svc.graph().downstream("seg:" + c.segmentId()), List.of(), List.of());
    }

    private List<String> withVersions(List<String> deps, HistoryState s,
                                      TangentSelection tan, Calibration cal) {
        List<String> out = new ArrayList<>(deps);
        if (tan != null) out.add("tan:" + tan.tangentId());
        if (cal != null) out.add("cal:" + cal.calibrationId());
        if (s.currentMass() != null) out.add("mass:" + s.currentMass().massId());
        return out;
    }

    private TangentSelection latestTangent(HistoryState s, String regionId) {
        TangentSelection best = null;
        long bestSeq = -1;
        for (HistoryState.TangentRef tr : s.tangents.values()) {
            if (tr.tangent.regionId().equals(regionId) && tr.selectedSeq > bestSeq) {
                best = tr.tangent;
                bestSeq = tr.selectedSeq;
            }
        }
        return best;
    }

    private List<String> unaffectedRegions(HistoryState s, String changedRegion) {
        List<String> out = new ArrayList<>();
        for (String rid : s.regions.keySet()) if (!rid.equals(changedRegion)) out.add(rid);
        return out;
    }

    /** Public accessor to package-private rules for tests/service composition. */
    public static final class BaselineRulesAccess extends dsc.derive.BaselineRules {}
    public static final class FeatureRulesAccess extends dsc.derive.FeatureRules {}

    public record RecomputeReport(String regionId, List<String> baselineIds, List<String> featureIds,
                                  Set<String> affectedNodeIds, List<String> unaffectedRegions,
                                  List<Long> eventSeqs) {}
}
