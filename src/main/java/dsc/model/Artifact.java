package dsc.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * 派生产物的一个不可变版本。scopeKey 是稳定身份（跨版本不变），
 * id = scopeKey#vN。依赖以 scopeKey -> 版本号记录，供依赖图判定过期。
 * 状态：CANDIDATE（候选，含未采纳）/ ADOPTED（被采纳）/ SUPERSEDED（
 * 同身份新版本取代）/ EXPIRED（依赖变化后过期）/ REJECTED（被否定）。
 */
public record Artifact(
        String id,
        String scopeKey,
        int version,
        String kind,
        String status,
        Map<String, Integer> dependencies,
        JsonNode payload,
        String ruleVersion,
        long eventSeq,
        List<Diagnostic> diagnostics) {

    public static final String CANDIDATE = "CANDIDATE";
    public static final String ADOPTED = "ADOPTED";
    public static final String SUPERSEDED = "SUPERSEDED";
    public static final String EXPIRED = "EXPIRED";
    public static final String REJECTED = "REJECTED";

    public Artifact withStatus(String newStatus) {
        return new Artifact(id, scopeKey, version, kind, newStatus, dependencies,
                payload, ruleVersion, eventSeq, diagnostics);
    }
}
