package dsc.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** One component of the enthalpy error budget, with its own stable identity. */
public record ErrorContribution(String contributionId, String source, String label,
                                double magnitude, String unit, String evidenceEventId) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ObjectNode toJson() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("contributionId", contributionId);
        node.put("source", source);
        node.put("label", label);
        node.put("magnitude", magnitude);
        node.put("unit", unit);
        if (evidenceEventId != null) {
            node.put("evidenceEventId", evidenceEventId);
        }
        return node;
    }

    public static ErrorContribution fromJson(JsonNode node) {
        return new ErrorContribution(node.get("contributionId").asText(),
                node.get("source").asText(), node.get("label").asText(),
                node.get("magnitude").asDouble(), node.get("unit").asText(),
                node.hasNonNull("evidenceEventId") ? node.get("evidenceEventId").asText() : null);
    }
}
