package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import dsc.core.*;

import java.util.ArrayList;
import java.util.List;

/** 事件 -> 状态投影。纯函数式折叠，支持重放到任意 seq。 */
public final class Projector {

    private Projector() {}

    public static State project(List<EventStore.Event> events) {
        State s = new State();
        for (EventStore.Event e : events) apply(s, e);
        return s;
    }

    public static void apply(State s, EventStore.Event e) {
        JsonNode p = e.payload();
        switch (e.type()) {
            case EventTypes.CASEPACK_LOADED -> s.loadedCasePacks.add(p.get("packId").asText());
            case EventTypes.SAMPLE_CREATED -> s.samples.put(p.get("sampleId").asText(),
                    new Models.Sample(p.get("sampleId").asText(), p.get("name").asText()));
            case EventTypes.SEGMENT_IMPORTED -> {
                List<Point> pts = new ArrayList<>();
                for (JsonNode pt : p.get("points")) {
                    pts.add(new Point(pt.get("t").asDouble(), pt.get("hf").asDouble()));
                }
                Models.Segment seg = new Models.Segment(
                        p.get("segmentId").asText(), p.get("sampleId").asText(),
                        p.get("label").asText(), p.get("cycleIndex").asInt(),
                        Direction.valueOf(p.get("direction").asText()),
                        EndoConvention.valueOf(p.get("convention").asText()),
                        p.get("atmosphere").asText(),
                        p.hasNonNull("rateKPerMin") ? p.get("rateKPerMin").asDouble() : null,
                        p.get("tempUnit").asText(), p.get("heatFlowUnit").asText(),
                        p.get("rawFileRef").asText(), pts);
                s.segments.put(seg.id(), seg);
            }
            case EventTypes.MASS_REVISED -> {
                Models.MassVersion mv = new Models.MassVersion(
                        p.get("massId").asText(), p.get("sampleId").asText(),
                        p.get("version").asLong(), p.get("massMg").asDouble(),
                        p.path("note").asText(""));
                s.masses.computeIfAbsent(mv.sampleId(), k -> new ArrayList<>()).add(mv);
            }
            case EventTypes.CALIBRATION_RECORDED -> {
                Models.Calibration c = new Models.Calibration(
                        p.get("calId").asText(), p.get("sampleId").asText(),
                        p.get("version").asLong(), p.get("reference").asText(),
                        p.get("offsetC").asDouble(), p.get("slope").asDouble());
                s.calibrations.put(c.sampleId(), c);
            }
            case EventTypes.TANGENT_SELECTED -> {
                List<Point> pts = new ArrayList<>();
                for (JsonNode pt : p.get("points")) {
                    pts.add(new Point(pt.get("t").asDouble(), pt.get("hf").asDouble()));
                }
                Models.Tangent t = new Models.Tangent(
                        p.get("tangentId").asText(), p.get("segmentId").asText(),
                        p.get("side").asText(), pts, p.path("user").asText(""));
                s.tangents.put(t.id(), t);
            }
            case EventTypes.BASELINE_PROPOSED -> {
                Models.BaselineCandidate b = new Models.BaselineCandidate(
                        p.get("baselineId").asText(), p.get("segmentId").asText(),
                        TransitionKind.valueOf(p.get("kind").asText()),
                        BaselineType.valueOf(p.get("type").asText()),
                        baselineFromJson(p.get("baseline")),
                        readStringList(p.get("tangentIds")),
                        p.get("windowLoC").asDouble(), p.get("windowHiC").asDouble(),
                        false, false);
                s.baselines.put(b.id(), b);
            }
            case EventTypes.BASELINE_ADOPTED -> {
                String id = p.get("baselineId").asText();
                Models.BaselineCandidate b = s.baselines.get(id);
                if (b != null) {
                    // 同一 (segment, kind) 下旧候选退位但保留，绝不删除
                    for (Models.BaselineCandidate other : new ArrayList<>(s.baselines.values())) {
                        if (other.segmentId().equals(b.segmentId()) && other.kind() == b.kind()
                                && other.adopted() && !other.id().equals(id)) {
                            s.baselines.put(other.id(), unadopt(other));
                        }
                    }
                    s.baselines.put(id, new Models.BaselineCandidate(b.id(), b.segmentId(), b.kind(),
                            b.type(), b.baseline(), b.tangentIds(), b.windowLoC(), b.windowHiC(),
                            true, b.rejected()));
                }
            }
            case EventTypes.ANALYSIS_PUBLISHED -> {
                String subject = p.get("subject").asText();
                s.publishedAnalysis.put(subject, p.get("derivedId").asText());
                s.invalidated.remove(subject);
            }
            case EventTypes.ANALYSIS_INVALIDATED -> s.invalidated.put(
                    p.get("subject").asText(), p.get("reason").asText());
            case EventTypes.DECISION_MADE -> s.decisions.add(new Models.Decision(
                    p.get("decisionId").asText(), p.get("kind").asText(),
                    p.get("targetType").asText(), p.get("targetId").asText(),
                    p.path("note").asText(""), e.actor()));
            case EventTypes.DIAGNOSTIC_RAISED -> s.diagnostics.add(new Models.Diagnostic(
                    p.get("diagId").asText(), p.get("code").asText(),
                    p.get("subject").asText(), p.get("message").asText(),
                    p.path("severity").asText("info")));
            case EventTypes.CONFLICT_RAISED -> s.conflicts.put(p.get("conflictId").asText(),
                    new Models.ConflictRec(p.get("conflictId").asText(), p.get("subject").asText(),
                            p.get("baseSeq").asLong(), p.get("eventA").asText(), p.get("eventB").asText(),
                            "open", null));
            case EventTypes.CONFLICT_RESOLVED -> {
                Models.ConflictRec c = s.conflicts.get(p.get("conflictId").asText());
                if (c != null) {
                    s.conflicts.put(c.id(), new Models.ConflictRec(c.id(), c.subject(), c.baseSeq(),
                            c.eventA(), c.eventB(), "resolved", e.id()));
                }
            }
            default -> { /* 未知事件类型向前兼容：忽略但保留在事件流中 */ }
        }
    }

    private static Models.BaselineCandidate unadopt(Models.BaselineCandidate b) {
        return new Models.BaselineCandidate(b.id(), b.segmentId(), b.kind(), b.type(), b.baseline(),
                b.tangentIds(), b.windowLoC(), b.windowHiC(), false, b.rejected());
    }

    static Baseline baselineFromJson(JsonNode n) {
        BaselineType type = BaselineType.valueOf(n.get("type").asText());
        if (type == BaselineType.LINEAR) {
            return Baseline.linear(n.get("a").asDouble(), n.get("b").asDouble());
        }
        return Baseline.sigmoidal(n.get("leftA").asDouble(), n.get("leftB").asDouble(),
                n.get("rightA").asDouble(), n.get("rightB").asDouble(),
                n.get("k").asDouble(), n.get("tMid").asDouble());
    }

    static com.fasterxml.jackson.databind.node.ObjectNode baselineToJson(Baseline b) {
        var n = EventStore.obj();
        n.put("type", b.type().name());
        n.put("a", b.a()); n.put("b", b.b());
        n.put("k", b.k()); n.put("tMid", b.tMid());
        n.put("leftA", b.leftA()); n.put("leftB", b.leftB());
        n.put("rightA", b.rightA()); n.put("rightB", b.rightB());
        return n;
    }

    private static List<String> readStringList(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n != null) for (JsonNode x : n) out.add(x.asText());
        return out;
    }
}
