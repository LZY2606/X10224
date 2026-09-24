package dsc.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dependency graph between source archives and derived artifacts.
 * A source change only invalidates its dependent subtree; every node can
 * explain why it is still valid or why it must be recomputed.
 */
public final class DerivationGraph {

    public static final class Node {
        public final String id;
        public final String kind;           // e.g. "instrument_output","sample_mass","normalized_curve","baseline","integration"
        public final Set<String> deps = new LinkedHashSet<>();
        public boolean stale;
        public String reason = "up to date";
        Node(String id, String kind) { this.id = id; this.kind = kind; }
    }

    public final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, Set<String>> dependents = new LinkedHashMap<>();

    public Node add(String id, String kind, List<String> dependencies) {
        Node n = nodes.computeIfAbsent(id, k -> new Node(k, kind));
        n.kind = kind;
        for (String d : dependencies) {
            n.deps.add(d);
            dependents.computeIfAbsent(d, k -> new LinkedHashSet<>()).add(id);
        }
        return n;
    }

    /** Mark everything downstream of `sourceId` stale; returns affected node ids in propagation order. */
    public List<String> invalidateFrom(String sourceId, String cause) {
        List<String> affected = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(sourceId);
        Set<String> seen = new LinkedHashSet<>();
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (String dep : dependents.getOrDefault(cur, Set.of())) {
                if (seen.add(dep)) {
                    Node n = nodes.get(dep);
                    if (n != null) {
                        n.stale = true;
                        n.reason = "stale: dependency '" + cur + "' changed (" + cause + ")";
                    }
                    affected.add(dep);
                    queue.add(dep);
                }
            }
        }
        return affected;
    }

    /** After a successful recompute, mark the node fresh again. */
    public void markFresh(String id) {
        Node n = nodes.get(id);
        if (n != null) { n.stale = false; n.reason = "up to date"; }
    }

    public String explain(String id) {
        Node n = nodes.get(id);
        if (n == null) return "unknown node";
        if (n.stale) return n.reason;
        return "valid: all " + n.deps.size() + " dependencies unchanged";
    }
}
