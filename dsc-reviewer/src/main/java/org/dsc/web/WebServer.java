package org.dsc.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.dsc.engine.CaseService;
import org.dsc.engine.Replay;
import org.dsc.engine.ViewQuery;
import org.dsc.domain.Models.TempWindow;
import org.dsc.domain.Models.TangentSelection;
import org.dsc.store.Archive;
import org.dsc.store.Store;

import java.nio.file.Path;

/**
 * HTTP 层。所有读接口返回稳定身份（id/version/createdEvent），
 * 时间旅行通过 ?at=eventId 恢复历史可见结论。
 */
public final class WebServer {
    private final Store store;
    private final CaseService service;
    private final ViewQuery view;

    public WebServer(Store store) {
        this.store = store;
        this.service = new CaseService(store);
        this.view = new ViewQuery(store);
    }

    public Javalin start(String host, int port) {
        Javalin app = Javalin.create(cfg -> cfg.showJavalinBanner = false);
        app.get("/", ctx -> ctx.redirect("/index.html"));
        app.get("/api/cases", this::listCases);
        app.post("/api/replay", this::replay);
        app.get("/api/case/{caseId}", this::caseView);
        app.post("/api/case/{caseId}/empty", this::emptyCheck);
        app.post("/api/case/{caseId}/tangent", this::adjustTangent);
        app.post("/api/case/{caseId}/baseline/{baselineId}/adopt", this::adopt);
        app.post("/api/case/{caseId}/baseline/{baselineId}/reject", this::reject);
        app.get("/api/result/{resultId}/provenance", this::provenance);
        app.get("/api/case/{caseId}/svg/{seqNo}", this::svg);
        app.get("/api/export", this::exportDb);
        app.post("/api/import", this::importDb);
        app.get("/api/head/{caseId}", this::head);

        cfgStatic(app);
        app.start(host, port);
        return app;
    }

    private void cfgStatic(Javalin app) {
        java.util.function.Function<String, String> read = name -> {
            try (var in = getClass().getResourceAsStream("/web/" + name)) {
                if (in == null) return null;
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) { return null; }
        };
        app.get("/index.html", ctx -> html(ctx, read.apply("index.html"), "text/html"));
        app.get("/app.js", ctx -> html(ctx, read.apply("app.js"), "application/javascript"));
        app.get("/app.css", ctx -> html(ctx, read.apply("app.css"), "text/css"));
    }

    private void html(Context ctx, String body, String type) {
        if (body == null) { ctx.status(404); return; }
        ctx.contentType(type + ";charset=utf-8").result(body);
    }

    private void listCases(Context ctx) throws Exception {
        ctx.json(store.caseIds());
    }

    private void replay(Context ctx) {
        boolean existed;
        try { existed = store.caseIds().contains(Replay.CASE_ID); }
        catch (Exception e) { existed = false; }
        if (existed) {
            ctx.json(EventRecord2.replayInfo("already-present",
                    "案例已存在；如需重放请删除数据库或导入新库"));
            return;
        }
        Replay replay = new Replay(new CaseService(store));
        var steps = replay.run();
        ObjectNode out = org.dsc.store.EventRecord.MAPPER.createObjectNode();
        out.put("caseId", Replay.CASE_ID);
        var arr = out.putArray("steps");
        steps.forEach(st -> {
            var o = arr.addObject();
            o.put("title", st.title());
            o.put("eventId", st.eventId());
            o.put("judgement", st.judgement());
        });
        ctx.json(out);
    }

    private void caseView(Context ctx) throws Exception {
        String at = ctx.queryParam("at");
        ctx.json(view.caseView(ctx.pathParam("caseId"), at));
    }

    private void emptyCheck(Context ctx) throws Exception {
        JsonNode b = body(ctx);
        int seq = b.get("seq").asInt();
        TempWindow w = window(b.get("window"));
        String basis = textOr(b, "basis", null);
        ctx.json(service.checkEmptyWindow(ctx.pathParam("caseId"), seq, w,
                textOr(b, "actor", "web"), basis).payload());
    }

    private void adjustTangent(Context ctx) throws Exception {
        JsonNode b = body(ctx);
        int seq = b.get("seq").asInt();
        TangentSelection tan = new TangentSelection(b.get("segmentId").asText(),
                window(b.get("left")), window(b.get("right")),
                textOr(b, "rationale", "web adjustment"), 2);
        ctx.json(service.adjustTangent(ctx.pathParam("caseId"), seq, tan,
                textOr(b, "actor", "web"), textOr(b, "basis", null)).payload());
    }

    private void adopt(Context ctx) throws Exception {
        JsonNode b = body(ctx);
        ctx.json(service.adoptBaseline(ctx.pathParam("caseId"),
                ctx.pathParam("baselineId"), textOr(b, "actor", "web"),
                textOr(b, "basis", null)).payload());
    }

    private void reject(Context ctx) throws Exception {
        JsonNode b = body(ctx);
        ctx.json(service.rejectBaseline(ctx.pathParam("caseId"),
                ctx.pathParam("baselineId"), textOr(b, "actor", "web"),
                textOr(b, "basis", null)).payload());
    }

    private void provenance(Context ctx) {
        String at = ctx.queryParam("at");
        ctx.json(view.provenance(ctx.pathParam("resultId"), at));
    }

    private void svg(Context ctx) {
        int seq = Integer.parseInt(ctx.pathParam("seqNo"));
        String at = ctx.queryParam("at");
        ctx.contentType("image/svg+xml").result(
                SvgChart.chart(view.caseView(Replay.CASE_ID, at), seq));
    }

    private void exportDb(Context ctx) {
        ctx.contentType("application/json").result(Archive.toJson(Archive.exportAll(store)));
    }

    private void importDb(Context ctx) {
        JsonNode root = Archive.parseJson(ctx.body());
        Archive.importAll(store, root);
        ctx.result("{\"imported\":true}");
    }

    private void head(Context ctx) throws Exception {
        var o = org.dsc.store.EventRecord.MAPPER.createObjectNode();
        o.put("head", String.valueOf(store.headEventId(ctx.pathParam("caseId"))));
        ctx.json(o);
    }

    private static JsonNode body(Context ctx) throws Exception {
        return org.dsc.store.EventRecord.MAPPER.readTree(ctx.body());
    }

    private static TempWindow window(JsonNode n) {
        return new TempWindow(n.get("startK").asDouble(), n.get("endK").asDouble());
    }

    private static String textOr(JsonNode n, String k, String dflt) {
        JsonNode v = n.get(k);
        return v == null || v.isNull() ? dflt : v.asText();
    }

    private static final class EventRecord2 {
        static ObjectNode replayInfo(String k, String v) {
            ObjectNode o = org.dsc.store.EventRecord.MAPPER.createObjectNode();
            o.put(k, v);
            return o;
        }
    }
}
