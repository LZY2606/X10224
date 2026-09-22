package dsc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable-ish working state reconstructed by replaying the event log.
 * Back references to archived versions are kept (supersededBy / stale flags) so the UI can
 * show why a normalized derived value is expired.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Snapshot {
    public String projectId;
    public String sampleName;
    public String sampleId;
    public String currentRulesetVersion;
    public long upToEventSeq;
    public final Map<String, Segment> segments = new LinkedHashMap<>();
    public final List<SourceRecord> sources = new ArrayList<>();
    public final Map<String, SourceRecord> currentSource = new LinkedHashMap<>();
    public final List<Interval> intervals = new ArrayList<>();
    public final List<TangentSpec> tangents = new ArrayList<>();
    public final Map<String, BaselineCandidate> baselines = new LinkedHashMap<>();
    public final Map<String, String> acceptedBaseline = new LinkedHashMap<>();
    public final Map<String, ThermalResult> results = new LinkedHashMap<>();
    public final Map<String, String> currentResult = new LinkedHashMap<>();
    public final List<Decision> decisions = new ArrayList<>();
    public final List<Derivation> derivations = new ArrayList<>();
    public final List<Anomaly> anomalies = new ArrayList<>();

    public Segment segment(String id) {
        return segments.get(id);
    }

    public SourceRecord current(SourceKind kind, String segmentId) {
        return currentSource.get(key(kind, segmentId));
    }

    public static String key(SourceKind kind, String segmentId) {
        return kind.code + "|" + (segmentId == null ? "-" : segmentId);
    }
}
