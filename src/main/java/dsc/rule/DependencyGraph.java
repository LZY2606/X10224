package dsc.rule;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 依赖图：直接来源 -> 归一化段 -> 转折 -> 基线候选 -> 焓。
 * 只传播到受影响节点；并能回答“为什么仍有效/为什么需要重算”。
 */
public final class DependencyGraph {

    public record Node(String scopeKey, String label, String kind) {
    }

    public record Validity(boolean valid, String reason, List<String> chain) {
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, Set<String>> edges = new LinkedHashMap<>();
    private final Map<String, Set<String>> reverse = new LinkedHashMap<>();

    public void addNode(Node node) {
        nodes.putIfAbsent(node.scopeKey(), node);
        edges.putIfAbsent(node.scopeKey(), new LinkedHashSet<>());
        reverse.putIfAbsent(node.scopeKey(), new LinkedHashSet<>());
    }

    public void dependsOn(String derived, String dependency) {
        addNode(new Node(dependency, dependency, "unknown"));
        addNode(new Node(derived, derived, "unknown"));
        edges.get(derived).add(dependency);
        reverse.get(dependency).add(derived);
    }

    /** 从发生变化的源节点传播，返回全部需要重算的派生（含传递闭包，按拓扑顺序）。 */
    public List<String> affectedBy(String changedSource) {
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        walk(changedSource, visited);
        visited.remove(changedSource);
        return List.copyOf(visited);
    }

    private void walk(String node, Set<String> acc) {
        for (String child : reverse.getOrDefault(node, Set.of())) {
            if (acc.add(child)) {
                walk(child, acc);
            }
        }
    }

    /**
     * 当前各依赖的版本号 vs 派生产生时记录的版本号；全部匹配才有效。
     */
    public Validity explain(String scopeKey, Map<String, Integer> recordedDeps,
                            Map<String, Integer> currentVersions) {
        for (Map.Entry<String, Integer> e : recordedDeps.entrySet()) {
            int cur = currentVersions.getOrDefault(e.getKey(), -1);
            if (cur != e.getValue()) {
                List<String> chain = pathTo(scopeKey, e.getKey());
                return new Validity(false,
                        "依赖 " + e.getKey() + " 已从 v" + e.getValue() + " 变为 v" + cur
                                + "，该结果需要重算", chain);
            }
        }
        List<String> chain = new java.util.ArrayList<>();
        for (String dep : recordedDeps.keySet()) {
            chain.add(dep + "@v" + recordedDeps.get(dep));
        }
        return new Validity(true, "全部依赖版本与产生时一致，结果仍有效", chain);
    }

    private List<String> pathTo(String from, String targetDep) {
        List<String> path = new java.util.ArrayList<>();
        if (findPath(from, targetDep, path)) {
            return path;
        }
        return List.of();
    }

    private boolean findPath(String cur, String target, List<String> path) {
        path.add(cur);
        if (cur.equals(target)) {
            return true;
        }
        for (String dep : edges.getOrDefault(cur, Set.of())) {
            if (findPath(dep, target, path)) {
                return true;
            }
        }
        path.remove(path.size() - 1);
        return false;
    }

    public Set<String> directDependencies(String scopeKey) {
        return Set.copyOf(edges.getOrDefault(scopeKey, Set.of()));
    }
}
