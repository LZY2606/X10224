package dsc.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Onset/peak/endset and integrated enthalpy for one transition against one baseline candidate.
 *
 * <p>Every displayed value carries a stable id (onsetId/peakId/endsetId/enthalpyId) so the review
 * UI and re-imported archive refer to the same entity.
 */
public final class TransitionResult {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public final String resultId;
    public final String candidateId;
    public final String transitionType; // glass, crystallization, melting, generic
    public final String thermalSense;   // endotherm | exotherm (from measured sign, never file name)
    public final double onsetC;
    public final double endsetC;
    public final double peakC;
    public final double peakMw;
    public final double areaJ;
    public final double enthalpyJPerG;
    public final double deltaCpJPerGPerK; // glass transition only, otherwise 0
    public final double combinedUncertaintyJPerG;
    public final List<ErrorContribution> errorBudget;
    public final List<String> diagnostics;

    public TransitionResult(String resultId, String candidateId, String transitionType,
                            String thermalSense, double onsetC, double endsetC, double peakC,
                            double doublePeakMw, double areaJ, double enthalpyJPerG,
                            double deltaCpJPerGPerK, double combinedUncertaintyJPerG,
                            List<ErrorContribution> errorBudget, List<String> diagnostics) {
        this.resultId = resultId;
        this.candidateId = candidateId;
        this.transitionType = transitionType;
        this.thermalSense = thermalSense;
        this.onsetC = onsetC;
        this.endsetC = endsetC;
        this.peakC = peakC;
        this.peakMw = doublePeakMw;
        this.areaJ = areaJ;
        this.enthalpyJPerG = enthalpyJPerG;
        this.deltaCpJPerGPerK = deltaCpJPerGPerK;
        this.combinedUncertaintyJPerG = combinedUncertaintyJPerG;
        this.errorBudget = errorBudget;
        this.diagnostics = diagnostics;
    }

    public String onsetId() {
        return resultId + "#onset";
    }

    public String peakId() {
        return resultId + "#peak";
    }

    public String endsetId() {
        return resultId + "#endset";
    }

    public String enthalpyId() {
        return resultId + "#enthalpy";
    }

    public ObjectNode toJson() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("resultId", resultId);
        node.put("candidateId", candidateId);
        node.put("transitionType", transitionType);
        node.put("thermalSense", thermalSense);
        ObjectNode onset = node.putObject("onset");
        onset.put("id", onsetId());
        onset.put("valueC", onsetC);
        ObjectNode peak = node.putObject("peak");
        peak.put("id", peakId());
        peak.put("valueC", peakC);
        peak.put("valueMw", peakMw);
        ObjectNode endset = node.putObject("endset");
        endset.put("id", endsetId());
        endset.put("valueC", endsetC);
        ObjectNode enthalpy = node.putObject("enthalpy");
        enthalpy.put("id", enthalpyId());
        enthalpy.put("areaJ", areaJ);
        enthalpy.put("normalizedJPerG", enthalpyJPerG);
        enthalpy.put("combinedUncertaintyJPerG", combinedUncertaintyJPerG);
        if (deltaCpJPerGPerK != 0.0) {
            node.put("deltaCpJPerGPerK", deltaCpJPerGPerK);
        }
        ArrayNode budget = node.putArray("errorBudget");
        for (ErrorContribution contribution : errorBudget) {
            budget.add(contribution.toJson());
        }
        ArrayNode diag = node.putArray("diagnostics");
        diagnostics.forEach(diag::add);
        return node;
    }

    public static TransitionResult fromJson(JsonNode node) {
        List<ErrorContribution> budget = new ArrayList<>();
        node.path("errorBudget").forEach(n -> budget.add(ErrorContribution.fromJson(n)));
        List<String> diag = new ArrayList<>();
        node.path("diagnostics").forEach(n -> diag.add(n.asText()));
        return new TransitionResult(
                node.get("resultId").asText(), node.get("candidateId").asText(),
                node.get("transitionType").asText(), node.get("thermalSense").asText(),
                node.get("onset").get("valueC").asDouble(),
                node.get("endset").get("valueC").asDouble(),
                node.get("peak").get("valueC").asDouble(),
                node.get("peak").path("valueMw").asDouble(),
                node.get("enthalpy").get("areaJ").asDouble(),
                node.get("enthalpy").get("normalizedJPerG").asDouble(),
                node.path("deltaCpJPerGPerK").asDouble(0.0),
                node.get("enthalpy").get("combinedUncertaintyJPerG").asDouble(),
                budget, diag);
    }
}
