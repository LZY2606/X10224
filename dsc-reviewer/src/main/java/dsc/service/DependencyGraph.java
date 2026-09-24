package dsc.service;

import dsc.store.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The "why still valid / why recompute" graph. Each derived event lists the
 * stable ids it depends on. When one direct source changes, transitive
 * propagation marks exactly the affected descendants — an unrelated segment's
 * results stay valid and are NOT recomputed.
 */
public final class DependencyGraph {

    private final Map<String, List<String>> dependents = new HashMap<>();
    private final Map<String, String> versionOf = new HashMap<>();

    public void index(List<Event> events) {
        dependents.clear();
        versionOf.clear();
        for (Event e : events) {
            versionOf.put(e.subjectId(), e.subjectId() + "#" + e.seq());
            for (String dep : e.dependsOn()) {
                dependents.computeIfAbsent(stable(dep), k -> new ArrayList<>())
                        .add(e.subjectId());
            }
        }
    }

    /** Strip a "#seq" suffix to get the stable id used as graph key. */
    public static String stable(String idOrRef) {
        int i = idOrRef.indexOf('#');
        return i < 0 ? idOrRef : idOrRef.substring(0, i);
    }

    /** All derived subjects transitively dependent on the given source (BFS). */
    public Set<String> affectedBy(String sourceStableId) {
        Set<String> seen = new LinkedHashSet<>();
        ArrayQueue q = new ArrayQueue();
        q.add(sourceStableId);
        seen.add(sourceStableId);
        while (!q.isEmpty()) {
            String cur = q.poll();
            for (String child : dependents.getOrDefault(cur, Collections.emptyList())) {
                if (seen.add(child)) q.add(child);
            }
        }
        seen.remove(sourceStableId);
        return seen;
    }

    public Set<String> allSources() {
        return new HashSet<>(versionOf.keySet());
    }

    private static final class ArrayQueue {
        private final List<String> items = new ArrayList<>();
        private int head = 0;

        void add(String s) {
            items.add(s);
        }

        String poll() {
            return items.get(head++);
        }

        boolean isEmpty() {
            return head >= items.size();
        }
    }
}
