package com.materials.dsc.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The full sample dossier: heating and cooling segments share one history;
 * separately archived sources; immutable events and derivation versions.
 */
public final class SampleHistory {
    public String id;
    public String name;
    public final List<Segment> segments = new ArrayList<>();
    public final Map<String, SourceRecord> sourcesById = new LinkedHashMap<>();
    public final List<Event> events = new ArrayList<>();
    public final List<DerivedNode> nodes = new ArrayList<>();
    public long headSeq;
    public String headVersion;

    public Segment segment(String id) {
        for (Segment s : segments) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }
}
