package dsc.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One independently versioned derivation output.
 *
 * <p>Each record snapshots every input dependency by id+version (segment, mass, scan rate,
 * atmosphere, temperature calibration, tangents) and the rule version. A record stays VALID while
 * its dependency snapshot matches the current pointers; a source change marks it STALE with the
 * reason, so the dependency graph can propagate updates only where needed and explain why an
 * unaffected result remains valid.
 */
public final class DerivationRecord {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String STATUS_VALID = "VALID";
    public static final String STATUS_STALE = "STALE";
    public static final String STATUS_AMBIGUOUS = "AMBIGUOUS";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_FAILED = "FAILED";

    public final String derivationId;
    public final String intervalId;
    public final String candidateId;
    public final String resultId;
    public final String ruleVersion;
    public final long producedAtSeq;
    public final Map<String, String> dependencies;
    public final BaselineCandidate baseline;
    public final TransitionResult result;
    public String status;
    public final List<String> staleReasons;

    public DerivationRecord(String derivationId, String intervalId, String candidateId,
                            String resultId, String ruleVersion, long producedAtSeq,
                            Map<String, String> dependencies, BaselineCandidate baseline,
                            TransitionResult result, String status, List<String> staleReasons) {
        this.derivationId = derivationId;
        this.intervalId = intervalId;
        this.candidateId = candidateId;
        this.resultId = resultId;
        this.ruleVersion = ruleVersion;
        this.producedAtSeq = producedAtSeq;
        this.dependencies = dependencies;
        this.result = result;
        this.status = status;
        this.staleReasons = new ArrayList<>(staleReasons);
    }

    public ObjectNode toJson() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("derivationId", derivationId);
        node.put("intervalId", intervalId);
        node.put("candidateId", candidateId);
        node.put("resultId", resultId);
        node.put("ruleVersion", ruleVersion);
        node.put("producedAtSeq", producedAtSeq);
        ObjectNode deps = node.putObject("dependencies");
        new TreeMap<>(dependencies).forEach(deps::put);
        node.set("baseline", baseline.toJson());
        node.set("result", result.toJson());
        node.put("status", status);
        ArrayNode reasons = node.putArray("staleReasons");
        staleReasons.forEach(reasons::add);
        return node;
    }

    public static DerivationRecord fromJson(JsonNode node) {
        Map<String, String> deps = new TreeMap<>();
        JsonNode depsNode = node.get("dependencies");
        depsNode.fieldNames().forEachRemaining(name -> deps.put(name, depsNode.get(name).asText()));
        List<String> reasons = new ArrayList<>();
        node.path("staleReasons").forEach(n -> reasons.add(n.asText()));
        return new DerivationRecord(
                node.get("derivationId").asText(), node.get("intervalId").asText(),
                node.get("candidateId").asText(), node.get("resultId").asText(),
                node.get("ruleVersion").asText(), node.get("producedAtSeq").asLong(),
                deps,
                BaselineCandidate.fromJson(node.get("baseline")),
                TransitionResult.fromJson(node.get("result")),
                node.path("status").asText(STATUS_VALID), reasons);
    }
}
