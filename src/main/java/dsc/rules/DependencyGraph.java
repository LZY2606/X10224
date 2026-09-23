package dsc.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rule DEPGRAPH-1: the derivation graph.
 *
 * <p>Nodes are sources, baselines, transitions and display values; edges go
 * from a derived node to the exact source/rule versions it was computed
 * from. When a direct source changes, {@link #propagate} returns exactly
 * the set of derived nodes that must be marked stale — propagation stops at
 * unaffected branches (e.g. a local calibration change only invalidates the
 * segment it belongs to).
 */
public final class DependencyGraph {

    private final Map<String, Set<String>> edges = new LinkedHashMap<>();
    private final Map<String, String> nodeKinds = new LinkedHashMap<>();

    public void addNode(String id, String kind) {
        edges.computeIfAbsent(id, k -> new LinkedHashSet<>());
        nodeKinds.putIfAbsent(id, kind);
    }

    /** derivedId depends on dependencyId. */
    public void addEdge(String derivedId, String dependencyId) {
        addNode(derivedId, nodeKinds.getOrDefault(derivedId, "derived"));
        addNode(dependencyId, nodeKinds.getOrDefault(dependencyId, "source"));
        edges.get(derivedId).add(dependencyId);
    }

    public Set<String> dependenciesOf(String id) {
        return Collections.unmodifiableSet(edges.getOrDefault(id, Set.of()));
    }

    public String kindOf(String id) {
        return nodeKinds.get(id);
    }

    /**
     * Propagate a change of {@code changedSourceIds} transitively through
     * dependents, returning the full affected set (excluding the changed
     * sources themselves). This is the authority for "why is this still
     * valid / why must it be recomputed".
     */
    public Set<String> propagate(Set<String> changedSourceIds) {
        Set<String> affected = new LinkedHashSet<>();
        Set<String> frontier = new HashSet<>(changedSourceIds);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<String, Set<String>> e : edges.entrySet()) {
                String node = e.getKey();
                if (affected.contains(node) || changedSourceIds.contains(node)) continue;
                for (String dep : e.getValue()) {
                    if (frontier.contains(dep)) {
                        affected.add(node);
                        frontier.add(node);
                        changed = true;
                        break;
                    }
                }
            }
        }
        return affected;
    }

    /** Human-readable explanation of whether {@code nodeId} is affected. */
    public List<String> explain(String nodeId, Set<String> changedSourceIds) {
        List<String> path = findPath(nodeId, changedSourceIds, new HashSet<>());
        if (path == null) {
            return List.of("UNCHANGED: no dependency path from changed sources to " + nodeId);
        }
        List<String> out = new ArrayList<>();
        out.add("STALE: change propagation path");
        for (int i = path.size() - 1; i > 0; i--) {
            out.add(path.get(i) + " -> " + path.get(i - 1));
        }
        return out;
    }

    private List<String> findPath(String node, Set<String> targets, Set<String> visited) {
        if (targets.contains(node)) return new ArrayList<>(List.of(node));
        if (!visited.add(node)) return null;
        for (String dep : edges.getOrDefault(node, Set.of())) {
            List<String> tail = findPath(dep, targets, visited);
            if (tail != null) {
                tail.add(node);
                return tail;
            }
        }
        return null;
    }

    public Map<String, Set<String>> snapshot() {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        edges.forEach((k, v) -> copy.put(k, new LinkedHashSet<>(v)));
        return copy;
    }
}
