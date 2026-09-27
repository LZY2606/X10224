package dsc.replay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dsc.model.Branch;
import dsc.model.Derivation;
import dsc.model.Event;
import dsc.model.Interval;
import dsc.model.Source;
import dsc.model.SourceKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds any historical view by walking the parent-linked event chain. Branch tips
 * never overwrite each other, so returning to an old event restores the exact
 * dependency revisions and visible conclusions of that moment.
 */
public final class ViewBuilder {

    private final ObjectMapper mapper;

    public ViewBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public View build(String projectId, List<Source> allSources, List<Event> allEvents,
                      List<Interval> allIntervals, List<Derivation> allDerivations,
                      List<Branch> branches, Branch pointer, String tipEventId) {
        Map<String, Event> byId = new LinkedHashMap<>();
        for (Event e : allEvents) {
            byId.put(e.eventId(), e);
        }
        List<Event> chain = Lineage.chainOf(byId, tipEventId);
        java.util.Set<String> chainIds = new java.util.LinkedHashSet<>();
        for (Event e : chain) {
            chainIds.add(e.eventId());
        }

        Map<SourceKind, List<Source>> history = new LinkedHashMap<>();
        for (SourceKind kind : SourceKind.values()) {
            history.put(kind, new ArrayList<>());
        }
        for (Source s : allSources) {
            if (chainIds.contains(s.producingEventId())) {
                history.get(s.kind()).add(s);
            }
        }
        history.values().forEach(l -> l.sort(Comparator.comparingLong(Source::ingestSeq)));
        Map<SourceKind, Source> current = new LinkedHashMap<>();
        for (var e : history.entrySet()) {
            if (!e.getValue().isEmpty()) {
                current.put(e.getKey(), e.getValue().get(e.getValue().size() - 1));
            }
        }

        List<Interval> intervals = new ArrayList<>();
        Map<String, Interval> latestInterval = new LinkedHashMap<>();
        for (Event event : chain) {
            JsonNode payload = parse(event.payloadJson());
            JsonNode intervalsNode = payload.get("intervals");
            if (intervalsNode != null && intervalsNode.isArray()) {
                for (JsonNode node : intervalsNode) {
                    Interval interval = new Interval(
                            node.get("intervalId").asText(),
                            projectId,
                            node.get("segmentId").asText(),
                            node.get("label").asText(),
                            node.get("startC").asDouble(),
                            node.get("endC").asDouble(),
                            event.seq());
                    latestInterval.put(interval.intervalId(), interval);
                }
            }
        }
        intervals.addAll(latestInterval.values());
        intervals.sort(Comparator.comparingLong(Interval::createdSeq)
                .thenComparing(Interval::intervalId));

        Map<String, String> accepted = new LinkedHashMap<>();
        Map<String, String> rejected = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        for (Event event : chain) {
            JsonNode payload = parse(event.payloadJson());
            if (event.type().equals("BASELINE_ACCEPT")) {
                accepted.put(payload.get("intervalId").asText(),
                        payload.get("baselineKind").asText());
                rejected.remove(payload.get("intervalId").asText());
            } else if (event.type().equals("BASELINE_REJECT")) {
                rejected.put(payload.get("intervalId").asText(),
                        payload.get("baselineKind").asText());
            } else if (event.type().equals("BASELINE_PARAMS")) {
                diagnostics.add("PARAMS_ADJUSTED:" + event.seq());
            }
        }

        List<Derivation> derivations = new ArrayList<>();
        for (Derivation d : allDerivations) {
            if (chainIds.contains(d.producingEventId())) {
                derivations.add(d);
            }
        }
        derivations.sort(Comparator.comparingLong(Derivation::producingSeq)
                .thenComparing(Derivation::derivationId));

        return new View(projectId, pointer, chain, history, current, intervals,
                accepted, rejected, derivations, diagnostics);
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            throw new IllegalStateException("invalid event payload: " + json, e);
        }
    }
}
