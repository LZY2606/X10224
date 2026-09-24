package org.materials.dsc.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.materials.dsc.domain.AtmosphereRecord;
import org.materials.dsc.domain.BaselineCandidate;
import org.materials.dsc.domain.CaseInfo;
import org.materials.dsc.domain.CaseState;
import org.materials.dsc.domain.Diagnostic;
import org.materials.dsc.domain.DependencyNode;
import org.materials.dsc.domain.Direction;
import org.materials.dsc.domain.ThermalSign;
import org.materials.dsc.domain.DisplayValue;
import org.materials.dsc.domain.EnthalpyResult;
import org.materials.dsc.domain.ErrorComponent;
import org.materials.dsc.domain.Event;
import org.materials.dsc.domain.InstrumentOutput;
import org.materials.dsc.domain.MassRecord;
import org.materials.dsc.domain.Quantity;
import org.materials.dsc.domain.RateRecord;
import org.materials.dsc.domain.SampleRow;
import org.materials.dsc.domain.Segment;
import org.materials.dsc.domain.SignConvention;
import org.materials.dsc.domain.TangentSelection;
import org.materials.dsc.domain.TemperatureCalibration;
import org.materials.dsc.domain.Transition;
import org.materials.dsc.domain.TransitionKind;
import org.materials.dsc.domain.Units;
import org.materials.dsc.store.Json;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 事件流 -> CaseState 的纯函数投影。
 * 回到任意旧事件时，投影得到的是当时的依赖版本与当时可见结论，
 * 不使用最新计算结果覆盖历史。派生事件直接携带当时算好的数值快照。
 */
public final class Projector {
    private Projector() {
    }

    public static CaseState project(List<Event> events) {
        CaseState st = new CaseState();
        for (Event e : events) {
            apply(st, e);
        }
        st.seq = events.isEmpty() ? 0 : events.get(events.size() - 1).seq();
        return st;
    }

    public static void apply(CaseState st, Event e) {
        st.events.add(e);
        st.revision++;
        ObjectNode p = (ObjectNode) e.payload();
        switch (e.type()) {
            case Event.CASE_CREATED -> {
                st.info = new CaseInfo(e.caseId(), text(p, "name"), text(p, "material"),
                        e.at(), text(p, "note"));
            }
            case Event.CASE_IMPORTED -> {
                if (st.info == null) {
                    st.info = new CaseInfo(e.caseId(), text(p, "name"), text(p, "material"),
                            e.at(), text(p, "note"));
                }
            }
            case Event.OUTPUT_ARCHIVED -> st.outputs.put(p.get("outputId").asText(), readOutput(p));
            case Event.MASS_RECORDED, Event.MASS_REVISED -> {
                MassRecord m = readMass(p, e);
                st.masses.put(m.id(), m);
                if (!st.massOrder.contains(m.id())) {
                    st.massOrder.add(m.id());
                }
            }
            case Event.RATE_RECORDED -> st.rates.put(p.get("id").asText(),
                    new RateRecord(p.get("id").asText(), readQuantity(p.get("rate")),
                            text(p, "rawText"), seq(e), e.at()));
            case Event.ATMOSPHERE_RECORDED -> st.atmospheres.put(p.get("id").asText(),
                    new AtmosphereRecord(p.get("id").asText(), text(p, "gas"),
                            p.hasNonNull("flowRate") ? readQuantity(p.get("flowRate")) : null,
                            text(p, "rawText"), seq(e), e.at()));
            case Event.CALIBRATION_RECORDED -> {
                List<String> refs = new ArrayList<>();
                if (p.has("pointRefs")) {
                    p.get("pointRefs").forEach(n -> refs.add(n.asText()));
                }
                st.calibrations.put(p.get("id").asText(), new TemperatureCalibration(
                        p.get("id").asText(), p.get("slope").asDouble(),
                        p.get("offsetCelsius").asDouble(), text(p, "standardRef"),
                        text(p, "note"), seq(e), e.at(), refs));
            }
            case Event.SEGMENTS_DERIVED -> readSegments(st, p, e);
            case Event.TRANSITIONS_DETECTED -> readTransitions(st, p, e);
            case Event.BASELINES_DERIVED -> readBaselines(st, p, e);
            case Event.BASELINE_ADOPTED -> adoptBaseline(st, p);
            case Event.BASELINE_REJECTED -> rejectTransition(st, p);
            case Event.TANGENT_SELECTED -> {
                TangentSelection t = new TangentSelection(p.get("id").asText(),
                        text(p, "traceId"), text(p, "segmentId"),
                        d(p, "leftX1"), d(p, "leftX2"), d(p, "rightX1"), d(p, "rightX2"),
                        text(p, "label"), text(p, "note"), seq(e), e.at());
                st.tangents.put(t.id(), t);
            }
            case Event.ANALYSIS_DERIVED -> readAnalysis(st, p, e, false, null);
            case Event.DERIVED_INVALIDATED -> invalidate(st, p);
            case Event.INTERVAL_ADJUSTED -> adjustInterval(st, p, e);
            case Event.DIAGNOSTIC -> st.diagnostics.add(new Diagnostic(
                    p.get("id").asText(), text(p, "severity", "warning"),
                    text(p, "code"), text(p, "message"), text(p, "sourceRef"), seq(e)));
            case Event.CASE_NOTE -> {
            }
            default -> {
            }
        }
        if (p.has("dependencyNodes")) {
            for (JsonNode dn : p.get("dependencyNodes")) {
                List<String> deps = new ArrayList<>();
                dn.get("dependsOn").forEach(x -> deps.add(x.asText()));
                DependencyUpdater.putNode(st, new org.materials.dsc.domain.DependencyNode(
                        dn.get("nodeId").asText(), dn.get("kind").asText(),
                        dn.path("label").asText(""), dn.get("version").asInt(),
                        String.valueOf(e.seq()), dn.path("ruleVersion").asText(null),
                        dn.path("status").asText(DependencyNode.STATUS_OK),
                        null, deps));
            }
        }
        if (Event.DERIVED_INVALIDATED.equals(e.type())) {
            propagateInvalidation(st, p);
        }
    }

    private static void propagateInvalidation(CaseState st, ObjectNode p) {
        String changedSource = p.get("changedSource").asText();
        String reason = p.get("reason").asText();
        for (String impacted : DependencyUpdater.impacted(st, changedSource)) {
            DependencyUpdater.mark(st, impacted, reason + " (via " + changedSource + ")");
        }
    }

    private static String seq(Event e) {
        return String.valueOf(e.seq());
    }

    private static String text(ObjectNode p, String key) {
        return p.hasNonNull(key) ? p.get(key).asText() : null;
    }

    private static String text(ObjectNode p, String key, String def) {
        return p.hasNonNull(key) ? p.get(key).asText() : def;
    }

    private static double d(ObjectNode p, String key) {
        return p.get(key).asDouble();
    }

    // ---------- payload readers ----------

    static Quantity readQuantity(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        String rawText = n.path("rawText").asText(null);
        String rawUnit = n.path("rawUnit").asText(null);
        if (n.hasNonNull("value")) {
            Units u = Units.fromToken(n.path("unit").asText("")).orElse(Units.DIMENSIONLESS);
            return new Quantity(n.get("value").asDouble(), u, rawText, rawText == null ? u.symbol() : rawUnit);
        }
        return Quantity.raw(rawText, rawUnit);
    }

    private static InstrumentOutput readOutput(ObjectNode p) {
        JsonNode o = p.get("output");
        List<SampleRow> rows = new ArrayList<>();
        JsonNode rowsNode = o.get("rows");
        for (JsonNode r : rowsNode) {
            rows.add(new SampleRow(r.get("index").asInt(),
                    r.get("rawTemperature").asText(), r.get("tempUnit").asText(),
                    r.get("rawHeatFlow").asText(), r.get("hfUnit").asText(),
                    r.hasNonNull("normalizedT") ? r.get("normalizedT").asDouble() : null,
                    r.hasNonNull("normalizedHF") ? r.get("normalizedHF").asDouble() : null,
                    r.get("nonConvertible").asBoolean()));
        }
        List<Diagnostic> diags = readDiagnostics(o.get("diagnostics"));
        return new InstrumentOutput(o.get("id").asText(), o.get("fileName").asText(),
                o.path("instrument").asText("(unknown)"),
                Direction.valueOf(o.get("declaredDirection").asText()),
                SignConvention.valueOf(o.get("signConvention").asText()),
                o.path("declaredDirectionRaw").asText(null),
                o.path("signConventionRaw").asText(null),
                Units.fromToken(o.get("tempUnit").asText()).orElse(Units.CELSIUS),
                Units.fromToken(o.get("hfUnit").asText()).orElse(Units.MILLIWATT),
                rows, diags, o.get("nonUniformTemperature").asBoolean(),
                o.get("medianStepCelsius").asDouble(),
                o.path("importEventSeq").asText(null),
                o.get("importedAt").asText(), o.get("sha256").asText());
    }

    private static List<String> readDiagnosticStrings(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (JsonNode d : arr) {
            out.add(d.path("code").asText() + ": " + d.path("message").asText());
        }
        return out;
    }

    private static List<Diagnostic> readDiagnostics(JsonNode arr) {
        List<Diagnostic> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (JsonNode d : arr) {
            out.add(new Diagnostic(d.get("id").asText(), d.path("severity").asText("warning"),
                    d.get("code").asText(), d.get("message").asText(),
                    d.path("sourceRef").asText(null), d.path("atEventSeq").asText(null)));
        }
        return out;
    }

    private static MassRecord readMass(ObjectNode p, Event e) {
        return new MassRecord(p.get("id").asText(), p.get("version").asInt(),
                readQuantity(p.get("mass")), text(p, "note"), seq(e), e.at());
    }

    private static void readSegments(CaseState st, ObjectNode p, Event e) {
        st.segments.clear();
        for (JsonNode n : p.get("segments")) {
            Segment seg = new Segment(n.get("id").asText(), n.get("traceId").asText(),
                    Direction.valueOf(n.get("direction").asText()),
                    n.get("startIndex").asInt(), n.get("endIndex").asInt(),
                    n.get("tStartCelsius").asDouble(), n.get("tEndCelsius").asDouble(),
                    readQuantity(n.get("rate")), n.path("cycleLabel").asText(null));
            st.segments.put(seg.id(), seg);
        }
    }

    private static void readTransitions(CaseState st, ObjectNode p, Event e) {
        for (JsonNode n : p.get("transitions")) {
            String id = n.get("id").asText();
            st.transitions.remove(id);
            List<String> overlaps = new ArrayList<>();
            n.get("overlapWith").forEach(x -> overlaps.add(x.asText()));
            List<String> bls = new ArrayList<>();
            if (n.has("baselineCandidateIds")) {
                n.get("baselineCandidateIds").forEach(x -> bls.add(x.asText()));
            }
            Transition tr = new Transition(id, n.get("traceId").asText(),
                    n.get("segmentId").asText(),
                    TransitionKind.valueOf(n.get("kindHint").asText()),
                    TransitionKind.valueOf(n.get("resolvedKind").asText()),
                    Direction.valueOf(n.get("segmentDirection").asText()),
                    ThermalSign.valueOf(n.get("thermalSign").asText()),
                    n.get("loIndex").asInt(), n.get("peakIndex").asInt(), n.get("hiIndex").asInt(),
                    readDv(n.get("onset")), readDv(n.get("peak")), readDv(n.get("endset")),
                    overlaps, bls, n.path("adoptedBaselineId").asText(null),
                    n.get("status").asText(), seq(e), n.path("ruleVersion").asText(null),
                    readDiagnosticStrings(n.get("diagnostics")));
            st.transitions.put(id, tr);
        }
    }

    private static DisplayValue readDv(JsonNode n) {
        List<ErrorComponent> errs = new ArrayList<>();
        if (n.has("errors")) {
            for (JsonNode x : n.get("errors")) {
                errs.add(new ErrorComponent(x.get("code").asText(), x.get("label").asText(),
                        x.get("contribution").asDouble(), x.get("unit").asText(),
                        x.path("sourceRef").asText(null)));
            }
        }
        List<String> prov = new ArrayList<>();
        n.get("provenance").forEach(x -> prov.add(x.asText()));
        return new DisplayValue(n.get("valueId").asText(), n.get("name").asText(),
                n.get("value").asDouble(), n.get("unit").asText(),
                n.path("uncertainty").asDouble(0.0), errs, prov,
                n.path("derivedFrom").asText(null), n.path("ruleVersion").asText(null),
                n.path("computedEventSeq").asText(null), n.path("stale").asBoolean(false));
    }

    private static void readBaselines(CaseState st, ObjectNode p, Event e) {
        for (JsonNode n : p.get("baselines")) {
            List<String> diags = new ArrayList<>();
            if (n.has("diagnostics")) {
                n.get("diagnostics").forEach(x -> diags.add(x.get("code").asText()));
            }
            BaselineCandidate b = new BaselineCandidate(n.get("id").asText(),
                    n.get("transitionId").asText(), n.get("traceId").asText(),
                    n.get("segmentId").asText(), n.get("origin").asText(),
                    n.get("loIndex").asInt(), n.get("hiIndex").asInt(),
                    n.get("leftX1").asDouble(), n.get("leftX2").asDouble(),
                    n.get("rightX1").asDouble(), n.get("rightX2").asDouble(),
                    n.get("slope").asDouble(), n.get("intercept").asDouble(),
                    n.get("rmsResidual").asDouble(), n.get("quality").asDouble(),
                    n.get("adopted").asBoolean(), n.path("supersededBy").asText(null),
                    seq(e), n.path("ruleVersion").asText(null), diags);
            st.baselines.put(b.id(), b);
            st.transitions.computeIfPresent(b.transitionId(), (tid, tr) -> {
                List<String> ids = new ArrayList<>(tr.baselineCandidateIds());
                if (!ids.contains(b.id())) {
                    ids.add(b.id());
                }
                return new Transition(tr.id(), tr.traceId(), tr.segmentId(), tr.kindHint(),
                        tr.resolvedKind(), tr.segmentDirection(), tr.thermalSign(),
                        tr.loIndex(), tr.peakIndex(), tr.hiIndex(), tr.onset(), tr.peak(),
                        tr.endset(), tr.overlapWith(), ids, tr.adoptedBaselineId(),
                        tr.status(), tr.eventSeq(), tr.ruleVersion(), tr.diagnostics());
            });
        }
    }

    private static void adoptBaseline(CaseState st, ObjectNode p) {
        String adopted = p.get("baselineId").asText();
        String transitionId = p.get("transitionId").asText();
        for (Map.Entry<String, BaselineCandidate> en : st.baselines.entrySet()) {
            BaselineCandidate b = en.getValue();
            if (!b.transitionId().equals(transitionId)) {
                continue;
            }
            boolean isAdopted = b.id().equals(adopted);
            en.setValue(new BaselineCandidate(b.id(), b.transitionId(), b.traceId(), b.segmentId(),
                    b.origin(), b.loIndex(), b.hiIndex(), b.leftX1(), b.leftX2(),
                    b.rightX1(), b.rightX2(), b.slope(), b.intercept(), b.rmsResidual(),
                    b.quality(), isAdopted, isAdopted ? null : adopted,
                    b.eventSeq(), b.ruleVersion(), b.diagnostics()));
        }
        st.transitions.computeIfPresent(transitionId, (id, tr) ->
                new Transition(tr.id(), tr.traceId(), tr.segmentId(), tr.kindHint(),
                        tr.resolvedKind(), tr.segmentDirection(), tr.thermalSign(),
                        tr.loIndex(), tr.peakIndex(), tr.hiIndex(), tr.onset(), tr.peak(),
                        tr.endset(), tr.overlapWith(), tr.baselineCandidateIds(), adopted,
                        Transition.STATUS_ADOPTED, tr.eventSeq(), tr.ruleVersion(),
                        tr.diagnostics()));
    }

    private static void rejectTransition(CaseState st, ObjectNode p) {
        String transitionId = p.get("transitionId").asText();
        st.transitions.computeIfPresent(transitionId, (id, tr) ->
                new Transition(tr.id(), tr.traceId(), tr.segmentId(), tr.kindHint(),
                        tr.resolvedKind(), tr.segmentDirection(), tr.thermalSign(),
                        tr.loIndex(), tr.peakIndex(), tr.hiIndex(), tr.onset(), tr.peak(),
                        tr.endset(), tr.overlapWith(), tr.baselineCandidateIds(),
                        tr.adoptedBaselineId(), Transition.STATUS_REJECTED,
                        tr.eventSeq(), tr.ruleVersion(), tr.diagnostics()));
    }

    private static void readAnalysis(CaseState st, ObjectNode p, Event e, boolean stale, String reason) {
        JsonNode n = p.get("analysis");
        List<ErrorComponent> budget = new ArrayList<>();
        for (JsonNode x : n.get("errorBudget")) {
            budget.add(new ErrorComponent(x.get("code").asText(), x.get("label").asText(),
                    x.get("contribution").asDouble(), x.get("unit").asText(),
                    x.path("sourceRef").asText(null)));
        }
        EnthalpyResult a = new EnthalpyResult(n.get("id").asText(),
                n.get("transitionId").asText(), n.get("baselineId").asText(),
                n.get("traceId").asText(), n.get("segmentId").asText(),
                ThermalSign.valueOf(n.get("sign").asText()),
                readDv(n.get("enthalpy")),
                n.hasNonNull("specificEnthalpy") ? readDv(n.get("specificEnthalpy")) : null,
                n.path("massRecordId").asText(null),
                n.get("areaMilliwattCelsius").asDouble(), n.get("rateCelsiusPerMin").asDouble(),
                budget, n.path("ruleVersion").asText(null), seq(e),
                n.path("stale").asBoolean(stale), n.path("invalidatedReason").asText(reason));
        st.analyses.put(a.id(), a);
    }

    private static void invalidate(CaseState st, ObjectNode p) {
        String reason = p.get("reason").asText();
        String changedSource = p.get("changedSource").asText();
        for (JsonNode target : p.get("affectedObjects")) {
            String kind = target.get("kind").asText();
            String id = target.get("id").asText();
            if ("analysis".equals(kind)) {
                st.analyses.computeIfPresent(id, (aid, a) ->
                        new EnthalpyResult(a.id(), a.transitionId(), a.baselineId(), a.traceId(),
                                a.segmentId(), a.sign(), stale(a.enthalpy()),
                                a.specificEnthalpy() == null ? null : stale(a.specificEnthalpy()),
                                a.massRecordId(), a.areaMilliwattCelsius(), a.rateCelsiusPerMin(),
                                a.errorBudget(), a.ruleVersion(), a.eventSeq(), true, reason));
            } else if ("transition".equals(kind)) {
                st.transitions.computeIfPresent(id, (tid, tr) ->
                        new Transition(tr.id(), tr.traceId(), tr.segmentId(), tr.kindHint(),
                                tr.resolvedKind(), tr.segmentDirection(), tr.thermalSign(),
                                tr.loIndex(), tr.peakIndex(), tr.hiIndex(),
                                stale(tr.onset()), stale(tr.peak()), stale(tr.endset()),
                                tr.overlapWith(), tr.baselineCandidateIds(),
                                tr.adoptedBaselineId(), tr.status(), tr.eventSeq(),
                                tr.ruleVersion(), tr.diagnostics()));
            }
        }
        if (p.hasNonNull("nodeId") && st.nodes.containsKey(p.get("nodeId").asText())) {
            DependencyUpdater.mark(st, p.get("nodeId").asText(), reason);
        }
    }

    private static DisplayValue stale(DisplayValue v) {
        return new DisplayValue(v.valueId(), v.name(), v.value(), v.unit(), v.uncertainty(),
                v.errors(), v.provenance(), v.derivedFrom(), v.ruleVersion(),
                v.computedEventSeq(), true);
    }

    private static void adjustInterval(CaseState st, ObjectNode p, Event e) {
        String transitionId = p.get("transitionId").asText();
        int lo = p.get("loIndex").asInt();
        int hi = p.get("hiIndex").asInt();
        st.transitions.computeIfPresent(transitionId, (id, tr) ->
            new Transition(tr.id(), tr.traceId(), tr.segmentId(), tr.kindHint(),
                    tr.resolvedKind(), tr.segmentDirection(), tr.thermalSign(),
                    lo, tr.peakIndex(), hi, tr.onset(), tr.peak(), tr.endset(),
                    tr.overlapWith(), tr.baselineCandidateIds(), tr.adoptedBaselineId(),
                    tr.status(), tr.eventSeq(), tr.ruleVersion(), tr.diagnostics()));
    }
}
