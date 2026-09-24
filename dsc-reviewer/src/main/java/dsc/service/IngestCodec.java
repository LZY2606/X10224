package dsc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.domain.HeatFlowConvention;
import dsc.domain.Metadata;
import dsc.domain.Quantity;
import dsc.domain.RawData;
import dsc.domain.ScanDirection;
import dsc.domain.Segment;

/**
 * JSON shape for archiving one instrument fragment plus its separately stored
 * metadata records. Raw text and units live inside the payload so the
 * originating file can be reconstructed verbatim after import.
 */
public final class IngestCodec {

    private IngestCodec() {
    }

    public static ObjectNode rawPayload(String sampleId, Segment segment) {
        ObjectNode p = Payloads.JSON.createObjectNode();
        p.put("sampleId", sampleId);
        p.put("segmentId", segment.id());
        p.put("cycleIndex", segment.cycleIndex());
        p.put("label", segment.label());
        p.put("declaredDirection", segment.declaredDirection().name());
        p.put("observedDirection", segment.observedDirection().name());
        RawData r = segment.raw();
        p.put("sourceName", r.sourceName());
        p.put("temperatureUnit", r.temperatureUnit());
        p.put("heatFlowUnit", r.heatFlowUnit());
        p.put("convention", r.convention().name());
        p.put("originalText", r.originalText());
        var tArr = p.putArray("temperature");
        for (double v : r.temperature()) tArr.add(v);
        var hArr = p.putArray("heatFlow");
        for (double v : r.heatFlow()) hArr.add(v);
        var bad = p.putArray("badRows");
        for (RawData.RawRowIssue issue : r.badRows()) {
            var o = bad.addObject();
            o.put("rowIndex", issue.rowIndex());
            o.put("rawTemperature", issue.rawTemperature());
            o.put("rawHeatFlow", issue.rawHeatFlow());
            o.put("reason", issue.reason());
        }
        ObjectNode m = p.putObject("metadata");
        m.put("id", segment.metadata().id());
        setQuantity(m.putObject("mass"), segment.metadata().mass());
        setQuantity(m.putObject("scanRate"), segment.metadata().scanRate());
        m.put("atmosphere", segment.metadata().atmosphere());
        m.put("temperatureCalibration", segment.metadata().temperatureCalibration());
        m.put("note", segment.metadata().note());
        return p;
    }

    static void setQuantity(ObjectNode node, Quantity q) {
        node.put("value", q.value());
        node.put("unit", q.unit());
        node.put("raw", q.raw());
        node.put("conversionError", q.conversionError());
    }

    static Segment segment(JsonNode p) {
        RawData r = new RawData(
                p.path("sourceName").asText(),
                p.path("temperatureUnit").asText("K"),
                p.path("heatFlowUnit").asText("mW"),
                HeatFlowConvention.valueOf(p.path("convention").asText("UNKNOWN")),
                doubles(p.path("temperature")),
                doubles(p.path("heatFlow")),
                issues(p.path("badRows")),
                p.path("originalText").asText(""));
        JsonNode m = p.path("metadata");
        Metadata md = new Metadata(
                m.path("id").asText(),
                Payloads.quantity(m.path("mass")),
                Payloads.quantity(m.path("scanRate")),
                m.path("atmosphere").asText(""),
                m.path("temperatureCalibration").asText(""),
                m.path("note").asText(""));
        ScanDirection declared = ScanDirection.valueOf(
                p.path("declaredDirection").asText(p.path("observedDirection").asText("HEATING")));
        return new Segment(p.path("segmentId").asText(), p.path("cycleIndex").asInt(0),
                p.path("label").asText(""), declared, r, md);
    }

    private static java.util.List<RawData.RawRowIssue> issues(JsonNode arr) {
        java.util.List<RawData.RawRowIssue> out = new java.util.ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(n -> out.add(new RawData.RawRowIssue(
                    n.path("rowIndex").asInt(), n.path("rawTemperature").asText(),
                    n.path("rawHeatFlow").asText(), n.path("reason").asText())));
        }
        return out;
    }

    private static double[] doubles(JsonNode arr) {
        if (arr == null || !arr.isArray()) return new double[0];
        double[] out = new double[arr.size()];
        for (int i = 0; i < arr.size(); i++) out[i] = arr.get(i).asDouble();
        return out;
    }
}
