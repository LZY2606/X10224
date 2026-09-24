package dsc.service;

import com.fasterxml.jackson.databind.JsonNode;
import dsc.domain.BaselineFit;
import dsc.domain.Diagnostic;
import dsc.domain.Metadata;
import dsc.domain.Quantity;
import dsc.domain.Segment;
import dsc.domain.TangentSelection;
import dsc.domain.TransitionResult;
import dsc.domain.TransitionType;
import dsc.store.Event;
import dsc.store.EventType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds the review state at any point in history by folding events.
 *
 * Crucially nothing here mutates candidates: a new mass revision inserts a new
 * mass version and marks every normalised descendant stale; the old candidate
 * remains in the map and remains reachable at the old event prefix. Multiple
 * legal baselines coexist as parallel entries.
 */
public class ReplayEngine {

    public static final class RawSegmentState {
        public Segment segment;
        public Metadata metadata;
        public final List<Quantity> massHistory = new ArrayList<>();
        public Quantity currentMass;
        public String currentMassId;
    }

    public Projection replay(List<Event> events, long atSeq) {
        Map<String, RawSegmentState> raws = new LinkedHashMap<>();
        Map<String, TangentSelection> tangents = new LinkedHashMap<>();
        // segmentId -> candidateId -> view
        Map<String, Map<String, Projection.BaselineCandidateView>> baselines = new LinkedHashMap<>();
        Map<String, Map<String, TransitionResult>> results = new LinkedHashMap<>();
        Map<String, Projection.Staleness> staleness = new LinkedHashMap<>();
        Map<String, String> accepted = new LinkedHashMap<>();
        Map<String, String> massVersion = new LinkedHashMap<>();
        List<Projection.AnomalyView> anomalies = new ArrayList<>();
        List<String> log = new ArrayList<>();

        DependencyGraph graph = new DependencyGraph();
        graph.index(events);

        for (Event e : events) {
            if (e.seq() > atSeq) break;
            JsonNode p = Payloads.tree(e.payload());
            switch (e.type()) {
                case RAW_ARCHIVED -> {
                    Segment seg = IngestCodec.segment(p);
                    RawSegmentState st = new RawSegmentState();
                    st.segment = seg;
                    st.metadata = seg.metadata();
                    st.currentMass = seg.metadata().mass();
                    st.currentMassId = seg.metadata().id();
                    st.massHistory.add(seg.metadata().mass());
                    raws.put(seg.id(), st);
                    massVersion.put(seg.id(), st.currentMassId);
                    log.add("[" + e.seq() + "] raw archived: " + seg.id()
                            + " (" + seg.raw().size() + " points)");
                }
                case METADATA_ARCHIVED -> {
                    String segId = p.path("segmentId").asText();
                    RawSegmentState st = raws.get(segId);
                    if (st != null) {
                        Quantity mass = Payloads.quantity(p.get("mass"));
                        st.massHistory.add(mass);
                        st.currentMass = mass;
                        st.currentMassId = p.path("massId").asText();
                        massVersion.put(segId, st.currentMassId);
                    }
                }
                case MASS_REVISED -> {
                    String segId = p.path("segmentId").asText();
                    RawSegmentState st = raws.get(segId);
                    Quantity mass = Payloads.quantity(p.get("mass"));
                    st.massHistory.add(mass);
                    st.currentMass = mass;
                    st.currentMassId = p.path("massId").asText();
                    massVersion.put(segId, st.currentMassId);
                    // Propagate: every normalised descendant of this segment goes stale.
                    for (String affected : graph.affectedBy(segId)) {
                        if (results.values().stream().anyMatch(m -> m.containsKey(affected))) {
                            staleness.put(affected, new Projection.Staleness(true,
                                    "mass revision " + st.currentMassId
                                            + " invalidates every normalised derivation",
                                    st.currentMassId));
                        }
                    }
                    log.add("[" + e.seq() + "] mass revised for " + segId
                            + " -> " + st.currentMassId + "; normalised derivations marked stale");
                }
                case SOURCE_PATCHED -> {
                    String changedSource = p.path("sourceId").asText();
                    String scopeSegment = p.path("segmentId").asText();
                    for (String affected : graph.affectedBy(changedSource)) {
                        staleness.put(affected, new Projection.Staleness(true,
                                "direct source '" + changedSource + "' changed; recompute required",
                                changedSource));
                    }
                    log.add("[" + e.seq() + "] source patch on " + changedSource
                            + "; local propagation only (" + scopeSegment + ")");
                }
                case TANGENT_ARCHIVED, PARAM_ADJUSTED -> {
                    TangentSelection t = Payloads.tangent(p);
                    tangents.put(t.id(), t);
                }
                case BASELINE_DERIVED -> {
                    String segId = p.path("segmentId").asText();
                    String candId = p.path("candidateId").asText();
                    BaselineFit fit = Payloads.baselineFit(p);
                    baselines.computeIfAbsent(segId, k -> new LinkedHashMap<>())
                            .put(candId, new Projection.BaselineCandidateView(candId, fit,
                                    p.path("ruleVersion").asText(), e.seq(),
                                    p.path("tangentId").asText()));
                    if (fit.diagnostics() != null) {
                        for (Diagnostic d : fit.diagnostics()) addAnomaly(anomalies, d, candId, e);
                    }
                }
                case TRANSITION_DERIVED -> {
                    TransitionResult r = Payloads.result(p);
                    results.computeIfAbsent(r.segmentId(), k -> new LinkedHashMap<>())
                            .put(r.id(), r);
                    if (!staleness.containsKey(r.id())) {
                        staleness.put(r.id(), Projection.Staleness.fresh());
                    }
                    for (Diagnostic d : r.diagnostics()) addAnomaly(anomalies, d, r.id(), e);
                }
                case DIRECTION_VERDICT, SEGMENT_SPLIT, EMPTY_INTERVAL, DECISION_CONFLICT -> {
                    for (Diagnostic d : Payloads.diagnostics(p.get("diagnostics"))) {
                        addAnomaly(anomalies, d, e.subjectId(), e);
                    }
                    if (e.type() == EventType.DECISION_CONFLICT) {
                        anomalies.add(new Projection.AnomalyView("CONFLICTING_DECISIONS", "WARNING",
                                p.path("message").asText("conflicting reviewer decisions"),
                                e.subjectId(), e.seq(), p.toString()));
                    }
                }
                case DECISION_ACCEPTED -> accepted.put(p.path("scopeKey").asText(
                        e.subjectId()), p.path("candidateId").asText());
                case DECISION_REJECTED -> accepted.remove(p.path("scopeKey").asText(e.subjectId()));
                default -> {
                }
            }
        }

        Map<String, Segment> segMap = new LinkedHashMap<>();
        Map<String, Quantity> masses = new LinkedHashMap<>();
        for (Map.Entry<String, RawSegmentState> en : raws.entrySet()) {
            segMap.put(en.getKey(), en.getValue().segment);
            masses.put(en.getKey(), en.getValue().currentMass);
        }
        return new Projection(atSeq, segMap, masses, massVersion, tangents, baselines, results,
                staleness, accepted, events.stream().filter(e -> e.seq() <= atSeq).toList(),
                List.copyOf(anomalies), List.copyOf(log));
    }

    private void addAnomaly(List<Projection.AnomalyView> anomalies, Diagnostic d,
                            String subject, Event e) {
        anomalies.add(new Projection.AnomalyView(d.code().name(), d.severity().name(),
                d.message(), subject, e.seq(),
                Payloads.write(java.util.Map.of("eventSeq", e.seq(), "subject", subject,
                        "dependsOn", e.dependsOn()))));
    }
}
