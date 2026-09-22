package dsc.rules;

import dsc.domain.Derivation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dependency graph of derivations over the direct sources.
 *
 * Recomputation propagates only along the edges: when source X gets a new version, exactly
 * the downstream derivations are reported stale/recomputed; unrelated results are reported
 * still-valid, with a human-readable reason.
 */
public final class DependencyGraph {

    /** node id -> derivation info */
    public static final class Node {
        public final String id;
        public final String kind;
        /** direct input source refs (e.g. "sample_mass:mass-s1@v1") and/or other node ids. */
        public final List<String> inputs = new ArrayList<>();
        public String fingerprint;
        public Derivation.Status status = Derivation.Status.VALID;

        Node(String id, String kind) {
            this.id = id;
            this.kind = kind;
        }
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, List<String>> downstream = new LinkedHashMap<>();

    public Node node(String id, String kind) {
        return nodes.computeIfAbsent(id, k -> new Node(k, kind));
    }

    public void edge(String inputRef, String nodeId) {
        node(nodeId, "derived").inputs.add(inputRef);
        downstream.computeIfAbsent(inputRef, k -> new ArrayList<>()).add(nodeId);
    }

    public Map<String, Node> nodes() {
        return nodes;
    }

    /**
     * Propagate a changed input reference.
     *
     * @return ordered list of node ids that must be recomputed; every other valid node is
     *         untouched.
     */
    public List<String> impacted(String changedInputRef) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> q = new ArrayDeque<>();
        q.add(changedInputRef);
        while (!q.isEmpty()) {
            String cur = q.poll();
            for (String n : downstream.getOrDefault(cur, List.of())) {
                if (seen.add(n)) {
                    q.add(n);
                }
            }
        }
        return new ArrayList<>(seen);
    }

    /** Explain why a node is still valid after a change. */
    public String validityReason(String nodeId, String changedInputRef) {
        List<String> chain = impacted(changedInputRef);
        if (chain.contains(nodeId)) {
            return "stale: 依赖链包含已变更输入 " + changedInputRef;
        }
        return "still-valid: " + nodeId + " 不依赖 " + changedInputRef
                + "（" + reachableInputs(nodeId) + "）";
    }

    private Set<String> reachableInputs(String nodeId) {
        Set<String> acc = new LinkedHashSet<>();
        Deque<String> q = new ArrayDeque<>(node(nodeId, "derived").inputs);
        while (!q.isEmpty()) {
            String cur = q.poll();
            Node n = nodes.get(cur);
            if (n == null) {
                acc.add(cur);
            } else {
                q.addAll(n.inputs);
            }
        }
        return acc;
    }
}
