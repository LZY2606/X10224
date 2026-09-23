package dsc.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dsc.event.Event;
import dsc.event.EventTypes;
import dsc.model.BaselineFit;
import dsc.model.Interval;
import dsc.model.Sample;
import dsc.model.Segment;
import dsc.model.SourceRecord;
import dsc.model.TransitionFeature;
import dsc.model.Types;
import dsc.derive.PieceBaseline;

import java.util.List;
import java.util.Map;

/**
 * Applies a single journal event to a {@link StateRepository}. Because state changes are a
 * pure fold over the event stream, rebuilding to event N returns exactly the view a reviewer
 * saw after N — later calculations never overwrite that history.
 */
public final class EventPlayer {
    private final ObjectMapper mapper = new ObjectMapper();

    public void apply(StateRepository state, Event event) {
        if (state.eventsView().stream().anyMatch(e -> e.seq == event.seq)) return;
        Map<String, Object> p = payload(event.payloadJson);
        switch (event.type) {
            case EventTypes.IMPORT, EventTypes.ARCHIVE_IMPORTED -> applyImport(state, event, p);
            case EventTypes.SOURCE_REVISED, EventTypes.MASS_REVISED ->
                    applySource(state, event, p);
            case EventTypes.INTERVAL_DRAGGED -> applyInterval(state, event, p);
            case EventTypes.TANGENT_SELECTED -> applyTangent(state, event, p);
            case EventTypes.BASELINES_DERIVED -> applyBaselines(state, event, p);
            case EventTypes.BASELINE_ACCEPTED, EventTypes.BASELINE_REJECTED ->
                    applyDecision(state, event, p);
            case EventTypes.DERIVED_EXPIRED -> applyStaleFlag(state, p, true);
            case EventTypes.DERIVED_STILL_VALID -> applyStaleFlag(state, p, false);
            default -> { /* marker/diagnostic events are history, no state mutation */ }
        }
        state.events.add(event);
    }

    private Map<String, Object> payload(String json) {
        try {
            return json == null || json.isBlank() ? Map.of()
                    : mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("bad payload: " + json, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyImport(StateRepository s, Event e, Map<String, Object> p) {
        List<Map<String, Object>> samples = (List<Map<String, Object>>) p.getOrDefault("samples", List.of());
        for (Map<String, Object> m : samples) {
            s.putSample(new Sample((String) m.get("id"), (String) m.get("name"), (String) m.get("material")));
        }
        List<Map<String, Object>> segs = (List<Map<String, Object>>) p.getOrDefault("segments", List.of());
        for (Map<String, Object> m : segs) {
            s.putSegment(new Segment((String) m.get("id"), (String) m.get("sampleId"),
                    (String) m.get("cycleId"), (String) m.get("label"),
                    ((Number) m.get("order")).intValue(),
                    Types.ScanDirection.valueOf((String) m.get("direction"))));
        }
        List<Map<String, Object>> srcs = (List<Map<String, Object>>) p.getOrDefault("sources", List.of());
        for (Map<String, Object> m : srcs) {
            s.putSource(toSource(m));
        }
        List<Map<String, Object>> ivs = (List<Map<String, Object>>) p.getOrDefault("intervals", List.of());
        for (Map<String, Object> m : ivs) {
            applyInterval(s, e, m);
        }
        List<Map<String, Object>> bls = (List<Map<String, Object>>) p.getOrDefault("baselines", List.of());
        for (Map<String, Object> m : bls) {
            applyOneBaseline(s, m);
        }
        List<Map<String, Object>> fs = (List<Map<String, Object>>) p.getOrDefault("features", List.of());
        for (Map<String, Object> m : fs) {
            applyOneFeature(s, m);
        }
    }

    @SuppressWarnings("unchecked")
    private SourceRecord toSource(Map<String, Object> m) {
        return new SourceRecord(
                (String) m.get("id"), (String) m.get("sampleId"),
                (String) m.get("segmentId"), Types.SourceKind.valueOf((String) m.get("kind")),
                (String) m.get("label"), (String) m.get("rawText"),
                (String) m.get("unit"), (String) m.get("reference"),
                (String) m.getOrDefault("origin", "import"),
                ((Number) m.getOrDefault("version", 1)).intValue(),
                mapper.convertValue(m.getOrDefault("metadata", Map.of()),
                        new TypeReference<Map<String, String>>() {}));
    }

    private void applySource(StateRepository s, Event e, Map<String, Object> p) {
        s.putSource(toSource(asMap(p, "source")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Map<String, Object> p, String key) {
        return (Map<String, Object>) p.get(key);
    }

    private void applyInterval(StateRepository s, Event e, Map<String, Object> p) {
        s.putInterval(new Interval((String) p.get("id"), (String) p.get("segmentId"),
                (String) p.getOrDefault("label", ""),
                ((Number) p.get("startK")).doubleValue(),
                ((Number) p.get("endK")).doubleValue(),
                ((Number) p.getOrDefault("version", 1)).intValue()));
    }

    private void applyTangent(StateRepository s, Event e, Map<String, Object> p) {
        s.putSource(toSource(asMap(p, "source")));
    }

    @SuppressWarnings("unchecked")
    private void applyBaselines(StateRepository s, Event e, Map<String, Object> p) {
        for (Map<String, Object> m : (List<Map<String, Object>>) p.getOrDefault("baselines", List.of())) {
            applyOneBaseline(s, m);
        }
    }

    private void applyOneBaseline(StateRepository s, Map<String, Object> m) {
        Types.Decision d = Types.Decision.valueOf(
                (String) m.getOrDefault("decision", Types.Decision.UNDECIDED.name()));
        BaselineFit.Kind kind = BaselineFit.Kind.valueOf((String) m.get("kind"));
        String id = (String) m.get("id");
        String intervalId = (String) m.get("intervalId");
        double a = num(m, "a"), b = num(m, "b"), c = num(m, "c");
        double rms = m.get("rmsW") == null ? Double.NaN : ((Number) m.get("rmsW")).doubleValue();
        String by = (String) m.get("decidedByEvent");
        String rationale = (String) m.getOrDefault("rationale", "");
        int ver = ((Number) m.getOrDefault("version", 1)).intValue();
        if (kind == BaselineFit.Kind.PIECEWISE_TANGENT) {
            s.putBaseline(new PieceBaseline(id, intervalId, a, b,
                    num(m, "postA"), num(m, "postB"),
                    m.get("joinK") == null ? Double.NaN : ((Number) m.get("joinK")).doubleValue(),
                    d, by, rationale, ver));
        } else {
            s.putBaseline(new BaselineFit(id, intervalId, kind, a, b, c, rms, d, by, rationale, ver));
        }
    }

    private void applyDecision(StateRepository s, Event e, Map<String, Object> p) {
        String baselineId = (String) p.get("baselineId");
        int version = ((Number) p.getOrDefault("version", 1)).intValue();
        BaselineFit current = s.currentBaseline(baselineId);
        if (current == null) return;
        Types.Decision d = EventTypes.BASELINE_ACCEPTED.equals(e.type)
                ? Types.Decision.ACCEPTED : Types.Decision.REJECTED;
        // decision is a new version of the same stable id
        s.putBaseline(copyWithDecision(current, version, d, e.id));
    }

    private BaselineFit copyWithDecision(BaselineFit b, int version, Types.Decision d, String eventId) {
        if (b instanceof PieceBaseline pb) {
            return new PieceBaseline(pb.id, pb.intervalId, pb.preA, pb.preB, pb.postA, pb.postB,
                    pb.joinK, d, eventId, pb.rationale, version);
        }
        return new BaselineFit(b.id, b.intervalId, b.kind, b.a, b.b, b.c, b.rmsW,
                d, eventId, b.rationale, version);
    }

    @SuppressWarnings("unchecked")
    private void applyOneFeature(StateRepository s, Map<String, Object> m) {
        TransitionFeature f = new TransitionFeature(
                (String) m.get("id"), (String) m.get("baselineId"),
                Types.TransitionKind.valueOf((String) m.get("kind")),
                num(m, "onsetK"), num(m, "peakK"), num(m, "endsetK"),
                num(m, "energyJ"), num(m, "enthalpyJkg"),
                mapper.convertValue(m.getOrDefault("errorSources", List.of()),
                        new TypeReference<List<String>>() {}),
                mapper.convertValue(m.getOrDefault("annotated", Map.of()),
                        new TypeReference<Map<String, String>>() {}));
        boolean stale = Boolean.TRUE.equals(m.get("stale"));
        String hash = (String) m.get("depsHash");
        s.putFeature(new StateRepository.FeatureRow(f, stale, hash));
    }

    @SuppressWarnings("unchecked")
    private void applyStaleFlag(StateRepository s, Map<String, Object> p, boolean stale) {
        for (String id : (List<String>) p.getOrDefault("featureIds", List.of())) {
            s.markStale(id, stale);
        }
    }

    private double num(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return Double.NaN;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return Double.NaN; }
    }
}
