package dsc.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 依赖图 + 版本账本（内存可重放，持久化由 store 层镜像）。
 *
 *  - 每个 nodeId 保存全部版本；current 指针只在一次推导“完整成功”后切换。
 *  - 来源节点新增版本时沿 reverseDeps 传播，收集受影响派生节点（只更新受影响部分）。
 *  - explain(nodeId) 说明当前版本为何仍有效（每个依赖版本与当前指针一致）或为何需要重算。
 */
public final class DependencyGraph {

    public static final String RULE_VERSION = "dsc-rules-1.0.0";

    private final Map<String, List<NodeVersion>> versions = new LinkedHashMap<>();
    private final Map<String, Integer> current = new HashMap<>();
    /** nodeId("kind:id") -> 直接依赖它的派生节点 id 集合 */
    private final Map<String, Set<String>> reverseDeps = new HashMap<>();

    private long seq = 0;

    public NodeVersion addVersion(String nodeId, GraphNodeKind kind, List<NodeVersion.DepRef> deps,
                                  String payloadJson, String status, String diagnostic,
                                  String ruleVersion) {
        int v = versions.computeIfAbsent(nodeId, k -> new ArrayList<>()).size() + 1;
        NodeVersion nv = new NodeVersion(nodeId, kind, v, deps, payloadJson, status, diagnostic,
                ruleVersion == null ? RULE_VERSION : ruleVersion, ++seq);
        versions.get(nodeId).add(nv);
        for (NodeVersion.DepRef dep : deps) {
            reverseDeps.computeIfAbsent(key(dep.kind, dep.nodeId), k -> new LinkedHashSet<>()).add(nodeId);
        }
        // 只有完整成功（或显式完成的 ERROR 诊断结果）才切当前指针
        if (!"STALE".equals(status)) {
            current.put(nodeId, v);
        }
        return nv;
    }

    public NodeVersion currentVersion(String nodeId) {
        Integer v = current.get(nodeId);
        if (v == null) {
            return null;
        }
        return getVersion(nodeId, v);
    }

    public NodeVersion getVersion(String nodeId, int version) {
        List<NodeVersion> list = versions.get(nodeId);
        if (list == null || version < 1 || version > list.size()) {
            return null;
        }
        return list.get(version - 1);
    }

    public List<NodeVersion> allVersions(String nodeId) {
        return List.copyOf(versions.getOrDefault(nodeId, List.of()));
    }

    public Map<String, Integer> currentPointers() {
        return Map.copyOf(current);
    }

    public void restorePointers(Map<String, Integer> pointers) {
        current.clear();
        current.putAll(pointers);
    }

    public void markStale(String nodeId) {
        current.remove(nodeId);
    }

    public static String key(GraphNodeKind kind, String nodeId) {
        return kind.code + ":" + nodeId;
    }

    /**
     * 传播失效：从发生变化的来源节点出发，返回所有受影响的派生节点 id（拓扑序）。
     * 只标记受影响对象，其它节点保持当前版本，重算引擎据此只更新受影响部分。
     */
    public List<String> affectedBy(GraphNodeKind changedKind, String changedNodeId) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        String start = key(changedKind, changedNodeId);
        queue.add(start);
        while (!queue.isEmpty()) {
            String k = queue.poll();
            for (String dependent : reverseDeps.getOrDefault(k, Set.of())) {
                if (seen.add(dependent)) {
                    queue.add(dependent);
                }
            }
        }
        return new ArrayList<>(seen);
    }

    /**
     * 解释节点当前版本为何仍有效 / 为何需要重算。
     */
    public Explanation explain(String nodeId) {
        NodeVersion cur = currentVersion(nodeId);
        if (cur == null) {
            return new Explanation(nodeId, false, "no current version (never computed or marked stale)",
                    List.of());
        }
        List<String> depLines = new ArrayList<>();
        boolean stale = false;
        for (NodeVersion.DepRef dep : cur.dependencies) {
            NodeVersion depCur = currentVersion(key(dep.kind, dep.nodeId));
            int curDepV = depCur == null ? -1 : depCur.version;
            boolean ok = curDepV == dep.version;
            if (!ok) {
                stale = true;
            }
            depLines.add(dep.kind.code + " '" + dep.nodeId + "' pinned v" + dep.version
                    + (ok ? " == current v" + curDepV + " (valid)"
                    : " != current v" + Math.max(curDepV, 0) + " (recompute required)"));
        }
        String headline = stale
                ? "STALE: a dependency has a newer version; recompute propagates only through this subtree"
                : "VALID: every pinned dependency version matches the current pointer";
        return new Explanation(nodeId, !stale, headline, depLines);
    }

    public List<NodeVersion> everything() {
        List<NodeVersion> out = new ArrayList<>();
        for (List<NodeVersion> list : versions.values()) {
            out.addAll(list);
        }
        return out;
    }

    public static final class Explanation {
        public final String nodeId;
        public final boolean valid;
        public final String headline;
        public final List<String> dependencyLines;

        public Explanation(String nodeId, boolean valid, String headline, List<String> dependencyLines) {
            this.nodeId = nodeId;
            this.valid = valid;
            this.headline = headline;
            this.dependencyLines = List.copyOf(dependencyLines);
        }
    }
}
