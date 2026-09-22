package org.dsc.engine;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dsc.domain.DscParser;
import org.dsc.domain.Models.*;
import org.dsc.store.EventRecord;
import org.dsc.store.EventRecord;
import org.dsc.store.Store;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用编排：把“仪器输出、质量、速率、气氛、标定、切线”分别存档，
 * 每次推导独立成版本，依赖图驱动增量重算；质量修订使归一化派生整体过期。
 */
public final class CaseService {
    private final Store store;

    public CaseService(Store store) { this.store = store; }

    public Store store() { return store; }

    public String head(String caseId) {
        try { return store.headEventId(caseId); }
        catch (SQLException e) { throw new Store.StoreException("head lookup failed", e); }
    }

    // ----------------------------------------------------------- case / sources

    public EventRecord openCase(String caseId, String sampleName, String material, String actor) {
        return store.appendEvent(caseId, "CASE_OPENED", actor, null,
                EventRecord.payload("sampleName", sampleName, "material", material), null);
    }

    /**
     * 分别存档一个直接来源。sourceId 稳定（如 src-mass），修订时 version+1 并作废旧版。
     * kind: INSTRUMENT_TRACE / MASS / SCAN_RATE / ATMOSPHERE / TEMP_CALIBRATION / TANGENT
     */
    public EventRecord recordSource(String caseId, String sourceId, String kind,
                                    String segmentId, String rawText, String unit,
                                    String actor, String basis) {
        int nextVersion = SourceState.versionOf(store, sourceId) + 1;
        boolean amend = nextVersion > 1;
        String type = amend ? "SOURCE_AMENDED" : "SOURCE_RECORDED";
        ObjectNode payload = EventRecord.payload("sourceId", sourceId, "kind", kind,
                "version", String.valueOf(nextVersion), "unit", unit == null ? "" : unit,
                "scope", "direct-source", "segmentId", segmentId == null ? "" : segmentId);
        EventRecord ev = store.appendEvent(caseId, type, actor, basis, payload, eventId -> {
            if (amend) {
                store.supersedeSource(sourceId, nextVersion - 1, sourceId + ":v" + nextVersion);
                payload.put("amendedFromVersion", String.valueOf(nextVersion - 1));
            }
            store.insertSource(eventId, caseId, sourceId, nextVersion, kind, segmentId, rawText, unit);
        });
        if (amend) propagateAmendment(caseId, sourceId, nextVersion, kind, actor, ev);
        return ev;
    }

    // ----------------------------------------------------------- parse / derive

    /** 解析仪器输出段并登记诊断（反向热流、非等间隔等都在此留痕）。 */
    public EventRecord parseSegment(String caseId, int seqNo, String segmentId, String rawBlock,
                                    String actor, String basis) {
        DscParser.ParsedSegment parsed = DscParser.parse(segmentId, seqNo, rawBlock);
        Segment seg = parsed.segment;
        String entityId = "ent-seg-" + caseId + "-" + seqNo;
        ObjectNode payload = EventRecord.payload("segmentId", segmentId, "entityId", entityId,
                "direction", seg.direction().name(), "convention", seg.convention().name(),
                "pointCount", String.valueOf(seg.points().size()),
                "nonUniform", String.valueOf(seg.nonUniformSpacing()));
        ArrayNode diag = payload.putArray("diagnostics");
        seg.diagnostics().forEach(diag::add);
        return store.appendEvent(caseId, "SEGMENT_PARSED", actor, basis, payload, eventId -> {
            store.insertSource(eventId, caseId, "src-trace-" + seqNo, 1,
                    "INSTRUMENT_TRACE", segmentId, rawBlock, "K,W/g(derived)");
            ObjectNode json = EventRecord.MAPPER.valueToTree(seg);
            store.insertEntity(eventId, caseId, entityId, 1, "SEGMENT",
                    "segment#" + seqNo + ":" + seg.direction(), json,
                    seg.diagnostics().isEmpty() ? "CURRENT" : "CURRENT_WITH_DIAGNOSTICS");
            store.addDep(eventId, entityId, 1, "src-trace-" + seqNo, 1);
        });
    }

    /**
     * 为一段生成并列基线候选；候选全部落库（accepted=false），等待人工裁定。
     * 空锚区间/退化拟合登记诊断，不产生伪造候选。
     */
    public List<EventRecord> deriveBaselines(String caseId, int seqNo, String segmentId,
                                             TempWindow eventWindow, TangentSelection tan,
                                             String actor, String basis) {
        Segment seg = requireSegment(caseId, seqNo);
        validateSameSegment(seg, eventWindow, tan);
        List<Baselines.Fit> fits = Baselines.candidates(seg, tan);
        List<EventRecord> out = new ArrayList<>();
        ObjectNode common = EventRecord.payload("segmentId", segmentId,
                "windowStartK", fmt(eventWindow.tStartK()), "windowEndK", fmt(eventWindow.tEndK()));
        String[] methods = {"LINEAR_TANGENT", "LINEAR_REGRESSION", "CONSTANT"};
        for (int i = 0; i < fits.size(); i++) {
            Baselines.Fit f = fits.get(i);
            String blId = "bl-" + caseId + "-" + seqNo + "-" + (i + 1);
            ObjectNode p = common.deepCopy();
            p.put("baselineId", blId);
            p.put("method", methods[Math.min(i, methods.length - 1)]);
            p.put("slope", fmt(f.slope));
            p.put("intercept", fmt(f.intercept));
            p.put("rmse", fmt(f.rmse));
            p.put("anchorPoints", String.valueOf(f.points));
            p.put("judgement", "CANDIDATE_RETAINED: multiple plausible baselines kept distinct");
            int idx = i;
            out.add(store.appendEvent(caseId, "BASELINE_DERIVED", actor, basis, p, eventId -> {
                ObjectNode json = EventRecord.MAPPER.createObjectNode();
                json.put("baselineId", blId);
                json.put("segmentId", segmentId);
                json.put("method", methods[Math.min(idx, methods.length - 1)]);
                json.put("slope", f.slope);
                json.put("intercept", f.intercept);
                json.put("rmse", f.rmse);
                json.put("anchorPoints", f.points);
                json.put("accepted", false);
                store.insertEntity(eventId, caseId, blId, 1, "BASELINE",
                        methods[Math.min(idx, methods.length - 1)], json, "CURRENT");
                store.addDep(eventId, blId, 1, "ent-seg-" + caseId + "-" + seqNo, 1);
                store.addDep(eventId, blId, 1, "src-tangent-" + seqNo, SourceState.versionOf(store, "src-tangent-" + seqNo));
            }));
        }
        if (fits.isEmpty()) {
            ObjectNode p = common.deepCopy();
            p.put("judgement", "NO_BASELINE: empty/degenerate anchor windows; no candidate fabricated");
            out.add(store.appendEvent(caseId, "BASELINE_DERIVED", actor, basis, p, null));
        }
        return out;
    }

    /** 计算转折结果（绑定某基线候选）。空区间、跨段、方向缺失在此拒绝并留事件。 */
    public EventRecord deriveTransition(String caseId, int seqNo, String baselineId,
                                        TempWindow window, boolean glassSuspected,
                                        String actor, String basis) {
        Segment seg = requireSegment(caseId, seqNo);
        String resId = "res-" + caseId + "-" + seqNo + "-" + Math.abs(window.hashCode() % 100000);

        if (Transitions.crossesSegmentBoundary(segmentsOf(caseId), window)) {
            ObjectNode p = EventRecord.payload("segmentId", seg.segmentId(),
                    "judgement", "CROSS_SEGMENT_REJECTED: heating/cooling must not be mixed");
            return store.appendEvent(caseId, "CROSS_SEGMENT_REJECTED", actor, basis, p, null);
        }
        Store.EntityRow blRow;
        try {
            blRow = store.currentEntity(baselineId).orElseThrow();
        } catch (Exception e) {
            throw new IllegalArgumentException("baseline not found: " + baselineId);
        }
        Baselines.Fit fit = new Baselines.Fit(blRow.json().get("slope").asDouble(),
                blRow.json().get("intercept").asDouble(),
                blRow.json().get("rmse").asDouble(),
                blRow.json().get("anchorPoints").asInt());

        Transitions.Analysis a = Transitions.analyze(seg, window, fit);
        if (glassSuspected && !a.rejected) {
            Double tg = Transitions.glassStepMidpoint(seg, window, fit);
            if (tg != null) {
                a.kind = TransitionKind.GLASS_TRANSITION;
                a.peakK = tg;
                a.errorSources.add("GLASS_STEP: Tg is step midpoint; enthalpy is not the primary quantity");
            }
        }
        ObjectNode p = EventRecord.payload("resultId", resId, "segmentId", seg.segmentId(),
                "baselineId", baselineId, "kind", a.kind.name(),
                "emptyWindow", String.valueOf(a.diagnostics.stream().anyMatch(d -> d.startsWith("empty window"))),
                "rejected", String.valueOf(a.rejected));
        if (!Double.isNaN(a.onsetK)) p.put("onsetK", fmt(a.onsetK));
        if (!Double.isNaN(a.peakK)) p.put("peakK", fmt(a.peakK));
        if (!Double.isNaN(a.endsetK)) p.put("endsetK", fmt(a.endsetK));
        if (!Double.isNaN(a.enthalpyJg)) p.put("enthalpyJg", fmt(a.enthalpyJg));
        p.put("uncertaintyJg", fmt(a.uncertaintyJg));
        ArrayNode errs = p.putArray("errorSources");
        a.errorSources.forEach(errs::add);
        ArrayNode diag = p.putArray("diagnostics");
        a.diagnostics.forEach(diag::add);

        int segVersion;
        try { segVersion = store.currentEntity("ent-seg-" + caseId + "-" + seqNo).get().version(); }
        catch (Exception e) { segVersion = 1; }
        final int segVersionF = segVersion;
        final int blVersion = blRow.version();
        return store.appendEvent(caseId, "TRANSITION_DERIVED", actor, basis, p, eventId -> {
            ObjectNode json = EventRecord.MAPPER.createObjectNode();
            json.put("resultId", resId);
            json.put("segmentId", seg.segmentId());
            json.put("baselineId", baselineId);
            json.put("kind", a.kind.name());
            json.put("onsetK", a.onsetK);
            json.put("peakK", a.peakK);
            json.put("endsetK", a.endsetK);
            json.put("enthalpyJg", a.enthalpyJg);
            json.put("uncertaintyJg", a.uncertaintyJg);
            ArrayNode es = json.putArray("errorSources");
            a.errorSources.forEach(es::add);
            ArrayNode dg = json.putArray("diagnostics");
            a.diagnostics.forEach(dg::add);
            String status = a.rejected ? "FAILED"
                    : (a.errorSources.isEmpty() ? "CURRENT" : "CURRENT_WITH_FLAGS");
            store.insertEntity(eventId, caseId, resId, 1, "RESULT",
                    a.kind + "@" + seg.segmentId(), json, status);
            store.addDep(eventId, resId, 1, "ent-seg-" + caseId + "-" + seqNo, segVersionF);
            store.addDep(eventId, resId, 1, baselineId, blVersion);
            store.addDep(eventId, resId, 1, "src-mass", SourceState.versionOf(store, "src-mass"));
        });
    }

    /** 空区间检验：直接产生带判定依据的事件。 */
    public EventRecord checkEmptyWindow(String caseId, int seqNo, TempWindow window,
                                        String actor, String basis) {
        Segment seg = requireSegment(caseId, seqNo);
        int n = Baselines.pointsInWindow(seg, window).size();
        ObjectNode p = EventRecord.payload("segmentId", seg.segmentId(),
                "windowStartK", fmt(window.tStartK()), "windowEndK", fmt(window.tEndK()),
                "sampleCount", String.valueOf(n),
                "judgement", n == 0 ? "EMPTY_WINDOW: no affected results, scope stays local"
                        : "WINDOW_NON_EMPTY:" + n + " samples");
        return store.appendEvent(caseId, "EMPTY_WINDOW_CHECKED", actor, basis, p, null);
    }

    // ----------------------------------------------------------- decisions

    public EventRecord adoptBaseline(String caseId, String baselineId, String actor, String basis) {
        return decide(caseId, baselineId, true, actor, basis);
    }

    public EventRecord rejectBaseline(String caseId, String baselineId, String actor, String basis) {
        return decide(caseId, baselineId, false, actor, basis);
    }

    private EventRecord decide(String caseId, String baselineId, boolean adopt,
                               String actor, String basis) {
        Store.EntityRow row;
        try { row = store.currentEntity(baselineId).orElseThrow(); }
        catch (Exception e) { throw new IllegalArgumentException("no such baseline: " + baselineId); }
        ObjectNode p = EventRecord.payload("baselineId", baselineId,
                "decision", adopt ? "ADOPTED" : "REJECTED",
                "judgement", adopt
                        ? "BASELINE_ADOPTED: other candidates retained, not overwritten"
                        : "BASELINE_REJECTED: kept for audit, reversible by later event");
        return store.appendEvent(caseId, adopt ? "BASELINE_ADOPTED" : "BASELINE_REJECTED",
                actor, basis, p, eventId -> {
                    ObjectNode json = row.json().deepCopy();
                    json.put("accepted", adopt);
                    int v = store.maxEntityVersion(baselineId) + 1;
                    store.insertEntity(eventId, caseId, baselineId, v, "BASELINE",
                            row.label(), json, adopt ? "CURRENT_ADOPTED" : "CURRENT_REJECTED");
                    if (adopt) staleResultsUsing(caseId, baselineId, "BASELINE_PREFERENCE_CHANGED", eventId);
                });
    }

    /**
     * 质量修订：插入新版本并使所有归一化派生（基线/结果）过期。
     * 旧质量版本与旧派生都保留，不覆盖历史。
     */
    public EventRecord reviseMass(String caseId, String newRawMassMg, String actor, String basis) {
        double mg;
        try { mg = Double.parseDouble(newRawMassMg.replaceAll("[^0-9eE+\\-.]", "")); }
        catch (Exception e) { throw new IllegalArgumentException("bad mass: " + newRawMassMg); }
        EventRecord ev = recordSource(caseId, "src-mass", "MASS", null,
                newRawMassMg, "mg", actor, basis);
        ObjectNode p = EventRecord.payload("massMg", String.valueOf(mg),
                "judgement", "MASS_REVISED: all normalized derivations are STALE until recomputed",
                "scope", "all-normalized-derived");
        return store.appendEvent(caseId, "MASS_REVISED", actor, ev.eventId(), p, eventId -> {
            ObjectNode json = EventRecord.MAPPER.createObjectNode();
            json.put("massMg", mg);
            json.put("rawText", newRawMassMg);
            json.put("unit", "mg");
            int v;
            try { v = store.currentEntity("ent-mass").map(Store.EntityRow::version).orElse(0) + 1; }
            catch (SQLException e) { throw new RuntimeException(e); }
            store.insertEntity(eventId, caseId, "ent-mass", v, "MASS",
                    "mass " + mg + " mg", json, "CURRENT");
            staleAll(caseId, List.of("BASELINE", "RESULT"),
                    "MASS_VERSION_CHANGED", eventId);
        });
    }

    /** 切线参数调整（拖区间）：记录新版本，旧版本保留；依赖该切线的基线过期。 */
    public EventRecord adjustTangent(String caseId, int seqNo, TangentSelection tan,
                                     String actor, String basis) {
        String sourceId = "src-tangent-" + seqNo;
        String raw = tan.leftWindow().tStartK() + "," + tan.leftWindow().tEndK()
                + "|" + tan.rightWindow().tStartK() + "," + tan.rightWindow().tEndK();
        EventRecord rec = recordSource(caseId, sourceId, "TANGENT", tan.segmentId(),
                raw, "K", actor, basis);
        ObjectNode p = EventRecord.payload("segmentId", tan.segmentId(),
                "judgement", "TANGENT_ADJUSTED: dependent baselines STALE; prior versions retained");
        return store.appendEvent(caseId, "TANGENT_ADJUSTED", actor, rec.eventId(), p, eventId -> {
            ObjectNode json = EventRecord.MAPPER.valueToTree(tan);
            String entId = "ent-tangent-" + seqNo;
            int v = store.maxEntityVersion(entId) + 1;
            store.insertEntity(eventId, caseId, entId, v, "TANGENT",
                    "tangent#" + seqNo, json, "CURRENT");
            staleByKindForSegment(caseId, "BASELINE", tan.segmentId(),
                    "TANGENT_VERSION_CHANGED", eventId);
        });
    }

    // ----------------------------------------------------------- propagation

    private void propagateAmendment(String caseId, String sourceId, int newVersion,
                                    String kind, String actor, EventRecord sourceEvent) {
        String reason = switch (kind) {
            case "INSTRUMENT_TRACE" -> "INSTRUMENT_TRACE_CHANGED";
            case "SCAN_RATE" -> "SCAN_RATE_CHANGED";
            case "ATMOSPHERE" -> "ATMOSPHERE_CHANGED";
            case "TEMP_CALIBRATION" -> "TEMP_CALIBRATION_CHANGED";
            case "TANGENT" -> "TANGENT_VERSION_CHANGED";
            default -> "SOURCE_AMENDED";
        };
        ObjectNode p = EventRecord.payload("sourceId", sourceId, "newVersion", String.valueOf(newVersion),
                "scope", "dependent-only", "judgement",
                reason + ": dependency graph propagates only to affected nodes; unrelated stay CURRENT");
        store.appendEvent(caseId, "DERIVATION_INVALIDATED", actor, sourceEvent.eventId(), p, eventId -> {
            try {
                for (String dep : store.dependentsOf(sourceId, newVersion - 1)) {
                    store.setStatus(dep, null, "STALE:" + reason);
                }
                // 仪器段改动：段及其下游基线/结果过期
                if ("INSTRUMENT_TRACE".equals(kind)) {
                    staleAll(caseId, List.of("SEGMENT", "BASELINE", "RESULT"), reason, eventId);
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    private void staleResultsUsing(String caseId, String baselineId, String reason, String eventId)
            throws SQLException {
        for (Store.EntityRow r : store.entitiesByKind(caseId, "RESULT")) {
            if (baselineId.equals(r.json().path("baselineId").asText(null))) {
                store.setStatus(r.entityId(), null, "STALE:" + reason);
            }
        }
    }

    private void staleByKindForSegment(String caseId, String kind, String segmentId,
                                       String reason, String eventId) throws SQLException {
        for (Store.EntityRow r : store.entitiesByKind(caseId, kind)) {
            if (segmentId.equals(r.json().path("segmentId").asText(null))) {
                store.setStatus(r.entityId(), null, "STALE:" + reason);
            }
        }
    }

    private void staleAll(String caseId, List<String> kinds, String reason, String eventId)
            throws SQLException {
        for (String kind : kinds) {
            for (Store.EntityRow r : store.entitiesByKind(caseId, kind)) {
                if (!r.status().startsWith("STALE")) store.setStatus(r.entityId(), null, "STALE:" + reason);
            }
        }
    }

    // ----------------------------------------------------------- read helpers

    private Segment requireSegment(String caseId, int seqNo) {
        try {
            Store.EntityRow row = store.currentEntity("ent-seg-" + caseId + "-" + seqNo)
                    .orElseThrow(() -> new IllegalStateException("segment not parsed: " + seqNo));
            return EventRecord.MAPPER.treeToValue(row.json(), Segment.class);
        } catch (Exception e) {
            throw new IllegalStateException("cannot load segment " + seqNo, e);
        }
    }

    private List<Segment> segmentsOf(String caseId) {
        try {
            List<Segment> out = new ArrayList<>();
            for (Store.EntityRow r : store.entitiesByKind(caseId, "SEGMENT")) {
                Store.EntityRow cur = store.currentEntity(r.entityId()).orElse(null);
                if (cur != null && cur.version() == r.version())
                    out.add(EventRecord.MAPPER.treeToValue(r.json(), Segment.class));
            }
            return out;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void validateSameSegment(Segment seg, TempWindow w, TangentSelection tan) {
        if (!tan.segmentId().equals(seg.segmentId()))
            throw new IllegalArgumentException("tangent belongs to another segment");
        if (!Transitions.windowInsideSegment(seg, tan.leftWindow())
                || !Transitions.windowInsideSegment(seg, tan.rightWindow()))
            throw new IllegalArgumentException("tangent anchor window leaves segment range");
    }

    private static String fmt(double v) { return String.format(java.util.Locale.ROOT, "%.4f", v); }
}
