package dsc.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 依赖图：源（segment/mass/rate/atmosphere/calibration/tangent）→ 规范化曲线 →
 * 窗口 → 基线候选 → 发现（onset/peak/endset、ΔH、ΔCp）。
 * 重算只沿边传播；每个节点记录当前内容指纹，回答“为何仍有效/为何需重算”。
 */
public final class DepGraph {
    public static final class Node {
        public String id;
        public String kind;
        public String hash;
        public String status;          // current | stale | blocked
        public String reason;
        public final List<String> deps = new ArrayList<>();
        public final List<String> dependents = new ArrayList<>();
    }

    public final Map<String, Node> nodes = new LinkedHashMap<>();

    public Node node(String id, String kind, String hash) {
        Node n = nodes.get(id);
        if (n == null) {
            n = new Node();
            n.id = id;
            n.kind = kind;
            n.hash = hash;
            nodes.put(id, n);
        }
        return n;
    }

    public void edge(String from, String to) {
        node(from, null, null);
        node(to, null, null);
        if (!nodes.get(from).dependents.contains(to)) nodes.get(from).dependents.add(to);
        if (!nodes.get(to).deps.contains(from)) nodes.get(to).deps.add(from);
    }

    /** 从给定的“脏源”集合沿依赖边传播，标记 stale。 */
    public void propagate(java.util.Set<String> dirtySourceNodes) {
        java.util.ArrayDeque<String> q = new java.util.ArrayDeque<>(dirtySourceNodes);
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (!q.isEmpty()) {
            String id = q.poll();
            if (!seen.add(id)) continue;
            Node n = nodes.get(id);
            if (n == null) continue;
            for (String d : n.dependents) {
                Node dn = nodes.get(d);
                if (dn != null && !"stale".equals(dn.status)) {
                    dn.status = "stale";
                    dn.reason = "upstream source changed: " + id;
                }
                q.add(d);
            }
        }
    }
}
