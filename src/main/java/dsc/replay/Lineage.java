package dsc.replay;

import dsc.model.Branch;
import dsc.model.Event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Helpers for the parent-linked event tree. */
public final class Lineage {

    private Lineage() {
    }

    /** Events on the root -> tip path, in chronological order. */
    public static List<Event> chainOf(Map<String, Event> byId, String tipEventId) {
        List<Event> reversed = new ArrayList<>();
        Set<String> guard = new LinkedHashSet<>();
        String cursor = tipEventId;
        while (cursor != null && guard.add(cursor)) {
            Event e = byId.get(cursor);
            if (e == null) {
                break;
            }
            reversed.add(e);
            cursor = e.parentEventId();
        }
        List<Event> chain = new ArrayList<>(reversed);
        java.util.Collections.reverse(chain);
        return chain;
    }

    public static long baseSeq(Map<String, Event> byId, String tipEventId) {
        Event tip = byId.get(tipEventId);
        return tip == null ? 0 : tip.seq();
    }

    public static List<Branch> liveBranches(List<Branch> all) {
        return new ArrayList<>(all);
    }

    public static Map<String, String> parentIndex(List<Event> events) {
        Map<String, String> parents = new LinkedHashMap<>();
        for (Event e : events) {
            parents.put(e.eventId(), e.parentEventId());
        }
        return parents;
    }
}
