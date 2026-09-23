package org.matlab.dsc.domain;

import java.util.List;
import java.util.Map;

/**
 * An immutable derived artifact version. Recomputation always inserts a new
 * row; the current pointer only moves once the whole transaction succeeds.
 *
 * @param id        globally unique version id (nodeId#vN)
 * @param nodeId    stable logical identity shared by all versions
 * @param kind      artifact kind
 * @param version   1-based version number
 * @param ruleId    rule version that produced it
 * @param status    COMPUTED | REJECTED | FAILED
 * @param payload   structured artifact data (units always explicit)
 * @param diagnostics attached but non-fatal diagnostics
 */
public record NodeVersion(String id, String nodeId, NodeKind kind, int version,
                          String ruleId, NodeStatus status,
                          Map<String, Object> payload, List<Diagnostic> diagnostics) {

    public String displayRef() {
        return nodeId + "@v" + version;
    }
}
