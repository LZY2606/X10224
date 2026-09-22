package dsc.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.core.Quantity;
import dsc.derive.BaselineResult;
import dsc.derive.FeatureResult;
import dsc.events.Event;
import dsc.events.HistoryState;
import dsc.events.Judgment;
import dsc.model.*;
import dsc.storage.Json;

import java.util.List;

/**
 * Assembles read-model JSON for the review UI. Every displayed value carries a
 * stable identity and a source chain: points are keyed by segment id + index,
 * derivations by their content-addressed id, judgments by code+subject.
 */
final class ApiViews {

    static final ObjectMapper M = Json.MAPPER;

    private ApiViews() {}

    static ObjectNode history(HistoryState s, long viewedAtSeq) {
        ObjectNode root = M.createObjectNode();
        root.put("historyId", s.historyId);
        root.put("name", s.name);
        root.put("headSeq", s.headSeq);
        root.put("viewedAtSeq", viewedAtSeq);
        root.set("mass", mass(s));
        root.set("calibration", calibration(s));
        root.set("atmospheres", atmospheres(s));
        root.set("segments", segments(s));
        root.set("regions", regions(s));
        root.set("derivations", derivations(s));
        root.set("accepted", accepted(s));
        root.set("events", events(s));
        root.set("judgments", allJudgments(s));
        return root;
    }

    private static ObjectNode mass(HistoryState s) {
        ObjectNode out = M.createObjectNode();
        ArrayNode arr = out.putArray("versions");
        for (MassVersion mv : s.masses.values()) {
            ObjectNode o = arr.addObject();
            o.put("id", mv.massId());
            o.put("version", mv.version());
            o.put("raw", mv.rawMass().raw());
            o.put("unit", mv.rawMass().declaredUnit().symbol());
            o.put("grams", mv.grams().value());
            o.put("reason", mv.reason());
            o.put("current", mv.massId().equals(s.currentMassId));
            o.put("recordedAt", mv.recordedAt().toString());
        }
        return out;
    }

    private static ObjectNode calibration(HistoryState s) {
        ObjectNode out = M.createObjectNode();
        ArrayNode arr = out.putArray("versions");
        for (Calibration c : s.calibrations.values()) {
            ObjectNode o = arr.addObject();
            o.put("id", c.calibrationId());
            o.put("standard", c.standard());
            q(o.putObject("offsetCelsius"), c.offsetCelsius());
            o.put("calibratedAt", c.calibratedAt().toString());
            o.put("note", c.note());
            o.put("current", c.calibrationId().equals(s.currentCalibrationId));
        }
        return out;
    }

    private static ArrayNode atmospheres(HistoryState s) {
        ArrayNode arr = M.createArrayNode();
        for (Atmosphere a : s.atmospheres.values()) {
            ObjectNode o = arr.addObject();
            o.put("id", a.atmosphereId());
            o.put("gas", a.gas());
            o.put("flowRaw", a.flowRate().raw());
            o.put("flowUnit", a.flowRate().declaredUnit().symbol());
            o.put("note", a.note());
        }
        return arr;
    }

    private static ArrayNode segments(HistoryState s) {
        ArrayNode arr = M.createArrayNode();
        for (String sid : s.traceOrder) {
            HistoryState.TraceRef tr = s.traces.get(sid);
            ScanSegment seg = tr.segment;
            ObjectNode o = arr.addObject();
            o.put("id", tr.segmentId);
            o.put("sourceFile", tr.sourceFile);
            o.put("cycle", tr.cycle);
            o.put("direction", seg.direction().name());
            o.put("directionLabel", seg.direction().label());
            o.put("convention", tr.convention.code());
            o.put("conventionDescription", tr.convention.description());
            o.put("nonUniform", seg.nonUniformTemperatures());
            o.put("supersededBy", tr.supersededBy);
            o.put("rawDescriptor", tr.rawDescriptor);
            o.set("parseDiagnostics", M.valueToTree(tr.parseDiagnostics));
            ArrayNode pts = o.putArray("points");
            for (SamplePoint p : seg.points()) {
                ObjectNode po = pts.addObject();
                po.put("id", tr.segmentId + ":p" + p.index());
                po.put("index", p.index());
                po.put("rawT", p.temperature().raw());
                po.put("rawHf", p.heatFlow().raw());
                if (p.plottable()) {
                    po.put("t", p.tCelsius().value());
                    po.put("hf", p.hfMilliwatts().value());
                } else {
                    po.putNull("t");
                    po.putNull("hf");
                }
                if (!p.diagnostics().isEmpty()) po.set("diagnostics", M.valueToTree(p.diagnostics()));
            }
        }
        return arr;
    }

    private static ArrayNode regions(HistoryState s) {
        ArrayNode arr = M.createArrayNode();
        for (HistoryState.RegionRef rr : s.regions.values()) {
            Region r = rr.region;
            ObjectNode o = arr.addObject();
            o.put("id", r.regionId());
            o.put("segmentId", r.segmentId());
            o.put("kind", r.kind().code());
            o.put("tLo", r.tLoCelsius());
            o.put("tHi", r.tHiCelsius());
            o.put("label", r.label());
            o.put("createdBy", r.createdBy());
            o.put("definedSeq", rr.definedSeq);
        }
        return arr;
    }

    private static ObjectNode derivations(HistoryState s) {
        ObjectNode out = M.createObjectNode();
        ArrayNode baselines = out.putArray("baselines");
        for (BaselineResult b : s.baselines.values()) {
            ObjectNode o = baselines.addObject();
            o.put("id", b.id());
            o.put("regionId", b.regionId());
            o.put("mode", b.mode());
            o.put("slope", finite(b.slopeMilliwattPerCelsius()));
            o.put("intercept", finite(b.interceptMilliwatt()));
            o.put("leftAnchor", b.leftAnchorCelsius());
            o.put("rightAnchor", b.rightAnchorCelsius());
            o.put("rmse", finite(b.rmseMilliwatt()));
            o.put("status", b.status().name());
            o.put("ruleVersion", b.ruleVersion());
            o.set("dependencies", M.valueToTree(b.dependencyIds()));
            o.set("judgments", judgments(b.judgments()));
            o.put("rejected", s.rejected.containsKey(b.id()));
        }
        ArrayNode feats = out.putArray("features");
        for (FeatureResult f : s.features.values()) {
            ObjectNode o = feats.addObject();
            o.put("id", f.id());
            o.put("regionId", f.regionId());
            o.put("baselineId", f.baselineId());
            o.put("kind", f.kind().code());
            q(o.putObject("onset"), f.onset());
            q(o.putObject("peak"), f.peak());
            q(o.putObject("endset"), f.endset());
            q(o.putObject("deltaH"), f.deltaHTotal());
            q(o.putObject("deltaHSpecific"), f.deltaHSpecific());
            o.put("sign", f.sign().name());
            o.put("status", f.status().name());
            o.put("massIdUsed", f.massIdUsed());
            o.put("calibrationIdUsed", f.calibrationIdUsed());
            o.put("ruleVersion", f.ruleVersion());
            o.set("dependencies", M.valueToTree(f.dependencyIds()));
            ArrayNode eb = o.putArray("errorBudget");
            for (FeatureResult.ErrorContribution ec : f.errorBudget()) {
                ObjectNode eo = eb.addObject();
                eo.put("source", ec.source());
                eo.put("magnitude", ec.magnitude());
                eo.put("unit", ec.unit());
                eo.put("basis", ec.basis());
            }
            o.set("judgments", judgments(f.judgments()));
            o.put("rejected", s.rejected.containsKey(f.id()));
        }
        return out;
    }

    private static ArrayNode accepted(HistoryState s) {
        ArrayNode arr = M.createArrayNode();
        s.acceptedBaseline.forEach((regionId, baselineId) -> {
            ObjectNode o = arr.addObject();
            o.put("regionId", regionId);
            o.put("baselineId", baselineId);
        });
        return arr;
    }

    private static ArrayNode events(HistoryState s) {
        ArrayNode arr = M.createArrayNode();
        for (Event e : s.events) {
            ObjectNode o = arr.addObject();
            o.put("seq", e.getSeq());
            o.put("type", e.getType().name());
            o.put("entityId", e.getEntityId());
            o.put("actor", e.getActor());
            o.put("comment", e.getComment());
            o.put("at", e.getAt().toString());
            o.put("supersedes", e.getSupersedes());
            o.set("dependencies", M.valueToTree(e.getDependencies()));
            o.set("judgments", judgments(e.getJudgments()));
        }
        return arr;
    }

    private static ArrayNode allJudgments(HistoryState s) {
        ArrayNode arr = M.createArrayNode();
        for (Event e : s.events) arr.addAll(judgments(e.getJudgments()));
        return arr;
    }

    private static ArrayNode judgments(List<Judgment> js) {
        ArrayNode arr = M.createArrayNode();
        if (js == null) return arr;
        for (Judgment j : js) {
            ObjectNode o = arr.addObject();
            o.put("code", j.code());
            o.put("severity", j.severity().name());
            o.put("subjectId", j.subjectId());
            o.put("message", j.message());
            o.put("ruleVersion", j.ruleVersion());
            o.set("evidence", M.valueToTree(j.evidence()));
        }
        return arr;
    }

    private static void q(ObjectNode o, Quantity q) {
        if (q == null) { o.putNull("value"); o.putNull("unit"); return; }
        o.put("value", q.value());
        o.put("unit", q.unit().symbol());
    }

    private static Double finite(double v) { return Double.isFinite(v) ? v : null; }
}
