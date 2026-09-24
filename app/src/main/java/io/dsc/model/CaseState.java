package io.dsc.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Materialised view of one branch of a case, produced by folding the event log.
 * Time travel rebuilds this object from a prefix of the log; current results
 * never overwrite historical conclusions because history is pure replay.
 */
public final class CaseState {
    public CaseMetadata metadata;
    public String branchId;
    public long headSeq;
    public String headHash;

    /** direct sources, keyed by stable id; newest version present. */
    public final Map<StableId, SourceRecord> sources = new LinkedHashMap<>();
    /** per-kind/per-segment pointer to the current source version id. */
    public final Map<String, StableId> currentSource = new LinkedHashMap<>();

    public final Map<StableId, Segment> segments = new LinkedHashMap<>();
    public final List<String> segmentOrder = new ArrayList<>();

    public final Map<StableId, Interval> intervals = new LinkedHashMap<>();
    public final Map<StableId, BaselineCandidate> baselines = new LinkedHashMap<>();
    public final Map<StableId, TransitionResult> transitions = new LinkedHashMap<>();
    public final Map<String, Diagnostic> diagnostics = new LinkedHashMap<>();

    /** source versions grouped by key kind:segment -> all versions. */
    public final Map<String, List<SourceRecord>> sourceHistory = new LinkedHashMap<>();

    public void putSource(SourceRecord rec) {
        sources.put(rec.id(), rec);
        String key = sourceKey(rec.kind(), rec.segmentId());
        currentSource.put(key, rec.id());
        sourceHistory.computeIfAbsent(key, k -> new ArrayList<>()).add(rec);
    }

    public SourceRecord currentSource(SourceKind kind, String segmentId) {
        StableId id = currentSource.get(sourceKey(kind, segmentId));
        return id == null ? null : sources.get(id);
    }

    public static String sourceKey(SourceKind kind, String segmentId) {
        return kind.code() + (segmentId == null ? "" : ":" + segmentId);
    }

    public Interval intervalForSlot(String slot) {
        for (Interval iv : intervals.values()) if (iv.slot().equals(slot)) return iv;
        return null;
    }

    public List<BaselineCandidate> baselinesForSlot(String slot) {
        List<BaselineCandidate> out = new ArrayList<>();
        for (BaselineCandidate b : baselines.values()) if (b.slot().equals(slot)) out.add(b);
        return out;
    }

    public BaselineCandidate adoptedBaseline(String slot) {
        for (BaselineCandidate b : baselines.values()) {
            if (b.slot().equals(slot) && b.adopted()) return b;
        }
        return null;
    }

    public List<TransitionResult> transitionsForSlot(String slot) {
        List<TransitionResult> out = new ArrayList<>();
        for (TransitionResult t : transitions.values()) if (t.slot().equals(slot)) out.add(t);
        return out;
    }

    public TransitionResult currentTransition(String slot) {
        TransitionResult best = null;
        for (TransitionResult t : transitions.values()) {
            if (!t.slot().equals(slot)) continue;
            if (t.status() == TransitionResult.Status.CURRENT) return t;
            if (best == null) best = t;
        }
        return best;
    }

    public List<Diagnostic> diagnosticsFor(String targetId) {
        List<Diagnostic> out = new ArrayList<>();
        for (Diagnostic d : diagnostics.values()) if (targetId.equals(d.targetId())) out.add(d);
        return out;
    }
}
