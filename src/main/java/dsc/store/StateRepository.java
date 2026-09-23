package dsc.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import dsc.event.Event;
import dsc.model.BaselineFit;
import dsc.model.Interval;
import dsc.model.Sample;
import dsc.model.Segment;
import dsc.model.SourceRecord;
import dsc.model.TransitionFeature;
import dsc.model.Types;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Materialised state rebuilt (or incrementally maintained) by replaying the event journal.
 * Every entity map is keyed by stable id and stores all versions; {@code current} holds the
 * current pointer. Replaying events up to a given seq reproduces the exact historical view.
 */
public final class StateRepository {
    public static final class FeatureRow {
        public final TransitionFeature feature;
        public final boolean stale;
        public final String depsHash;

        public FeatureRow(TransitionFeature feature, boolean stale, String depsHash) {
            this.feature = feature;
            this.stale = stale;
            this.depsHash = depsHash;
        }
    }

    final Map<String, Sample> samples = new LinkedHashMap<>();
    final Map<String, Segment> segments = new LinkedHashMap<>();
    final Map<String, List<SourceRecord>> sources = new LinkedHashMap<>();
    final Map<String, List<Interval>> intervals = new LinkedHashMap<>();
    final Map<String, List<BaselineFit>> baselines = new LinkedHashMap<>();
    final Map<String, FeatureRow> featureRows = new LinkedHashMap<>();
    final List<Event> events = new ArrayList<>();

    private final ObjectMapper mapper = new ObjectMapper();

    public List<Event> eventsView() {
        return List.copyOf(events);
    }

    public List<Sample> samples() {
        return List.copyOf(samples.values());
    }

    public Sample sample(String id) {
        return samples.get(id);
    }

    public List<Segment> segmentsOf(String sampleId) {
        List<Segment> out = new ArrayList<>();
        for (Segment s : segments.values()) if (sampleId.equals(s.sampleId)) out.add(s);
        out.sort((x, y) -> Integer.compare(x.order, y.order));
        return out;
    }

    public Segment segment(String id) {
        return segments.get(id);
    }

    public List<SourceRecord> sourcesBySegment(String segmentId) {
        List<SourceRecord> all = new ArrayList<>();
        for (List<SourceRecord> l : sources.values()) {
            for (SourceRecord r : l) if (segmentId.equals(r.segmentId)) all.add(r);
        }
        return all;
    }

    public List<SourceRecord> allSourceVersions(String id) {
        return List.copyOf(sources.getOrDefault(id, List.of()));
    }

    public SourceRecord currentSource(String id, Types.SourceKind kind, String scopeSegmentId) {
        if (scopeSegmentId == null) {
            return currentSourcesOf(id, null, kind);
        }
        return currentSourcesOf(id, scopeSegmentId, kind);
    }

    /**
     * Returns the newest version of a source for a scope. Source ids are stable across
     * revisions; scope is sample level (segmentId null) or the given segment.
     */
    public SourceRecord currentSourcesOf(String sampleId, String segmentId, Types.SourceKind kind) {
        SourceRecord best = null;
        for (List<SourceRecord> l : sources.values()) {
            SourceRecord r = l.get(l.size() - 1);
            if (!sampleId.equals(r.sampleId) || r.kind != kind) continue;
            boolean scopeOk = segmentId == null ? r.segmentId == null : segmentId.equals(r.segmentId);
            if (scopeOk && (best == null || r.version > best.version)) best = r;
        }
        return best;
    }

    public List<SourceRecord> currentSourcesOfSample(String sampleId) {
        List<SourceRecord> out = new ArrayList<>();
        for (List<SourceRecord> l : sources.values()) {
            SourceRecord r = l.get(l.size() - 1);
            if (sampleId.equals(r.sampleId)) out.add(r);
        }
        return out;
    }

    public List<Interval> intervalsOf(String segmentId) {
        List<Interval> out = new ArrayList<>();
        for (List<Interval> l : intervals.values()) {
            Interval iv = l.get(l.size() - 1);
            if (segmentId.equals(iv.segmentId)) out.add(iv);
        }
        return out;
    }

    public Interval currentInterval(String id) {
        List<Interval> l = intervals.get(id);
        return l == null ? null : l.get(l.size() - 1);
    }

    public List<BaselineFit> baselinesOfInterval(String intervalId) {
        List<BaselineFit> out = new ArrayList<>();
        for (List<BaselineFit> l : baselines.values()) {
            BaselineFit b = l.get(l.size() - 1);
            if (intervalId.equals(b.intervalId)) out.add(b);
        }
        return out;
    }

    public BaselineFit currentBaseline(String id) {
        List<BaselineFit> l = baselines.get(id);
        return l == null ? null : l.get(l.size() - 1);
    }

    public List<FeatureRow> featuresOfBaseline(String baselineId) {
        List<FeatureRow> out = new ArrayList<>();
        for (FeatureRow row : featureRows.values()) {
            if (baselineId.equals(row.feature.baselineId)) out.add(row);
        }
        return out;
    }

    public Optional<FeatureRow> feature(String id) {
        return Optional.ofNullable(featureRows.get(id));
    }

    public List<FeatureRow> allFeatures() {
        return List.copyOf(featureRows.values());
    }

    // ---- mutation used by the event player ---------------------------------

    void putSample(Sample s) {
        samples.put(s.id, s);
    }

    void putSegment(Segment s) {
        segments.put(s.id, s);
    }

    void putSource(SourceRecord r) {
        sources.computeIfAbsent(r.id, k -> new ArrayList<>());
        List<SourceRecord> list = sources.get(r.id);
        if (list.stream().anyMatch(x -> x.version == r.version)) return;
        list.add(r);
        list.sort((x, y) -> Integer.compare(x.version, y.version));
    }

    void putInterval(Interval iv) {
        intervals.computeIfAbsent(iv.id, k -> new ArrayList<>()).add(iv);
    }

    void putBaseline(BaselineFit b) {
        List<BaselineFit> l = baselines.computeIfAbsent(b.id, k -> new ArrayList<>());
        if (l.stream().anyMatch(x -> x.version == b.version)) return;
        l.add(b);
        l.sort((x, y) -> Integer.compare(x.version, y.version));
    }

    void putFeature(FeatureRow row) {
        featureRows.put(row.feature.id, row);
    }

    void markStale(String featureId, boolean stale) {
        FeatureRow row = featureRows.get(featureId);
        if (row != null && row.stale != stale) {
            featureRows.put(featureId, new FeatureRow(row.feature, stale, row.depsHash));
        }
    }

    Map<String, Object> readJson(String json) {
        try {
            if (json == null || json.isBlank()) return Map.of();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("bad event payload: " + json, e);
        }
    }

    String writeJson(Map<String, Object> m) {
        try {
            return mapper.writeValueAsString(m);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
