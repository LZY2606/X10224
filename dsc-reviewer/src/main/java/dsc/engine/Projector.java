package dsc.engine;

import com.fasterxml.jackson.databind.JsonNode;
import dsc.event.DscEvent;
import dsc.event.EventTypes;
import dsc.model.Quant;
import dsc.model.RawPoint;
import dsc.model.SegmentDef;
import dsc.model.SourceMeta;
import dsc.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Folds the ordered event log into an immutable-at-one-seq {@link SampleState}. */
public final class Projector {

    public SampleState fold(List<DscEvent> events) {
        SampleState state = new SampleState();
        for (DscEvent event : events) {
            apply(state, event);
        }
        state.seq = events.isEmpty() ? 0 : events.get(events.size() - 1).seq();
        return state;
    }

    private void apply(SampleState state, DscEvent e) {
        JsonNode p = e.payload();
        switch (e.type()) {
            case EventTypes.CASE_OPENED -> {
                state.caseId = text(p, "caseId");
                state.title = text(p, "title");
            }
            case EventTypes.SOURCE_ARCHIVED -> {
                List<String> notes = new ArrayList<>();
                if (p.has("notes")) p.get("notes").forEach(n -> notes.add(n.asText()));
                state.sources.add(new SourceMeta(text(p, "sourceId"), text(p, "kind"),
                        text(p, "origin"), text(p, "checksum"), e.createdAt(),
                        text(p, "rawExcerpt"), notes));
            }
            case EventTypes.SEGMENT_IMPORTED -> {
                SegmentDef def = Json.MAPPER.convertValue(p.get("segment"), SegmentDef.class);
                List<SampleState.SegmentVersion> versions =
                        state.segments.computeIfAbsent(def.segmentId(), k -> new ArrayList<>());
                versions.add(new SampleState.SegmentVersion(e.id(), "IMPORT",
                        versions.size() + 1, e.id(), def, text(p, "reason")));
            }
            case EventTypes.SOURCE_PATCHED -> {
                String segmentId = text(p, "segmentId");
                List<SampleState.SegmentVersion> versions = state.segments.get(segmentId);
                SampleState.SegmentVersion current = versions.get(versions.size() - 1);
                List<RawPoint> rows = new ArrayList<>(current.def.points());
                List<RawPoint> patchedRows = Json.MAPPER.convertValue(
                        p.get("points"), Json.MAPPER.getTypeFactory()
                                .constructCollectionType(List.class, RawPoint.class));
                List<Integer> touched = new ArrayList<>();
                for (RawPoint rp : patchedRows) {
                    rows.set(rp.rowIndex(), rp);
                    touched.add(rp.rowIndex());
                }
                SegmentDef old = current.def;
                SegmentDef updated = new SegmentDef(old.segmentId(), old.cycleIndex(),
                        old.direction(), old.declaredRate(), old.hfConvention(),
                        old.tempUnit(), old.hfUnit(), rows);
                versions.add(new SampleState.SegmentVersion(e.id(), "PATCH",
                        versions.size() + 1, current.eventId, updated,
                        text(p, "reason") + " rows=" + touched));
            }
            case EventTypes.MASS_SET -> state.massVersions.add(new SampleState.MassVersion(
                    e.id(), state.massVersions.size() + 1,
                    Json.MAPPER.convertValue(p.get("mass"), Quant.class),
                    p.has("balanceUncertainty")
                            ? Json.MAPPER.convertValue(p.get("balanceUncertainty"), Quant.class)
                            : null,
                    text(p, "sourceId")));
            case EventTypes.RATE_REVISED -> {
                String segmentId = text(p, "segmentId");
                List<SampleState.RateVersion> list =
                        state.rates.computeIfAbsent(segmentId, k -> new ArrayList<>());
                list.add(new SampleState.RateVersion(e.id(), list.size() + 1,
                        Json.MAPPER.convertValue(p.get("rate"), Quant.class),
                        textOr(p, "origin", "MANUAL")));
            }
            case EventTypes.ATMOSPHERE_SET -> state.atmosphereVersions.add(
                    new SampleState.AtmosphereRecord(e.id(), text(p, "gas"),
                            p.has("flow") ? Json.MAPPER.convertValue(p.get("flow"), Quant.class) : null,
                            text(p, "sourceId")));
            case EventTypes.CALIBRATION_SET -> state.calibrationVersions.add(
                    new SampleState.CalibrationVersion(e.id(),
                            state.calibrationVersions.size() + 1,
                            Json.MAPPER.convertValue(p.get("offset"), Quant.class),
                            p.has("uncertainty")
                                    ? Json.MAPPER.convertValue(p.get("uncertainty"), Quant.class)
                                    : null,
                            text(p, "sourceId")));
            case EventTypes.INTERVAL_DRAGGED -> {
                String intervalId = text(p, "intervalId");
                List<SampleState.IntervalVersion> list =
                        state.intervals.computeIfAbsent(intervalId, k -> new ArrayList<>());
                list.add(new SampleState.IntervalVersion(e.id(), intervalId,
                        text(p, "segmentId"), p.get("t0").asDouble(),
                        p.get("t1").asDouble(), textOr(p, "kind", "PEAK")));
            }
            case EventTypes.TANGENT_PICKED -> {
                String intervalId = text(p, "intervalId");
                String side = text(p, "side");
                Map<String, List<SampleState.TangentVersion>> bySide =
                        state.tangents.computeIfAbsent(intervalId, k -> new LinkedHashMap<>());
                List<SampleState.TangentVersion> list =
                        bySide.computeIfAbsent(side, k -> new ArrayList<>());
                list.add(new SampleState.TangentVersion(e.id(), intervalId, side,
                        p.get("anchorT0").asDouble(), p.get("anchorT1").asDouble()));
            }
            case EventTypes.BASELINE_DECIDED -> {
                String intervalId = text(p, "intervalId");
                String method = text(p, "method");
                Map<String, SampleState.BaselineVerdict> byMethod = state.decisions
                        .computeIfAbsent(intervalId, k -> new LinkedHashMap<>());
                byMethod.put(method, new SampleState.BaselineVerdict(e.id(), method,
                        textOr(p, "verdict", "ACCEPT"), e.actor()));
            }
            case EventTypes.CONFLICT_RESOLVED -> state.conflictResolutions.put(
                    text(p, "conflictId"),
                    new SampleState.ConflictResolution(e.id(), text(p, "conflictId"),
                            text(p, "resolution"), textOr(p, "keptEventId", ""), e.actor()));
            case EventTypes.REVIEW_NOTE -> state.notes.add(e.id() + ": " + text(p, "text"));
            default -> {
                // CONFLICT_DETECTED / DIAGNOSTIC_RAISED are facts with no folded projection
                // (their influence is carried by later resolve/accept events).
            }
        }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : "";
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        return node.hasNonNull(field) ? node.get(field).asText() : fallback;
    }
}
