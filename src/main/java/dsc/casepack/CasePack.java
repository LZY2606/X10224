package dsc.casepack;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** 离线案例包 manifest 的类型映射（Jackson 读 JSON 树，按需取值）。 */
public record CasePack(String caseId, String title, int schemaVersion, JsonNode raw,
                       List<SegmentDef> segments, List<IntervalDef> intervals,
                       List<SourceDef> sources, JsonNode beats, JsonNode edgeCases) {

    public record SegmentDef(String segmentId, String file, int cycle,
                             String programDirection, String declaredSign,
                             String rateValue, String rateUnit,
                             String gas, String flowRaw, String flowUnit) {
    }

    public record IntervalDef(String intervalId, String segmentId, double t0C, double t1C,
                              String transitionId, String hint) {
    }

    public record SourceDef(String id, String kind, String refId, int version,
                            String originalRep, JsonNode node) {
    }
}
