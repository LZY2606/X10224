package dsc.derive;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 依赖图。节点是"稳定身份 -> 内容哈希版本"。
 * 直接来源（仪器输出/质量/速率/气氛/标定/切线）各自独立；
 * 派生节点（段视图、基线候选、转折积分、归一化量）声明其依赖。
 *
 * 质量修订只会使依赖该质量的归一化派生过期；
 * 仪器输出改动只影响绑定它的段；
 * 支持"为何仍有效 / 为何需要重算"的解释。
 */
public final class DepGraph {

    public static final class Node {
        public final String id;
        public final String kind;
        public final String hash;
        public final List<String> deps;

        Node(String id, String kind, String hash, List<String> deps) {
            this.id = id;
            this.kind = kind;
            this.hash = hash;
            this.deps = List.copyOf(deps);
        }
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();

    public void put(String id, String kind, String hash, List<String> deps) {
        nodes.put(id, new Node(id, kind, hash, List.copyOf(deps)));
    }

    public Node get(String id) {
        return nodes.get(id);
    }

    public boolean contains(String id) {
        return nodes.containsKey(id);
    }

    /** 返回从 changedIds 出发沿依赖边可达的全部节点（含起点），即受影响集合。 */
    public List<String> affected(Iterable<String> changedIds) {
        Map<String, List<String>> dependents = new HashMap<>();
        for (Node n : nodes.values()) {
            for (String d : n.deps) {
                dependents.computeIfAbsent(d, k -> new ArrayList<>()).add(n.id);
            }
        }
        List<String> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        Deque<String> q = new ArrayDeque<>();
        for (String c : changedIds) {
            if (seen.add(c)) {
                q.add(c);
            }
        }
        while (!q.isEmpty()) {
            String x = q.poll();
            out.add(x);
            for (String child : dependents.getOrDefault(x, List.of())) {
                if (seen.add(child)) {
                    q.add(child);
                }
            }
        }
        return out;
    }

    /** 解释某节点为何仍有效：所有依赖哈希都未变。 */
    public List<String> whyValid(String nodeId, Map<String, String> oldHashes) {
        List<String> reasons = new ArrayList<>();
        Node n = nodes.get(nodeId);
        if (n == null) {
            reasons.add("节点不存在");
            return reasons;
        }
        for (String d : n.deps) {
            Node dep = nodes.get(d);
            String old = oldHashes.get(d);
            if (dep != null && dep.hash.equals(old)) {
                reasons.add("依赖 " + d + " 内容未变 (" + dep.hash.substring(0, Math.min(7, dep.hash.length())) + ")");
            } else {
                reasons.add("依赖 " + d + " 缺失或已变化，需要重算");
            }
        }
        if (n.deps.isEmpty()) {
            reasons.add("直接来源，无派生依赖");
        }
        return reasons;
    }

    /** 依赖链路径（用于来源链回溯）。 */
    public List<List<String>> pathsFromSource(String targetId, String sourceId) {
        List<List<String>> results = new ArrayList<>();
        List<String> path = new ArrayList<>();
        dfs(targetId, sourceId, path, results, new java.util.HashSet<>());
        return results;
    }

    private void dfs(String cur, String target, List<String> path,
                     List<List<String>> results, java.util.Set<String> visiting) {
        if (!visiting.add(cur)) {
            return;
        }
        path.add(cur);
        if (cur.equals(target)) {
            results.add(new ArrayList<>(path));
        } else {
            Node n = nodes.get(cur);
            if (n != null) {
                for (String d : n.deps) {
                    dfs(d, target, path, results, visiting);
                }
            }
        }
        path.remove(path.size() - 1);
        visiting.remove(cur);
    }

    public Map<String, Node> all() {
        return Map.copyOf(nodes);
    }

    public Map<String, String> hashSnapshot() {
        Map<String, String> m = new LinkedHashMap<>();
        nodes.forEach((k, v) -> m.put(k, v.hash));
        return m;
    }
}
