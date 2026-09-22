package dsc.store;

import dsc.model.Doc;
import java.util.*;

/** Immutable materialized view at one journal position. */
public final class Snapshot {
    public final long seq;
    private final Map<String, Doc> byLineage;

    public Snapshot(long seq, Map<String, Doc> byLineage) {
        this.seq = seq;
        this.byLineage = Collections.unmodifiableMap(new LinkedHashMap<>(byLineage));
    }

    public Collection<Doc> docs() { return byLineage.values(); }

    public Optional<Doc> byLineage(String lineage) {
        return Optional.ofNullable(byLineage.get(lineage));
    }

    public List<Doc> byKind(String kind) {
        return byLineage.values().stream().filter(d -> d.kind.equals(kind)).toList();
    }

    public Optional<Doc> byId(String id) {
        return byLineage.values().stream().filter(d -> d.id.equals(id)).findFirst();
    }
}
