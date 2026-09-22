package dsc.derive;

import java.util.*;

/**
 * Dependency graph of derivations. Nodes are content-addressed derivation ids;
 * an edge A -> B means "A was computed from B". When a source version changes,
 * only descendants of that version are marked stale — the recompute request
 * can state precisely why an unaffected result is still valid.
 */
public final class DependencyGraph {

    private final Map<String, Set<String>> dependents = new HashMap<>();
    private final Map<String, Set<String>> dependencies = new HashMap<>();

    public synchronized void record(String node, Collection<String> deps) {
        dependencies.computeIfAbsent(node, k -> new HashSet<>()).addAll(deps);
        for (String d : deps) dependents.computeIfAbsent(d, k -> new HashSet<>()).add(node);
    }

    /** All derivation ids that (transitively) depend on {@code sourceId}. */
    public synchronized Set<String> downstream(String sourceId) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>(dependents.getOrDefault(sourceId, Set.of()));
        while (!stack.isEmpty()) {
            String n = stack.pop();
            if (seen.add(n)) stack.addAll(dependents.getOrDefault(n, Set.of()));
        }
        return seen;
    }

    public synchronized Set<String> depsOf(String node) {
        return Set.copyOf(dependencies.getOrDefault(node, Set.of()));
    }

    public synchronized boolean exists(String node) { return dependencies.containsKey(node); }

    /**
     * Explain validity in one sentence of data: list of dependency ids the
     * result was built from; the caller compares them with current versions.
     */
    public synchronized Map<String, Object> explain(String node) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("node", node);
        out.put("dependencies", new ArrayList<>(dependencies.getOrDefault(node, Set.of())));
        out.put("dependents", new ArrayList<>(dependents.getOrDefault(node, Set.of())));
        return out;
    }
}
