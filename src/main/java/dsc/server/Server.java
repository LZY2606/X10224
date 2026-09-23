package dsc.server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dsc.casepack.CasePack;
import dsc.derive.Engine;
import dsc.derive.Workspace;
import dsc.store.Store;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class Server {
    private static final Gson GSON = new Gson();
    private final Store store;
    private final Workspace ws;

    public Server(Store store) {
        this.store = store;
        this.ws = new Workspace(store, new Engine(store));
    }

    public Javalin app() {
        Javalin app = Javalin.create();
        app.get("/", ctx -> ctx.html(web("index.html")));
        app.get("/app.js", ctx -> ctx.contentType("text/javascript").result(web("app.js")));
        app.get("/style.css", ctx -> ctx.contentType("text/css").result(web("style.css")));

        app.get("/api/samples", ctx -> json(ctx, store.samples()));
        app.post("/api/samples", ctx -> {
            Map<String, Object> body = body(ctx);
            String id = Store.newId("s");
            store.createSample(id, String.valueOf(body.getOrDefault("name", "样品")));
            json(ctx, Map.of("id", id));
        });

        // 90 秒样例路径：建样 -> 导入案例包 -> 场景重放
        app.post("/api/demo", ctx -> {
            String id = Store.newId("s");
            store.createSample(id, "热转折案演示样品");
            List<Map<String, Object>> log = new ArrayList<>(CasePack.importAll(ws, id));
            log.addAll(CasePack.runScenario(ws, id));
            json(ctx, Map.of("sampleId", id, "log", log));
        });

        app.post("/api/samples/{id}/import", ctx -> json(ctx, CasePack.importAll(ws, ctx.pathParam("id"))));
        app.post("/api/samples/{id}/scenario", ctx -> json(ctx, CasePack.runScenario(ws, ctx.pathParam("id"))));

        app.get("/api/samples/{id}/state", ctx -> json(ctx, state(ctx.pathParam("id"))));

        app.post("/api/samples/{id}/windows", ctx -> {
            Map<String, Object> b = body(ctx);
            json(ctx, ws.createWindow(ctx.pathParam("id"),
                    String.valueOf(b.get("run")),
                    ((Number) b.get("segment")).intValue(),
                    ((Number) b.get("t1")).doubleValue(),
                    ((Number) b.get("t2")).doubleValue()));
        });

        app.post("/api/samples/{id}/decisions/{win}", ctx -> {
            Map<String, Object> b = body(ctx);
            Store.Version v = ws.decide(ctx.pathParam("id"), ctx.pathParam("win"),
                    String.valueOf(b.get("candidate")), String.valueOf(b.get("action")),
                    String.valueOf(b.getOrDefault("user", "匿名")),
                    b.get("baseVersionId") == null ? null : String.valueOf(b.get("baseVersionId")));
            json(ctx, v);
        });

        app.post("/api/samples/{id}/decisions/{win}/resolve", ctx -> {
            Map<String, Object> b = body(ctx);
            Store.Version cur = store.current(ctx.pathParam("id"), Engine.DEC_BASELINE, ctx.pathParam("win"))
                    .orElseThrow();
            @SuppressWarnings("unchecked")
            Map<String, Object> myPayload = (Map<String, Object>) b.get("myPayload");
            json(ctx, ws.resolveKeepBoth(ctx.pathParam("id"), ctx.pathParam("win"), cur, myPayload,
                    String.valueOf(b.getOrDefault("userA", "甲")), String.valueOf(b.getOrDefault("userB", "乙"))));
        });

        app.put("/api/samples/{id}/sources/{category}/{key}", ctx -> {
            Map<String, Object> b = body(ctx);
            String category = ctx.pathParam("category").replace('_', ':');
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) b.get("payload");
            json(ctx, ws.reviseSource(ctx.pathParam("id"), category, ctx.pathParam("key"), payload,
                    b.get("baseVersionId") == null ? null : String.valueOf(b.get("baseVersionId"))));
        });

        app.post("/api/samples/{id}/recompute", ctx -> json(ctx, ws.engine().deriveAll(ctx.pathParam("id"))));

        app.get("/api/samples/{id}/explain", ctx -> json(ctx, ws.engine().explain(ctx.pathParam("id"),
                ctx.queryParam("category"), ctx.queryParam("key"))));

        app.get("/api/samples/{id}/events", ctx -> json(ctx, store.events(ctx.pathParam("id"))));

        app.get("/api/samples/{id}/replay/{seq}", ctx -> {
            Map<String, Store.Version> visible = store.visibleAt(ctx.pathParam("id"),
                    Long.parseLong(ctx.pathParam("seq")));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("seq", Long.parseLong(ctx.pathParam("seq")));
            out.put("visible", visible);
            json(ctx, out);
        });

        app.get("/api/samples/{id}/provenance/{versionId}",
                ctx -> json(ctx, store.provenance(ctx.pathParam("versionId"))));

        app.get("/api/samples/{id}/history", ctx -> json(ctx, store.history(ctx.pathParam("id"),
                ctx.queryParam("category"), ctx.queryParam("key"))));

        app.exception(Store.Conflict.class, (e, ctx) -> {
            ctx.status(409);
            json(ctx, Map.of("error", "CONFLICT", "current", e.current));
        });
        app.exception(NoSuchElementException.class, (e, ctx) -> {
            ctx.status(404);
            json(ctx, Map.of("error", "NOT_FOUND"));
        });
        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            ctx.status(400);
            json(ctx, Map.of("error", e.getMessage()));
        });
        return app;
    }

    private Map<String, Object> state(String sample) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sample", sample);
        List<Map<String, Object>> current = new ArrayList<>();
        List<Map<String, Object>> diagnostics = new ArrayList<>();
        for (Store.Version v : store.currentVersions(sample)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.id());
            m.put("category", v.category());
            m.put("key", v.key());
            m.put("version", v.version());
            m.put("deps", v.deps());
            m.put("payload", v.payload());
            current.add(m);
            Object diags = v.payload().get("diagnostics");
            if (diags instanceof List<?> dl) {
                for (Object d : dl) {
                    if (d instanceof Map<?, ?> dm) {
                        Map<String, Object> dd = new LinkedHashMap<>((Map<String, Object>) dm);
                        dd.put("originVersionId", v.id());
                        dd.put("originCategory", v.category());
                        dd.put("originKey", v.key());
                        diagnostics.add(dd);
                    }
                }
            }
        }
        out.put("current", current);
        out.put("diagnostics", diagnostics);
        out.put("events", store.events(sample));
        return out;
    }

    private static String web(String name) {
        try (InputStream in = Server.class.getResourceAsStream("/web/" + name)) {
            if (in == null) throw new NoSuchElementException("web resource " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> body(Context ctx) {
        String s = ctx.body();
        if (s == null || s.isBlank()) return Map.of();
        return GSON.fromJson(s, new TypeToken<Map<String, Object>>() {}.getType());
    }

    private static void json(Context ctx, Object o) {
        ctx.contentType("application/json; charset=utf-8").result(GSON.toJson(o));
    }
}
