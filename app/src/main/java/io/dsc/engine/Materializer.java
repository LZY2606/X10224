package io.dsc.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dsc.model.BaselineCandidate;
import io.dsc.model.Branch;
import io.dsc.model.CaseMetadata;
import io.dsc.model.CaseState;
import io.dsc.model.Derivation;
import io.dsc.model.DerivedValue;
import io.dsc.model.Diagnostic;
import io.dsc.model.ErrorContribution;
import io.dsc.model.Event;
import io.dsc.model.EventType;
import io.dsc.model.Interval;
import io.dsc.model.Quantity;
import io.dsc.model.ScanDirection;
import io.dsc.model.Segment;
import io.dsc.model.SignConvention;
import io.dsc.model.SourceKind;
import io.dsc.model.SourceRecord;
import io.dsc.model.StableId;
import io.dsc.model.TransitionResult;
import io.dsc.model.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * Folds the append-only event log into a CaseState. Time travel is simply
 * folding a prefix: historical views replay stored derivations instead of
 * recomputing with the newest rules, so old conclusions are never overwritten.
 */
public final class Materializer {

    private final ObjectMapper mapper = new ObjectMapper();

    public CaseState fold(String caseId, String branchId, List<Event> events) {
        return foldUntil(caseId, branchId, events, Long.MAX_VALUE);
    }

    public CaseState foldUntil(String caseId, String branchId, List<Event> events, long untilSeq) {
        CaseState s = new CaseState();
        s.branchId = branchId;
        for (Event ev : events) {
            if (ev.seq() > untilSeq) break;
            apply(s, ev);
            s.headSeq = ev.seq();
            s.headHash = ev.hash();
        }
        if (s.metadata == null) {
            s.metadata = new CaseMetadata(caseId, caseId, "?", "?", "?", List.of());
        }
        return s;
    }

    private void apply(CaseState s, Event ev) {
        ObjectNode p = ev.payload();
        switch (ev.type()) {
            case CASE_CREATED -> applyCaseCreated(s, ev, p);
            case SOURCE_ARCHIVED, SOURCE_AMENDED -> applySource(s, ev, p);
            case INTERVAL_DRAWN -> applyInterval(s, ev, p);
            case BASELINES_COMPUTED -> applyBaselines(s, ev, p);
            case BASELINE_ADOPTED -> applyBaselineDecision(s, p, true, null);
            case BASELINE_REJECTED -> applyBaselineDecision(s, p, false,
                    p.path("reason").asText(null));
            case TRANSITION_COMPUTED -> applyTransition(s, ev, p);
            case TRANSITION_ACCEPTED -> applyTransitionDecision(s, p, true, null);
            case TRANSITION_REJECTED -> applyTransitionDecision(s, p, false,
                    p.path("reason").asText(null));
            case MASS_REVISED -> applyMassRevision(s, ev, p);
            case PARAMS_REVISED -> applyParamsRevision(s, ev, p);
            case NOTE -> { /* narrative only */ }
            default -> { /* branch/replay handled by callers */ }
        }
        JsonNode diags = p.get("diagnostics");
        if (diags != null && diags.isArray()) {
            for (JsonNode d : diags) addDiagnostic(s, d, ev.eventId());
        }
    }

    private void applyCaseCreated(CaseState s, Event ev, ObjectNode p) {
        List<String> tags = new ArrayList<>();
        p.path("tags").forEach(t -> tags.add(t.asText()));
        s.metadata = new CaseMetadata(p.get("caseId").asText(), p.get("title").asText(),
                p.get("material").asText(), p.get("createdAt").asText(),
                p.path("ruleVersion").asText("?"), tags);
    }

    private void applySource(CaseState s, Event ev, ObjectNode p) {
        String kindCode = p.get("kind").asText();
        SourceKind kind = SourceKind.valueOf(kindCode);
        String segmentId = p.path("segmentId").asText(null);
        if (kind == SourceKind.INSTRUMENT_OUTPUT) {
            upsertSegment(s, ev, p);
        }
        SourceRecord rec = new SourceRecord(
                StableId.parse(p.get("sourceId").asText()),
                s.metadata == null ? p.path("caseId").asText("?") : s.metadata.caseId(),
                kind, segmentId, p.get("version").asInt(),
                p.hasNonNull("supersedesId") ? StableId.parse(p.get("supersedesId").asText()) : null,
                p.path("summary").asText(""),
                p.get("bodyRef").asText(),
                ev.occurredAt());
        s.putSource(rec);
    }
