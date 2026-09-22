package dsc.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * One legally admissible baseline for an interval.
 *
 * <p>When several baselines are reasonable, all are retained with their differences instead of
 * collapsing to a single answer. Values are in the endotherm-positive coordinate base (mW).
 */
public record BaselineCandidate(String candidateId, String method,
                                double interceptMw, double slopeMwPerC,
                                double leftAnchorC, double leftAnchorMw,
                                double rightAnchorC, double rightAnchorMw,
                                double rmsResidualMw, double fitUncertaintyMw,
                                String rationale, boolean usesUserTangents) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public double valueAt(double temperatureC) {
        return interceptMw + slopeMwPerC * temperatureC;
    }

    public ObjectNode toJson() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("candidateId", candidateId);
        node.put("method", method);
        node.put("interceptMw", interceptMw);
        node.put("slopeMwPerC", slopeMwPerC);
        node.put("leftAnchorC", leftAnchorC);
        node.put("leftAnchorMw", leftAnchorMw);
        node.put("rightAnchorC", rightAnchorC);
        node.put("rightAnchorMw", rightAnchorMw);
        node.put("rmsResidualMw", rmsResidualMw);
        node.put("fitUncertaintyMw", fitUncertaintyMw);
        node.put("rationale", rationale);
        node.put("usesUserTangents", usesUserTangents);
        return node;
    }

    public static BaselineCandidate fromJson(JsonNode node) {
        return new BaselineCandidate(
                node.get("candidateId").asText(), node.get("method").asText(),
                node.get("interceptMw").asDouble(), node.get("slopeMwPerC").asDouble(),
                node.get("leftAnchorC").asDouble(), node.get("leftAnchorMw").asDouble(),
                node.get("rightAnchorC").asDouble(), node.get("rightAnchorMw").asDouble(),
                node.get("rmsResidualMw").asDouble(), node.get("fitUncertaintyMw").asDouble(),
                node.path("rationale").asText(""), node.path("usesUserTangents").asBoolean(false));
    }
}
