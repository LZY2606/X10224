package dsc.web;

import com.fasterxml.jackson.databind.JsonNode;
import dsc.store.Json;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;

/** 审阅器 REST API。曲线页所有操作（拖动/选基线/接受否定/回放/导入导出）都经此留事件。 */
public final class WebApi {
    private WebApi() {
    }

    public static void routes(Javalin app, DscService svc) {
        app.get("/api/state", ctx -> {
            ensureSeeded(svc);
            ctx.json(svc.engine().currentState());
        });

        app.get("/api/status", ctx -> ctx.json(svc.status()));

        app.post("/api/replay", ctx -> {
            ensureSeeded(svc);
            svc.engine().replayAllBeats();
            ctx.json(svc.engine().currentState());
        });

        app.post("/api/beats/rule-revision", ctx -> {
            ensureSeeded(svc);
            svc.engine().reviseRuleParameters(bodyAuthor(ctx));
            ctx.json(svc.engine().currentState());
        });

        app.post("/api/beats/mass-revision", ctx -> {
            ensureSeeded(svc);
            JsonNode b = Json.MAPPER.readTree(ctx.body());
            svc.engine().reviseMass(
                    b.path("value").asText("8.37"),
                    b.path("unit").asText("mg"),
                    b.path("originalRep").asText("复核称量单 #B-042R：8.37 mg"),
                    b.path("author").asText("web-reviewer"));
            ctx.json(svc.engine().currentState());
        });

        app.post("/api/beats/local-source-change", ctx -> {
            ensureSeeded(svc);
            JsonNode b = Json.MAPPER.readTree(ctx.body());
            svc.engine().changeAtmosphere(
                    b.path("segmentId").asText("seg2"),
                    b.path("flowRaw").asText("45"),
                    b.path("author").asText("web-reviewer"));
            ctx.json(svc.engine().currentState());
        });

        app.post("/api/intervals/adjust", ctx -> {
            ensureSeeded(svc);
            JsonNode b = Json.MAPPER.readTree(ctx.body());
            svc.engine().adjustInterval(
                    b.get("intervalId").asText(),
                    b.get("segmentId").asText(),
                    b.get("t0K").asDouble(),
                    b.get("t1K").asDouble(),
                    b.path("author").asText("web-reviewer"),
                    b.path("baseRevision").asLong(-1));
            ctx.json(svc.engine().currentState());
        });

        app.post("/api/decisions", ctx -> {
            ensureSeeded(svc);
            JsonNode b = Json.MAPPER.readTree(ctx.body());
            svc.engine().decideBaseline(
                    b.get("intervalId").asText(),
                    b.get("candidateId").asText(),
                    b.path("action").asText("ACCEPT"),
                    b.path("author").asText("web-reviewer"),
                    b.path("baseRevision").asLong(-1));
            ctx.json(svc.engine().currentState());
        });

        app.post("/api/beats/conflict", ctx -> {
            ensureSeeded(svc);
            JsonNode b = Json.MAPPER.readTree(ctx.body());
            var info = svc.engine().conflictingDecisions(
                    b.path("intervalId").asText("interval-melt"),
                    b.path("candidateA").asText(""),
                    b.path("candidateB").asText(""));
            ctx.json(Map.of("conflict", info, "state", svc.engine().currentState()));
        });

        app.get("/api/events/{seq}/asof", ctx -> {
            ensureSeeded(svc);
            long seq = Long.parseLong(ctx.pathParam("seq"));
            ctx.json(svc.engine().asOfEvent(seq));
        });

        app.get("/api/export", ctx -> {
            ensureSeeded(svc);
            ctx.contentType("application/json");
            ctx.header("Content-Disposition",
                    "attachment; filename=\"dsc-transition-snapshot.json\"");
            ctx.result(svc.engine().exportSnapshot());
        });

        app.post("/api/import", ctx -> {
            String body = ctx.body();
            svc.wipeAndImportSnapshot(body);
            ctx.json(svc.engine().currentState());
        });

        app.post("/api/reseed", ctx -> {
            svc.wipeAndReseed();
            ctx.json(svc.engine().currentState());
        });
    }

    private static void ensureSeeded(DscService svc) throws Exception {
        if (!svc.seeded()) {
            svc.seedFromCasePack(null);
        }
    }

    private static String bodyAuthor(Context ctx) throws Exception {
        if (ctx.body() == null || ctx.body().isBlank()) {
            return "web-reviewer";
        }
        JsonNode b = Json.MAPPER.readTree(ctx.body());
        return b.path("author").asText("web-reviewer");
    }
}
