package org.materials.dsc.engine;

import org.materials.dsc.domain.CaseState;
import org.materials.dsc.domain.DependencyNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 依赖图传播：某直接来源改动后，只把传递依赖标记过期/重算，
 * 不相关分支保持有效；并能回答“为何仍有效 / 为何需要重算”。
 */
public final class DependencyUpdater {
    private DependencyUpdater() {
    }

    public static void putNode(CaseState st, DependencyNode node) {
        st.nodes.put(node.nodeId(), node);
        for (String dep : node.dependsOn()) {
            st.edges.computeIfAbsent(dep, k -> new ArrayList<>()).add(node.nodeId());
        }
    }

    /** 受 changedNode 影响的全部派生节点（传递闭包）。 */
    public static List<String> impacted(CaseState st, String changedNode) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> frontier = new ArrayList<>();
        frontier.add(changedNode);
        while (!frontier.isEmpty()) {
            String cur = frontier.remove(frontier.size() - 1);
            for (String next : st.edges.getOrDefault(cur, List.of())) {
                if (seen.add(next)) {
                    out.add(next);
                    frontier.add(next);
                }
            }
        }
        return out;
    }

    /** 说明某节点为何仍有效：它依赖的直接来源均未变化。 */
    public static Map<String, String> explain(CaseState st, String nodeId) {
        Map<String, String> explanation = new LinkedHashMap<>();
        DependencyNode node = st.nodes.get(nodeId);
        if (node == null) {
            explanation.put("status", "unknown");
            return explanation;
        }
        explanation.put("status", node.status());
        explanation.put("ruleVersion", String.valueOf(node.ruleVersion()));
        for (String dep : node.dependsOn()) {
            DependencyNode d = st.nodes.get(dep);
            explanation.put(dep, d == null ? "missing" : d.status()
                    + (d.staleReason() == null ? "" : ":" + d.staleReason()));
        }
        return explanation;
    }

    static void mark(CaseState st, String nodeId, String reason) {
        DependencyNode node = st.nodes.get(nodeId);
        if (node != null) {
            st.nodes.put(nodeId, new DependencyNode(node.nodeId(), node.kind(), node.label(),
                    node.version(), node.producedEventSeq(), node.ruleVersion(),
                    DependencyNode.STATUS_STALE, reason, node.dependsOn()));
        }
        for (String child : impacted(st, nodeId)) {
            DependencyNode c = st.nodes.get(child);
            if (c != null && DependencyNode.STATUS_OK.equals(c.status())) {
                st.nodes.put(child, new DependencyNode(c.nodeId(), c.kind(), c.label(),
                        c.version(), c.producedEventSeq(), c.ruleVersion(),
                        DependencyNode.STATUS_STALE, "downstream-of:" + nodeId, c.dependsOn()));
            }
        }
    }
}
