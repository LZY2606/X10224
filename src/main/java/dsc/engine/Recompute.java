package dsc.engine;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.domain.Derivation;
import dsc.domain.Event;

import java.util.*;

/**
 * Dependency-graph propagation. Given the materialized state after a batch of
 * new fact events, it re-derives only the affected keys:
 *
 *   curve/source patch -> NORMALIZED(segment) -> BASELINE/EVENT of that segment
 *   mass revision      -> every NORMALIZED of the sample (all normalized
 *                         derivations expire) -> all their downstream keys
 *   calibration        -> temperature-bearing derivations of the sample
 *   interval action    -> only that interval's BASELINE/EVENT
 *
 * The set returned also answers "why still valid / why recomputed": each
 * derivation carries its dependency list and content-addressed id.
 */
public final class Recompute {
    private Recompute() {}

    public record Result(List<Derivation> derivations, List<Event> judgments,
                         Map<String, String> affectedReasons) {}

    /**
     * Rebuild derivations from scratch for a branch (used for full replay and
     * for time-travel snapshots); results are identical to incremental runs
     * because ids are content-addressed.
     */
    public static Result full(State st, String branch) {
        List<Derivation> all = new ArrayList<>();
        List<Event> judges = new ArrayList<>();
        LinkedHashSet<String> doneSamples = new LinkedHashSet<>();
        for (ObjectNode curve : new ArrayList<>(st.curves.values())) {
            String segId = curve.path("id").asText();
            Engine.Reaction r = Engine.resolveSegment(branch, st, segId);
            all.addAll(r.derivations());
            judges.addAll(r.judgments());
            String sampleId = curve.path("sampleId").asText();
            if (doneSamples.add(sampleId)) judges.addAll(Engine.sampleJudgments(branch, st, sampleId));
        }
        Map<String, String> reasons = new LinkedHashMap<>();
        for (Derivation d : all) reasons.put(d.id(), "FULL_REBUILD");
        return new Result(all, dedup(judges), reasons);
    }

    /**
     * Incremental run. {@code dirtySegments}/{@code dirtyIntervals}/
     * {@code massSamples}/{@code calibrationSamples} describe what the new
     * events touched. Previous derivations already live in {@code st.current}.
     */
    public static Result incremental(State st, String branch, Set<String> dirtySegments,
                                     Set<String> dirtyIntervals, Set<String> massSamples,
                                     Set<String> calibrationSamples) {
        Set<String> segments = new LinkedHashSet<>(dirtySegments);
        for (ObjectNode c : st.curves.values()) {
            String sid = c.path("sampleId").asText();
            if (massSamples.contains(sid) || calibrationSamples.contains(sid)) {
                segments.add(c.path("id").asText());
            }
        }
        List<Derivation> all = new ArrayList<>();
        List<Event> judges = new ArrayList<>();
        Map<String, String> reasons = new LinkedHashMap<>();
        LinkedHashSet<String> samples = new LinkedHashSet<>();
        for (String segId : segments) {
            ObjectNode curve = st.curve(segId);
            if (curve == null) continue;
            samples.add(curve.path("sampleId").asText());
            Engine.Reaction r = Engine.resolveSegment(branch, st, segId);
            for (Derivation d : r.derivations()) {
                String why = reasonFor(d, dirtySegments, dirtyIntervals, massSamples, calibrationSamples, st);
                reasons.put(d.id(), why);
            }
            all.addAll(r.derivations());
            judges.addAll(r.judgments());
        }
        // an interval-only change still forces sample-level mix checks
        for (String ivId : dirtyIntervals) {
            ObjectNode iv = st.intervals.get(ivId);
            if (iv != null) {
                String segId = iv.path("segmentId").asText();
                ObjectNode c = st.curve(segId);
                if (c != null) samples.add(c.path("sampleId").asText());
            }
        }
        for (String sid : samples) judges.addAll(Engine.sampleJudgments(branch, st, sid));
        return new Result(all, dedup(judges), reasons);
    }

    private static String reasonFor(Derivation d, Set<String> dirtySegments, Set<String> dirtyIntervals,
                                    Set<String> massSamples, Set<String> calibrationSamples, State st) {
        String key = d.key();
        if (key.startsWith("NORMALIZED:")) {
            String seg = key.substring("NORMALIZED:".length());
            if (massSamples.contains(st.curve(seg).path("sampleId").asText()))
                return "MASS_REVISION: all mass-normalized derivations expire and must recompute";
            if (calibrationSamples.contains(st.curve(seg).path("sampleId").asText()))
                return "CALIBRATION_REVISION: temperature basis changed";
            return "DIRECT_SOURCE_PATCH: instrument output for this segment changed";
        }
        if (key.startsWith("BASELINE:") || key.startsWith("EVENT:")) {
            String[] parts = key.split(":");
            String ivId = parts.length >= 3 ? parts[2] : "";
            if (dirtyIntervals.contains(ivId))
                return "INTERVAL_ACTION: this interval was drawn, refit, adopted or rejected";
            return "DOWNSTREAM: its NORMALIZED dependency changed (dependency graph propagation)";
        }
        return "RECOMPUTED";
    }

    private static List<Event> dedup(List<Event> events) {
        LinkedHashMap<String, Event> byId = new LinkedHashMap<>();
        for (Event e : events) byId.put(e.id(), e);
        return new ArrayList<>(byId.values());
    }
}
