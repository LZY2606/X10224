package dsc.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.core.Quantity;
import dsc.core.Unit;
import dsc.core.Units;
import dsc.derive.BaselineResult;
import dsc.derive.FeatureResult;
import dsc.derive.RuleVersions;
import dsc.model.*;
import dsc.storage.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * Codecs between domain objects and event payloads. Derived snapshots stored
 * verbatim in the event let time travel return the *historical* computed
 * values instead of recomputing with the newest rules.
 */
public final class JsonStorageCodecs {

    private JsonStorageCodecs() {}

    public static TraceImport readTrace(JsonNode p) {
        TraceImport ti = new TraceImport();
        ti.segmentId = p.path("segmentId").asText();
        ti.sourceFile = p.path("sourceFile").asText();
        ti.cycle = p.path("cycle").asInt(1);
        ti.conventionRaw = p.path("convention").asText("unknown");
        ti.declaredRate = p.path("declaredRate").asDouble(0);
        ti.rateUnit = Units.bySymbol(p.path("rateUnit").asText("°C/min"));
        for (JsonNode row : p.path("rows")) {
            ti.rows.add(new TraceImport.Row(row.path("t").asText(),
                    row.path("hf").asText(), row.path("time").asText(null)));
        }
        return ti;
    }

    public static ObjectNode writeTrace(TraceImport ti) {
        ObjectNode p = Json.MAPPER.createObjectNode();
        p.put("segmentId", ti.segmentId);
        p.put("sourceFile", ti.sourceFile);
        p.put("cycle", ti.cycle);
        p.put("convention", ti.conventionRaw);
        p.put("declaredRate", ti.declaredRate);
        p.put("rateUnit", ti.rateUnit.symbol());
        ArrayNode rows = p.putArray("rows");
        for (TraceImport.Row r : ti.rows) {
            ObjectNode o = rows.addObject();
            o.put("t", r.temperature);
            o.put("hf", r.heatFlow);
            if (r.time != null) o.put("time", r.time);
        }
        return p;
    }

    public static BaselineResult readBaseline(Event e) {
        JsonNode p = e.getPayload();
        NodeStatus st = NodeStatus.valueOf(p.path("status").asText("VALID"));
        List<Judgment> js = readJudgments(p.get("judgments"));
        return new BaselineResult(e.getEntityId(), p.path("regionId").asText(),
                p.path("mode").asText(), p.path("slope").asDouble(), p.path("intercept").asDouble(),
                p.path("leftAnchor").asDouble(), p.path("rightAnchor").asDouble(),
                p.path("rmse").asDouble(), st, p.path("ruleVersion").asText(RuleVersions.BASELINE_RULES),
                e.getDependencies() == null ? List.of() : e.getDependencies(), js, e.getSeq());
    }

    public static ObjectNode writeBaseline(BaselineResult b) {
        ObjectNode p = Json.MAPPER.createObjectNode();
        p.put("regionId", b.regionId());
        p.put("mode", b.mode());
        p.put("slope", b.slopeMilliwattPerCelsius());
        p.put("intercept", b.interceptMilliwatt());
        p.put("leftAnchor", b.leftAnchorCelsius());
        p.put("rightAnchor", b.rightAnchorCelsius());
        p.put("rmse", b.rmseMilliwatt());
        p.put("status", b.status().name());
        p.put("ruleVersion", b.ruleVersion());
        writeJudgments(p.putArray("judgments"), b.judgments());
        return p;
    }

    public static FeatureResult readFeature(Event e) {
        JsonNode p = e.getPayload();
        NodeStatus st = NodeStatus.valueOf(p.path("status").asText("VALID"));
        List<FeatureResult.ErrorContribution> errs = new ArrayList<>();
        for (JsonNode x : p.path("errors")) {
            errs.add(new FeatureResult.ErrorContribution(x.path("source").asText(),
                    x.path("magnitude").asDouble(), x.path("unit").asText(), x.path("basis").asText()));
        }
        return new FeatureResult(e.getEntityId(), p.path("regionId").asText(),
                p.path("baselineId").asText(),
                TransitionKind.parse(p.path("kind").asText("generic")),
                readQuantity(p.get("onset")), readQuantity(p.get("peak")), readQuantity(p.get("endset")),
                readQuantity(p.get("deltaH")), readQuantity(p.get("deltaHSpecific")),
                EnthalpySign.valueOf(p.path("sign").asText("AMBIGUOUS")), st,
                textOrNull(p, "massIdUsed"), textOrNull(p, "calibrationIdUsed"),
                p.path("ruleVersion").asText(RuleVersions.FEATURE_RULES), errs,
                readJudgments(p.get("judgments")),
                e.getDependencies() == null ? List.of() : e.getDependencies(), e.getSeq());
    }

    public static ObjectNode writeFeature(FeatureResult f) {
        ObjectNode p = Json.MAPPER.createObjectNode();
        p.put("regionId", f.regionId());
        p.put("baselineId", f.baselineId());
        p.put("kind", f.kind().code());
        writeQuantity(p.putObject("onset"), f.onset());
        writeQuantity(p.putObject("peak"), f.peak());
        writeQuantity(p.putObject("endset"), f.endset());
        writeQuantity(p.putObject("deltaH"), f.deltaHTotal());
        writeQuantity(p.putObject("deltaHSpecific"), f.deltaHSpecific());
        p.put("sign", f.sign().name());
        p.put("status", f.status().name());
        if (f.massIdUsed() != null) p.put("massIdUsed", f.massIdUsed());
        if (f.calibrationIdUsed() != null) p.put("calibrationIdUsed", f.calibrationIdUsed());
        p.put("ruleVersion", f.ruleVersion());
        ArrayNode errors = p.putArray("errors");
        for (FeatureResult.ErrorContribution ec : f.errorBudget()) {
            ObjectNode o = errors.addObject();
            o.put("source", ec.source());
            o.put("magnitude", ec.magnitude());
            o.put("unit", ec.unit());
            o.put("basis", ec.basis());
        }
        writeJudgments(p.putArray("judgments"), f.judgments());
        return p;
    }

    private static void writeQuantity(ObjectNode o, Quantity q) {
        if (q == null) { o.putNull("value"); o.putNull("unit"); return; }
        o.put("value", q.value());
        o.put("unit", q.unit().symbol());
    }

    private static Quantity readQuantity(JsonNode n) {
        if (n == null || n.isNull() || n.path("value").isNull()) return null;
        Unit u = Units.bySymbol(n.path("unit").asText("-"));
        return new Quantity(n.path("value").asDouble(), u);
    }

    static void writeJudgments(ArrayNode arr, List<Judgment> js) {
        for (Judgment j : js) {
            ObjectNode o = arr.addObject();
            o.put("code", j.code());
            o.put("severity", j.severity().name());
            o.put("subjectId", j.subjectId() == null ? "" : j.subjectId());
            o.put("message", j.message());
            o.put("ruleVersion", j.ruleVersion());
            o.set("evidence", Json.MAPPER.valueToTree(j.evidence() == null ? java.util.Map.of() : j.evidence()));
        }
    }

    static List<Judgment> readJudgments(JsonNode arr) {
        List<Judgment> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) return out;
        for (JsonNode j : arr) {
            out.add(new Judgment(j.path("code").asText(),
                    Judgment.Severity.valueOf(j.path("severity").asText("INFO")),
                    j.path("subjectId").asText(""), j.path("message").asText(""),
                    j.path("ruleVersion").asText(""),
                    Json.MAPPER.convertValue(j.path("evidence"),
                            Json.MAPPER.getTypeFactory().constructMapType(java.util.LinkedHashMap.class,
                                    String.class, Object.class))));
        }
        return out;
    }

    private static String textOrNull(JsonNode p, String key) {
        return p.has(key) && !p.path(key).isNull() ? p.path(key).asText() : null;
    }
}
