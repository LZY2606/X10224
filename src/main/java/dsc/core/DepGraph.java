package dsc.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Dependency graph over versioned nodes. A node key is "kind:id:version".
 *  edges: node -> set of input nodes it was derived from. */
public final class DepGraph {
    private final Map<String, Set<String>> inputs = new LinkedHashMap<>();

    public void addNode(String key, Set<String> deps) {
        inputs.put(key, new LinkedHashSet<>(deps));
    }

    public Set<String> inputsOf(String key) {
        return inputs.getOrDefault(key, Set.of());
    }

    /** All nodes that transitively depend on {@code changed}. */
    public Set<String> affectedBy(String changed) {
        // build reverse edges
        Map<String, Set<String>> rev = new LinkedHashMap<>();
        for (var e : inputs.entrySet())
            for (String in : e.getValue())
                rev.computeIfAbsent(in, k -> new LinkedHashSet<>()).add(e.getKey());
        Set<String> out = new LinkedHashSet<>();
        Deque<String> q = new ArrayDeque<>();
        q.add(changed);
        while (!q.isEmpty()) {
            String n = q.poll();
            for (String dep : rev.getOrDefault(n, Set.of()))
                if (out.add(dep)) q.add(dep);
        }
        return out;
    }
}
