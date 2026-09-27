package dsc.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dependency graph between versioned source entities and derived entities.
 * When a direct source is revised, propagation follows the reverse edges and
 * reports every affected derivation so only those parts are recomputed and the
 * rest can be shown as still valid with an explanation.
 */
public final class DependencyGraph {

    /** Status of a derived entity against current source versions. */
    public enum Status { VALID, EXPIRED, SUPERSEDED }

    private final Map<String, List<String>> edges = new LinkedHashMap<>();
    private final Map<String, String> label = new HashMap<>();
    private final Map<String, Map<String, Integer>> consumes = new HashMap<>();
    private final Map<String, Map<String, Integer>> currentVersions = new HashMap<>();

    public void registerNode(String node, String display) {
        edges.putIfAbsent(node, new ArrayList<>());
        label.put(node, display);
    }

    public void edge(String from, String to) {
        edges.computeIfAbsent(from, k -> new ArrayList<>());
        if (!edges.get(from).contains(to)) {
            edges.get(from).add(to);
        }
        edges.computeIfAbsent(to, k -> new ArrayList<>());
    }

    /** Derived node consumes {@code source} at {@code version}. */
    public void consumes(String derived, String source, int version) {
        consumes.computeIfAbsent(derived, k -> new LinkedHashMap<>()).put(source, version);
        registerNode(source, source);
        registerNode(derived, derived);
        edge(source, derived);
    }

    public void setCurrentVersion(String source, int version) {
        currentVersions.computeIfAbsent(source, k -> new HashMap<>());
        currentVersions.get(source).put("v", version);
    }

    public int currentVersion(String source) {
        Map<String, Integer> m = currentVersions.get(source);
        return m == null ? 0 : m.getOrDefault("v", 0);
    }

    /** Reverse-BFS from a changed source: every derivation that must be recomputed. */
    public List<String> affectedBy(String source) {
        List<String> out = new ArrayList<>();
        Deque<String> q = new ArrayDeque<>();
        q.add(source);
        java.util.Set<String> seen = new java.util.HashSet<>();
        seen.add(source);
        while (!q.isEmpty()) {
            String node = q.poll();
            for (String to : edges.getOrDefault(node, List.of())) {
                if (seen.add(to)) {
                    out.add(to);
                    q.add(to);
                }
            }
        }
        return out;
    }

    public Status statusOf(String derived) {
        Map<String, Integer> need = consumes.get(derived);
        if (need == null) {
            return Status.VALID;
        }
        for (Map.Entry<String, Integer> e : need.entrySet()) {
            if (e.getValue() < currentVersion(e.getKey())) {
                return Status.EXPIRED;
            }
        }
        return Status.VALID;
    }

    /** Human-readable reason a derivation is valid or needs recomputation. */
    public String explain(String derived) {
        Map<String, Integer> need = consumes.getOrDefault(derived, Map.of());
        List<String> stale = new ArrayList<>();
        List<String> fresh = new ArrayList<>();
        for (Map.Entry<String, Integer> e : need.entrySet()) {
            int cur = currentVersion(e.getKey());
            if (e.getValue() < cur) {
                stale.add(label.getOrDefault(e.getKey(), e.getKey()) + " v" + e.getValue() + "->v" + cur);
            } else {
                fresh.add(label.getOrDefault(e.getKey(), e.getKey()) + " v" + cur);
            }
        }
        if (!stale.isEmpty()) {
            return "needs recomputation because: " + String.join(", ", stale);
        }
        return "still valid against current sources: " + String.join(", ", fresh);
    }

    public Map<String, String> snapshotStatus() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String node : consumes.keySet()) {
            out.put(node, statusOf(node).name());
        }
        return out;
    }

    public Map<String, String> labels() {
        return label;
    }
}
