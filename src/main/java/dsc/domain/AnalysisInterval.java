package dsc.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A reviewer-drawn temperature interval on a single segment.
 *
 * <p>Intervals never span segment boundaries: heating and cooling are never mixed in one analysis.
 */
public record AnalysisInterval(String intervalId, String segmentId, double fromC, double toC) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ObjectNode toJson() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("intervalId", intervalId);
        node.put("segmentId", segmentId);
        node.put("fromC", fromC);
        node.put("toC", toC);
        return node;
    }

    public static AnalysisInterval fromJson(JsonNode node) {
        return new AnalysisInterval(node.get("intervalId").asText(),
                node.get("segmentId").asText(),
                node.get("fromC").asDouble(), node.get("toC").asDouble());
    }

    public double lowC() {
        return Math.min(fromC, toC);
    }

    public double highC() {
        return Math.max(fromC, toC);
    }
}
