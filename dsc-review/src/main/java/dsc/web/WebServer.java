package dsc.web;

import com.fasterxml.jackson.databind.JsonNode;
import dsc.casepack.CaseBootstrap;
import dsc.casepack.CasePack;
import dsc.events.HistoryState;
import dsc.model.TangentSelection;
import dsc.model.TraceImport;
import dsc.service.*;
import dsc.storage.ConflictException;
import dsc.storage.Database;
import dsc.storage.Json;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Javalin HTTP layer. All mutations go through {@link DscService} as atomic
 * batches; concurrent edits from the same base seq return HTTP 409 with the
 * resumable conflict details.
 */
public final class WebServer {

    private final Database db;
    private final DscService service;
    private final DscServiceDerive derive;
    private final CasePack pack;
    private final Path fixtureDir;

    public WebServer(Database db, CasePack pack, Path fixtureDir) {
        this.db = db;
        this.pack = pack;
        this.fixtureDir = fixtureDir;
        this.service = pack.service();
        this.derive = pack.deriveService();
    }

    public Javalin start(String host, int port) {
        Javalin app = Javalin.create(cfg ->
                cfg.staticFiles.add(staticFiles -> staticFiles.directory = "/static"));

        app.get("/api/history", ctx -> {
            String at = ctx.queryParam("atSeq");
            Long seq = at == null || at.isBlank() ? null : Long.parseLong(at);
            long head = head();
            HistoryState state = (seq == null || seq >= head) ? service.state(hid()) : service.stateAt(hid(), seq);
            ctx.json(ApiViews.history(state, seq == null ? head : Math.min(seq, head)));
        });

        app.get("/api/story", ctx -> ctx.json(Map.of(
                "historyId", CasePack.HISTORY_ID,
                "name", CasePack.HISTORY_NAME,
                "steps", pack.story)));

        app.get("/api/validity/{id}", ctx ->
                ctx.json(service.validity(hid(), ctx.pathParam("id"))));

        app.post("/api/regions", ctx -> {
            JsonNode b = Json.MAPPER.readTree(ctx.bodyInputStream());
            var events = service.defineRegion(hid(), new Commands.DefineRegion(
                    text(b, "actor", "reviewer"), text(b, "segmentId", ""),
                    text(b, "kind", "generic"), b.path("tLo").asDouble(), b.path("tHi").asDouble(),
                    text(b, "label", "")));
            ctx.status(HttpStatus.CREATED).json(Map.of("eventSeqs", seqs(events)));
        });

        app.post("/api/tangents", ctx -> {
            JsonNode b = Json.MAPPER.readTree(ctx.bodyInputStream());
            var events = service.selectTangent(hid(), new Commands.SelectTangent(
                    text(b, "actor", "reviewer"), b.path("regionId").asText(),
                    b.path("leftLo").asDouble(), b.path("leftHi").asDouble(),
                    b.path("rightLo").asDouble(), b.path("rightHi").asDouble(),
                    text(b, "mode", TangentSelection.MODE_TWO_SIDED),
                    text(b, "note", "")));
            ctx.status(HttpStatus.CREATED).json(Map.of("tangentId", events.get(0).getEntityId(),
                    "eventSeq", events.get(0).getSeq()));
        });

        app.post("/api/recompute/{regionId}", ctx -> {
            DscServiceDerive.RecomputeReport r = derive.recompute(hid(), ctx.pathParam("regionId"));
            ctx.json(Map.of(
                    "regionId", r.regionId(),
                    "baselineIds", r.baselineIds(),
                    "featureIds", r.featureIds(),
                    "affectedNodeIds", r.affectedNodeIds(),
                    "unaffectedRegions", r.unaffectedRegions(),
                    "eventSeqs", r.eventSeqs()));
        });

        app.post("/api/accept", ctx -> {
            JsonNode b = Json.MAPPER.readTree(ctx.bodyInputStream());
            var events = service.acceptCandidate(hid(), new Commands.AcceptCandidate(
                    text(b, "actor", "reviewer"), b.path("regionId").asText(),
                    b.path("baselineId").asText(), text(b, "comment", "accepted from curve page")));
            ctx.json(Map.of("eventSeq", events.get(0).getSeq()));
        });

        app.post("/api/reject", ctx -> {
            JsonNode b = Json.MAPPER.readTree(ctx.bodyInputStream());
            var events = service.rejectCandidate(hid(), new Commands.RejectCandidate(
                    "reviewer", b.path("candidateId").asText(), text(b, "reason", "rejected from curve page")));
            ctx.json(Map.of("eventSeq", events.get(0).getSeq()));
        });

        app.post("/api/adjust", ctx -> {
            JsonNode b = Json.MAPPER.readTree(ctx.bodyInputStream());
            DscServiceDerive.RecomputeReport r = derive.adjust(hid(), new Commands.AdjustParameter(
                    text(b, "actor", "reviewer"), b.path("regionId").asText(),
                    b.has("tangentId") && !b.path("tangentId").isNull()
                            ? b.path("tangentId").asText() : null,
                    b.path("leftLo").asDouble(), b.path("leftHi").asDouble(),
                    b.path("rightLo").asDouble(), b.path("rightHi").asDouble(),
                    text(b, "note", "handles moved on curve page")));
            ctx.json(Map.of("eventSeqs", r.eventSeqs(), "baselineIds", r.baselineIds()));
        });

        app.get("/api/export", ctx -> {
            ctx.contentType("application/json");
            ctx.result(Json.MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(dsc.service.ExportImport.exportBundle(
                            pack.store(), hid(), CasePack.HISTORY_NAME)));
        });

        app.exception(ConflictException.class, (e, ctx) -> {
            ctx.status(HttpStatus.CONFLICT);
            ctx.json(Map.of("error", "optimistic-lock-conflict",
                    "expected", e.expected(), "actual", e.actual(),
                    "resolution", "rebase onto actual head and re-apply your decision"));
        });
        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("error", "bad-request", "message", e.getMessage()));
        });

        app.start(host, port);
        return app;
    }

    private long head() { return service.headSeq(hid()); }
    private static String hid() { return CasePack.HISTORY_ID; }
    private static String text(JsonNode n, String k, String dflt) {
        return n != null && n.has(k) && !n.path(k).isNull() ? n.path(k).asText() : dflt;
    }
    private static List<Long> seqs(List<dsc.events.Event> events) {
        return events.stream().map(dsc.events.Event::getSeq).toList();
    }
}
