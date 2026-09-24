package dsc.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.domain.BaselineFit;
import dsc.domain.BaselineModel;
import dsc.domain.Diagnostic;
import dsc.domain.DscAnalyzer;
import dsc.domain.Interval;
import dsc.domain.Metadata;
import dsc.domain.Quantity;
import dsc.domain.Segment;
import dsc.domain.TangentSelection;
import dsc.domain.TransitionResult;
import dsc.domain.TransitionType;
import dsc.store.Event;
import dsc.store.EventStore;
import dsc.store.EventType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application layer: turns reviewer commands into event batches and rebuilds
 * projections. Every batch is atomic (all-or-nothing pointer switch) and every
 * derived value records the exact source versions it used.
 */
public class AnalysisService {

    private final EventStore store;

    public AnalysisService(EventStore store) {
        this.store = store;
    }

    public long head() {
        return store.head();
    }

    public Projection current() {
        List<Event> all = store.readAll();
        return new ReplayEngine().replay(all, all.isEmpty() ? 0 : all.get(all.size() - 1).seq());
    }

    public Projection at(long seq) {
        return new ReplayEngine().replay(store.readUpTo(seq), seq);
    }

    /** Archive an instrument fragment together with its five separate source records. */
    public List<Event> archiveRaw(String sampleId, Segment segment, String author, long parent) {
        ObjectNode payload = IngestCodec.rawPayload(sampleId, segment);
        List<Diagnostic> directionChecks = new ArrayList<>();
        DscAnalyzer.guardSegment(segment, directionChecks);
        List<EventStore.NewEvent> batch = new ArrayList<>();
        batch.add(new EventStore.NewEvent(EventType.RAW_ARCHIVED, segment.id(),
                Payloads.write(payload), List.of(sampleId)));
        if (!directionChecks.isEmpty()) {
            ObjectNode verdict = Payloads.JSON.createObjectNode();
            verdict.put("segmentId", segment.id());
            verdict.put("observedDirection", segment.observedDirection().name());
            verdict.put("convention", segment.raw().convention().name());
            verdict.set("diagnostics", Payloads.JSON.valueToTree(directionChecks));
            batch.add(new EventStore.NewEvent(EventType.DIRECTION_VERDICT, segment.id(),
                    Payloads.write(verdict), List.of(segment.id())));
        }
        return store.appendBatch(parent, author, batch, segment.id());
    }

    public List<Event> reviseMass(String segmentId, String massId, Quantity mass,
                                  String author, long parent) {
        ObjectNode p = Payloads.JSON.createObjectNode();
        p.put("segmentId", segmentId);
        p.put("massId", massId);
        IngestCodec.setQuantity(p.putObject("mass"), mass);
        return store.appendBatch(parent, author,
                List.of(new EventStore.NewEvent(EventType.MASS_REVISED, massId,
                        Payloads.write(p), List.of(segmentId))), massId);
    }

    /** One direct source changes; only descendants in its dependency subgraph are invalidated. */
    public List<Event> patchSource(String sourceId, String segmentId, String reason,
                                   String author, long parent) {
        ObjectNode p = Payloads.JSON.createObjectNode();
        p.put("sourceId", sourceId);
        p.put("segmentId", segmentId);
        p.put("reason", reason);
        return store.appendBatch(parent, author,
                List.of(new EventStore.NewEvent(EventType.SOURCE_PATCHED, sourceId,
                        Payloads.write(p), List.of(sourceId))), sourceId);
    }

    /**
     * Run analysis for one interval. Returns every legal baseline candidate;
     * overlapping transition windows add an explicit diagnostic instead of
     * merging peaks. Empty intervals produce an EMPTY_INTERVAL event and no
     * fabricated numbers.
     */
    public AnalysisRunResult analyse(String segmentId, Interval interval,
                                     List<TangentSelection> tangentChoices,
                                     TransitionType type, String author, long parent) {
        Projection now = current();
        Segment segment = now.segments().get(segmentId);
        if (segment == null) throw new IllegalArgumentException("unknown segment " + segmentId);
        Quantity mass = now.masses().get(segmentId);
        String massVersion = now.massVersionOf().getOrDefault(segmentId, segment.metadata().id());
        Metadata md = new Metadata(segment.metadata().id(), mass, segment.metadata().scanRate(),
                segment.metadata().atmosphere(), segment.metadata().temperatureCalibration(),
                segment.metadata().note());

        // Empty / out-of-range interval -> one refusal event, no derived rows.
        double[] t = segment.raw().temperature();
        int inside = 0;
        for (double v : t) if (interval.normalized().contains(v)) inside++;
        if (inside < 2) {
            ObjectNode p = Payloads.JSON.createObjectNode();
            p.put("segmentId", segmentId);
            p.put("startK", interval.normalized().startK());
            p.put("endK", interval.normalized().endK());
            p.set("diagnostics", Payloads.JSON.valueToTree(List.of(
                    new Diagnostic(Diagnostic.Severity.ERROR, Diagnostic.Code.EMPTY_INTERVAL,
                            "empty interval: " + inside + " samples", segmentId))));
            List<Event> ev = store.appendBatch(parent, author, List.of(
                    new EventStore.NewEvent(EventType.EMPTY_INTERVAL, "empty@" + segmentId,
                            Payloads.write(p), List.of(segmentId))), segmentId);
            return new AnalysisRunResult(ev, List.of(), List.of(), true);
        }

        List<EventStore.NewEvent> batch = new ArrayList<>();
        List<Projection.BaselineCandidateView> baselineViews = new ArrayList<>();
        List<TransitionResult> results = new ArrayList<>();

        // Detect overlap with already accepted intervals on this segment (kept, not merged).
        boolean overlapping = now.transitionCandidates().getOrDefault(segmentId, Map.of())
                .values().stream()
                .anyMatch(r -> overlaps(r.interval(), interval));

        for (TangentSelection sel : tangentChoices) {
            if (!sel.segmentId().equals(segmentId)) continue;
            String tangentEventId = sel.id();
            batch.add(new EventStore.NewEvent(EventType.TANGENT_ARCHIVED, sel.id(),
                    tangentPayload(sel), List.of(segmentId)));

            List<Diagnostic> fitDiag = new ArrayList<>();
            BaselineFit fit = DscAnalyzer.fitBaseline(segment, sel, fitDiag);
            if (overlapping) {
                fit = addDiagnostic(fit, new Diagnostic(Diagnostic.Severity.WARNING,
                        Diagnostic.Code.OVERLAPPING_TRANSITIONS,
                        "interval overlaps an existing transition; candidates kept separate", sel.id()));
            }
            String candidateId = "base-" + DscAnalyzer.RULE_VERSION + "-"
                    + TransitionIdsHash.hash(sel.id(), String.valueOf(sel.model()),
                    String.valueOf(sel.leftWindow().startK()),
                    String.valueOf(sel.leftWindow().endK()),
                    String.valueOf(sel.rightWindow().startK()),
                    String.valueOf(sel.rightWindow().endK()),
                    fmt(fit.left().a()), fmt(fit.left().b()));
            ObjectNode bp = Payloads.JSON.createObjectNode();
            bp.put("candidateId", candidateId);
            bp.put("segmentId", segmentId);
            bp.put("tangentId", sel.id());
            bp.put("ruleVersion", DscAnalyzer.RULE_VERSION);
            bp.set("fit", Payloads.JSON.valueToTree(fit));
            batch.add(new EventStore.NewEvent(EventType.BASELINE_DERIVED, candidateId,
                    Payloads.write(bp), List.of(segmentId, tangentEventId, massVersion)));
            baselineViews.add(new Projection.BaselineCandidateView(candidateId, fit,
                    DscAnalyzer.RULE_VERSION, -1, sel.id()));

            TransitionResult r = DscAnalyzer.analyse(segment, interval, sel, type, md, massVersion);
            results.add(r);
            ObjectNode rp = Payloads.JSON.createObjectNode();
            rp.set("result", Payloads.JSON.valueToTree(r));
            batch.add(new EventStore.NewEvent(EventType.TRANSITION_DERIVED, r.id(),
                    Payloads.write(rp),
                    List.of(segmentId, tangentEventId, candidateId, massVersion)));
        }

        List<Event> ev = store.appendBatch(parent, author, batch, segmentId);
        return new AnalysisRunResult(ev, baselineViews, results, false);
    }

    /** Reviewer accepts one candidate; rejected/other candidates remain in the log. */
    public List<Event> accept(String segmentId, String scopeKey, String candidateId,
                              String reviewer, String note, long parent) {
        ObjectNode p = Payloads.JSON.createObjectNode();
        p.put("scopeKey", scopeKey);
        p.put("candidateId", candidateId);
        p.put("note", note);
        List<Event> ev = store.appendBatch(parent, reviewer, List.of(
                new EventStore.NewEvent(EventType.DECISION_ACCEPTED, scopeKey,
                        Payloads.write(p), List.of(candidateId, segmentId))), scopeKey);
        store.recordDecision(candidateId, ev.get(0).seq(), "ACCEPTED", reviewer, note);
        return ev;
    }

    public List<Event> reject(String scopeKey, String candidateId, String reviewer,
                              String note, long parent) {
        ObjectNode p = Payloads.JSON.createObjectNode();
        p.put("scopeKey", scopeKey);
        p.put("candidateId", candidateId);
        p.put("note", note);
        List<Event> ev = store.appendBatch(parent, reviewer, List.of(
                new EventStore.NewEvent(EventType.DECISION_REJECTED, scopeKey,
                        Payloads.write(p), List.of(candidateId))), scopeKey);
        store.recordDecision(candidateId, ev.get(0).seq(), "REJECTED", reviewer, note);
        return ev;
    }

    /**
     * Two reviewers diverged from the same base: record the conflict explicitly.
     * Nothing is overwritten; resolution is a follow-up event that chooses one
     * branch while preserving the other candidate.
     */
    public List<Event> conflict(String scopeKey, String candidateA, String candidateB,
                                String reviewer, long parent) {
        ObjectNode p = Payloads.JSON.createObjectNode();
        p.put("candidateA", candidateA);
        p.put("candidateB", candidateB);
        p.put("message", "reviewers selected different legal candidates for " + scopeKey);
        p.set("diagnostics", Payloads.JSON.valueToTree(List.of(
                new Diagnostic(Diagnostic.Severity.WARNING,
                        Diagnostic.Code.CONFLICTING_DECISIONS,
                        "unresolved conflict between two reviewer decisions", scopeKey))));
        List<Event> ev = store.appendBatch(parent, reviewer, List.of(
                new EventStore.NewEvent(EventType.DECISION_CONFLICT, scopeKey,
                        Payloads.write(p), List.of(candidateA, candidateB))), scopeKey);
        store.recordDecision(candidateA, ev.get(0).seq(), "CONFLICT", reviewer, "branch A");
        store.recordDecision(candidateB, ev.get(0).seq(), "CONFLICT", reviewer, "branch B");
        return ev;
    }

    public List<Event> resolveConflict(String scopeKey, String chosenCandidateId,
                                       String reviewer, String note, long parent) {
        ObjectNode p = Payloads.JSON.createObjectNode();
        p.put("scopeKey", scopeKey);
        p.put("chosenCandidateId", chosenCandidateId);
        p.put("note", note);
        List<Event> ev = store.appendBatch(parent, reviewer, List.of(
                new EventStore.NewEvent(EventType.CONFLICT_RESOLVED, scopeKey,
                        Payloads.write(p), List.of(chosenCandidateId))), scopeKey);
        store.recordDecision(chosenCandidateId, ev.get(0).seq(), "ACCEPTED", reviewer, note);
        return ev;
    }

    public List<Event> dragInterval(String segmentId, Interval oldInterval,
                                    Interval newInterval, TransitionType type,
                                    String author, long parent) {
        ObjectNode p = Payloads.JSON.createObjectNode();
        p.put("segmentId", segmentId);
        p.put("type", type.name());
        p.put("oldStartK", oldInterval.normalized().startK());
        p.put("oldEndK", oldInterval.normalized().endK());
        p.put("newStartK", newInterval.normalized().startK());
        p.put("newEndK", newInterval.normalized().endK());
        return store.appendBatch(parent, author, List.of(
                new EventStore.NewEvent(EventType.INTERVAL_DRAGGED, segmentId,
                        Payloads.write(p), List.of(segmentId))), segmentId);
    }

    private static boolean overlaps(Interval a, Interval b) {
        return a.normalized().startK() < b.normalized().endK()
                && b.normalized().startK() < a.normalized().endK();
    }

    private static BaselineFit addDiagnostic(BaselineFit fit, Diagnostic d) {
        List<Diagnostic> all = new ArrayList<>(fit.diagnostics());
        all.add(d);
        return new BaselineFit(fit.model(), fit.left(), fit.right(), fit.stepTemperatureK(),
                fit.leftPointCount(), fit.rightPointCount(), fit.rmseMW(), List.copyOf(all));
    }

    private static String tangentPayload(TangentSelection sel) {
        ObjectNode p = Payloads.JSON.createObjectNode();
        p.set("tangent", Payloads.JSON.valueToTree(sel));
        return Payloads.write(p);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.8g", v);
    }

    public record AnalysisRunResult(List<Event> events,
                                    List<Projection.BaselineCandidateView> baselines,
                                    List<TransitionResult> results,
                                    boolean emptyRefusal) {
    }
}
