package org.materials.dsc.engine;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.materials.dsc.domain.AtmosphereRecord;
import org.materials.dsc.domain.BaselineCandidate;
import org.materials.dsc.domain.Diagnostic;
import org.materials.dsc.domain.DisplayValue;
import org.materials.dsc.domain.EnthalpyResult;
import org.materials.dsc.domain.ErrorComponent;
import org.materials.dsc.domain.InstrumentOutput;
import org.materials.dsc.domain.MassRecord;
import org.materials.dsc.domain.Quantity;
import org.materials.dsc.domain.RateRecord;
import org.materials.dsc.domain.Segment;
import org.materials.dsc.domain.TangentSelection;
import org.materials.dsc.domain.TemperatureCalibration;
import org.materials.dsc.domain.Transition;
import org.materials.dsc.store.Json;

import java.util.List;

/** 事件 payload 序列化（领域对象 -> JSON 快照，随事件长期保留）。 */
final class Payloads {
    private Payloads() {
    }

    static ObjectNode quantity(Quantity q) {
        ObjectNode n = Json.obj();
        if (q == null) {
            return n;
        }
        if (q.convertible()) {
            n.put("value", q.value());
            n.put("unit", q.unit().symbol());
        }
        n.put("rawText", q.rawText());
        n.put("rawUnit", q.rawUnit());
        return n;
    }

    static ObjectNode mass(MassRecord m) {
        ObjectNode p = Json.obj();
        p.put("id", m.id());
        p.put("version", m.version());
        p.set("mass", quantity(m.mass()));
        p.put("note", m.note());
        return p;
    }

    static ObjectNode rate(RateRecord r) {
        ObjectNode p = Json.obj();
        p.put("id", r.id());
        p.set("rate", quantity(r.rate()));
        p.put("rawText", r.rawText());
        return p;
    }

    static ObjectNode atmosphere(AtmosphereRecord a) {
        ObjectNode p = Json.obj();
        p.put("id", a.id());
        p.put("gas", a.gas());
        if (a.flowRate() != null) {
            p.set("flowRate", quantity(a.flowRate()));
        }
        p.put("rawText", a.rawText());
        return p;
    }

    static ObjectNode calibration(TemperatureCalibration c) {
        ObjectNode p = Json.obj();
        p.put("id", c.id());
        p.put("slope", c.slope());
        p.put("offsetCelsius", c.offsetCelsius());
        p.put("standardRef", c.standardRef());
        p.put("note", c.note());
        ArrayNode refs = p.putArray("pointRefs");
        c.pointRefs().forEach(refs::add);
        return p;
    }

    static ObjectNode output(InstrumentOutput o) {
        ObjectNode p = Json.obj();
        p.put("outputId", o.id());
        ObjectNode n = Json.obj();
        n.put("id", o.id());
        n.put("fileName", o.fileName());
        n.put("instrument", o.instrument());
        n.put("declaredDirection", o.declaredDirection().name());
        n.put("signConvention", o.signConvention().name());
        n.put("declaredDirectionRaw", o.declaredDirectionRaw());
        n.put("signConventionRaw", o.signConventionRaw());
        n.put("tempUnit", o.tempUnit().symbol());
        n.put("hfUnit", o.hfUnit().symbol());
        n.put("nonUniformTemperature", o.nonUniformTemperature());
        n.put("medianStepCelsius", o.medianStepCelsius());
        n.put("importedAt", o.importedAt());
        n.put("sha256", o.sha256());
        ArrayNode rows = n.putArray("rows");
        o.rows().forEach(r -> {
            ObjectNode rn = rows.addObject();
            rn.put("index", r.index());
            rn.put("rawTemperature", r.rawTemperature());
            rn.put("tempUnit", r.tempUnit());
            rn.put("rawHeatFlow", r.rawHeatFlow());
            rn.put("hfUnit", r.hfUnit());
            if (r.normalizedT() != null) {
                rn.put("normalizedT", r.normalizedT());
            }
            if (r.normalizedHF() != null) {
                rn.put("normalizedHF", r.normalizedHF());
            }
            rn.put("nonConvertible", r.nonConvertible());
        });
        ArrayNode diags = n.putArray("diagnostics");
        o.diagnostics().forEach(d -> diagnostic(diags.addObject(), d));
        p.set("output", n);
        return p;
    }

    private static void diagnostic(ObjectNode n, Diagnostic d) {
        n.put("id", d.id());
        n.put("severity", d.severity());
        n.put("code", d.code());
        n.put("message", d.message());
        n.put("sourceRef", d.sourceRef());
    }

    static ObjectNode segments(List<Segment> segments) {
        ObjectNode p = Json.obj();
        ArrayNode arr = p.putArray("segments");
        for (Segment s : segments) {
            ObjectNode n = arr.addObject();
            n.put("id", s.id());
            n.put("traceId", s.traceId());
            n.put("direction", s.direction().name());
            n.put("startIndex", s.startIndex());
            n.put("endIndex", s.endIndex());
            n.put("tStartCelsius", s.tStartCelsius());
            n.put("tEndCelsius", s.tEndCelsius());
            n.set("rate", quantity(s.rate()));
            n.put("cycleLabel", s.cycleLabel());
        }
        return p;
    }

    static ObjectNode transitions(List<Transition> transitions) {
        ObjectNode p = Json.obj();
        ArrayNode arr = p.putArray("transitions");
        for (Transition t : transitions) {
            ObjectNode n = arr.addObject();
            n.put("id", t.id());
            n.put("traceId", t.traceId());
            n.put("segmentId", t.segmentId());
            n.put("kindHint", t.kindHint().name());
            n.put("resolvedKind", t.resolvedKind().name());
            n.put("segmentDirection", t.segmentDirection().name());
            n.put("thermalSign", t.thermalSign().name());
            n.put("loIndex", t.loIndex());
            n.put("peakIndex", t.peakIndex());
            n.put("hiIndex", t.hiIndex());
            n.set("onset", displayValue(t.onset()));
            n.set("peak", displayValue(t.peak()));
            n.set("endset", displayValue(t.endset()));
            ArrayNode ov = n.putArray("overlapWith");
            t.overlapWith().forEach(ov::add);
            ArrayNode bl = n.putArray("baselineCandidateIds");
            t.baselineCandidateIds().forEach(bl::add);
            if (t.adoptedBaselineId() != null) {
                n.put("adoptedBaselineId", t.adoptedBaselineId());
            }
            n.put("status", t.status());
            n.put("ruleVersion", t.ruleVersion());
            ArrayNode diags = n.putArray("diagnostics");
            t.diagnostics().forEach(diags::add);
        }
        return p;
    }

    static ObjectNode displayValue(DisplayValue v) {
        ObjectNode n = Json.obj();
        n.put("valueId", v.valueId());
        n.put("name", v.name());
        n.put("value", v.value());
        n.put("unit", v.unit());
        n.put("uncertainty", v.uncertainty());
        n.put("derivedFrom", v.derivedFrom());
        n.put("ruleVersion", v.ruleVersion());
        n.put("computedEventSeq", v.computedEventSeq());
        n.put("stale", v.stale());
        ArrayNode prov = n.putArray("provenance");
        v.provenance().forEach(prov::add);
        ArrayNode errs = n.putArray("errors");
        for (ErrorComponent e : v.errors()) {
            ObjectNode en = errs.addObject();
            en.put("code", e.code());
            en.put("label", e.label());
            en.put("contribution", e.contribution());
            en.put("unit", e.unit());
            en.put("sourceRef", e.sourceRef());
        }
        return n;
    }

    static ObjectNode baselines(List<BaselineCandidate> baselines) {
        ObjectNode p = Json.obj();
        ArrayNode arr = p.putArray("baselines");
        for (BaselineCandidate b : baselines) {
            ObjectNode n = arr.addObject();
            n.put("id", b.id());
            n.put("transitionId", b.transitionId());
            n.put("traceId", b.traceId());
            n.put("segmentId", b.segmentId());
            n.put("origin", b.origin());
            n.put("loIndex", b.loIndex());
            n.put("hiIndex", b.hiIndex());
            n.put("leftX1", b.leftX1());
            n.put("leftX2", b.leftX2());
            n.put("rightX1", b.rightX1());
            n.put("rightX2", b.rightX2());
            n.put("slope", b.slope());
            n.put("intercept", b.intercept());
            n.put("rmsResidual", b.rmsResidual());
            n.put("quality", b.quality());
            n.put("adopted", b.adopted());
            n.put("supersededBy", b.supersededBy());
            n.put("ruleVersion", b.ruleVersion());
            n.putArray("diagnostics");
        }
        return p;
    }

    static ObjectNode tangent(TangentSelection t) {
        ObjectNode p = Json.obj();
        p.put("id", t.id());
        p.put("traceId", t.traceId());
        p.put("segmentId", t.segmentId());
        p.put("leftX1", t.leftX1());
        p.put("leftX2", t.leftX2());
        p.put("rightX1", t.rightX1());
        p.put("rightX2", t.rightX2());
        p.put("label", t.label());
        p.put("note", t.note());
        return p;
    }

    static ObjectNode analysis(EnthalpyResult a) {
        ObjectNode n = Json.obj();
        n.put("id", a.id());
        n.put("transitionId", a.transitionId());
        n.put("baselineId", a.baselineId());
        n.put("traceId", a.traceId());
        n.put("segmentId", a.segmentId());
        n.put("sign", a.sign().name());
        n.set("enthalpy", displayValue(a.enthalpy()));
        if (a.specificEnthalpy() != null) {
            n.set("specificEnthalpy", displayValue(a.specificEnthalpy()));
        }
        n.put("massRecordId", a.massRecordId());
        n.put("areaMilliwattCelsius", a.areaMilliwattCelsius());
        n.put("rateCelsiusPerMin", a.rateCelsiusPerMin());
        n.put("ruleVersion", a.ruleVersion());
        n.put("stale", a.stale());
        n.put("invalidatedReason", a.invalidatedReason());
        ArrayNode budget = n.putArray("errorBudget");
        for (ErrorComponent e : a.errorBudget()) {
            ObjectNode en = budget.addObject();
            en.put("code", e.code());
            en.put("label", e.label());
            en.put("contribution", e.contribution());
            en.put("unit", e.unit());
            en.put("sourceRef", e.sourceRef());
        }
        ObjectNode p = Json.obj();
        p.set("analysis", n);
        return p;
    }
}
