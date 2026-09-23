package dsc.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.casepack.CasePack;
import dsc.derive.Engine;
import dsc.domain.Curve;
import dsc.domain.Diagnostic;
import dsc.store.VersionStore;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Server {
    private final VersionStore store;
    private final Engine engine;
    private final CasePack casePack;
    private final List<Diagnostic> diags = new ArrayList<>();

    public Server(VersionStore store, Engine engine) {
        this.store = store;
        this.engine = engine;
        this.casePack = new CasePack(store, engine);
    }

    public Javalin create() {
        Javalin app = Javalin.create(cfg -> cfg.showJavalinBanner = false);
        app.get("/", ctx -> ctx.html(Page.HTML));
        app.post("/api/casepack/load", ctx -> {
            diags.clear();
            casePack.importAll(diags);
            ctx.json(ok("imported"));
        });
        app.post("/api/scenario/replay", ctx -> {
            List<JsonNode> steps = casePack.replay(diags);
            ObjectNode n = ok("replayed");
            n.set("steps", VersionStore.M.valueToTree(steps));
            ctx.json(n);
        });
        app.get("/api/state", ctx -> ctx.json(state()));
        app.post("/api/interval", ctx -> {
            JsonNode body = VersionStore.M.readTree(ctx.body());
            reviseInterval(body.get("event").asText(), body.get("tStart").asDouble(), body.get("tEnd").asDouble());
            ctx.json(ok("interval-updated"));
        });
        app.post("/api/baseline/choose", ctx -> {
            JsonNode body = VersionStore.M.readTree(ctx.body());
            chooseBaseline(body.get("event").asText(), body.get("baselineId").asText());
            ctx.json(ok("baseline-chosen"));
        });
        app.post("/api/branch/resolve", ctx -> {
            JsonNode body = VersionStore.M.readTree(ctx.body());
            store.resolveBranch(body.get("id").asText(), body.get("chosen").asText());
            ObjectNode ev = VersionStore.M.createObjectNode();
            ev.put("branch", body.get("id").asText());
            ev.put("chosen", body.get("chosen").asText());
            ev.put("note", "unchosen candidate retained");
            store.event("BRANCH_RESOLVED", ev, 0);
            ctx.json(ok("resolved"));
        });
        app.get("/api/events/{seq}/view", ctx -> {
            long seq = Long.parseLong(ctx.pathParam("seq"));
            ctx.json(historicalView(seq));
        });
        app.exception(Exception.class, (e, ctx) -> {
            ObjectNode n = VersionStore.M.createObjectNode();
            n.put("error", e.getMessage());
            ctx.status(500).json(n);
        });
        return app;
    }

    private static ObjectNode ok(String msg) {
        ObjectNode n = VersionStore.M.createObjectNode();
        n.put("ok", true);
        n.put("message", msg);
        return n;
    }

    private void reviseInterval(String eventId, double tStart, double tEnd) throws SQLException {
        ObjectNode tan = (ObjectNode) store.currentSource("tangents", "user-1").orElseThrow();
        for (JsonNode ev : tan.get("events")) {
            if (ev.get("id").asText().equals(eventId)) {
                ((ObjectNode) ev).put("tStart", tStart);
                ((ObjectNode) ev).put("tEnd", tEnd);
            }
        }
        String txid = store.begin();
        int v = store.stageSource(txid, "tangents", "user-1", tan);
        store.commit(txid, new String[]{"tangents", "user-1", String.valueOf(v)});
        var prop = engine.propagate("tangents", "user-1", diags);
        ObjectNode ev = VersionStore.M.createObjectNode();
        ev.put("event", eventId);
        ev.put("tStart", tStart);
        ev.put("tEnd", tEnd);
        ev.put("status", "ACCEPTED");
        ev.set("affected", VersionStore.M.valueToTree(prop));
        store.event("INTERVAL_DRAG", ev, 0);
    }

    private void chooseBaseline(String eventId, String baselineId) throws SQLException {
        ObjectNode tan = (ObjectNode) store.currentSource("tangents", "user-1").orElseThrow();
        tan.put("chosenBaseline_" + eventId, baselineId);
        String txid = store.begin();
        int v = store.stageSource(txid, "tangents", "user-1", tan);
        store.commit(txid, new String[]{"tangents", "user-1", String.valueOf(v)});
        ObjectNode ev = VersionStore.M.createObjectNode();
        ev.put("event", eventId);
        ev.put("chosenBaseline", baselineId);
        ev.put("status", "ACCEPTED");
        ev.put("kept", "parallel candidates retained");
        store.event("BASELINE_CHOSEN", ev, 0);
    }

    /** Restore the dependency versions and visible conclusions as of an old event. */
    private JsonNode historicalView(long seq) throws SQLException {
        List<JsonNode> events = store.events();
        ObjectNode out = VersionStore.M.createObjectNode();
        ArrayNode evs = out.putArray("eventsUpTo");
        for (JsonNode e : events) {
            if (e.get("seq").asLong() <= seq) evs.add(e);
        }
        out.put("note", "conclusions shown with the dependency versions in force at event " + seq
                + "; history is never overwritten by newer computations");
        out.set("derived", VersionStore.M.valueToTree(store.derivedAll()));
        return out;
    }

    private JsonNode state() throws SQLException {
        ObjectNode out = VersionStore.M.createObjectNode();
        // sources with versions
        ObjectNode src = out.putObject("sources");
        for (String[] k : new String[][]{{"instrument-output", "run-1"}, {"mass", "sample-1"},
                {"scan-rate", "run-1"}, {"atmosphere", "run-1"}, {"calibration", "run-1"},
                {"tangents", "user-1"}}) {
            int v = store.currentVersion(k[0], k[1]);
            if (v > 0) {
                ObjectNode s = src.putObject(k[0]);
                s.put("id", k[1]);
                s.put("version", v);
                store.currentSource(k[0], k[1]).ifPresent(p -> {
                    if (!k[0].equals("instrument-output")) s.set("payload", p);
                    else s.put("rows", p.get("rows").size());
                });
            }
        }
        // curve points for plotting (downsampled)
        store.currentSource("instrument-output", "run-1").ifPresent(run -> {
            List<Diagnostic> tmp = new ArrayList<>();
            Curve c = Engine.parseCurve(run, tmp);
            ArrayNode pts = out.putArray("curve");
            int stride = Math.max(1, c.points.size() / 900);
            for (int i = 0; i < c.points.size(); i += stride) {
                Curve.Pt p = c.points.get(i);
                pts.addArray().add(p.tempC).add(p.heatFlow);
            }
            ArrayNode segs = out.putArray("segments");
            List<Curve.Segment> ss = c.splitSegments();
            for (int i = 0; i < ss.size(); i++) {
                ObjectNode sn = segs.addObject();
                sn.put("index", i);
                sn.put("direction", ss.get(i).direction.name());
                sn.put("tMin", ss.get(i).tMin());
                sn.put("tMax", ss.get(i).tMax());
            }
        });
        out.set("derived", VersionStore.M.valueToTree(store.derivedAll()));
        out.set("events", VersionStore.M.valueToTree(store.events()));
        out.set("branches", VersionStore.M.valueToTree(store.branches()));
        ArrayNode dg = out.putArray("diagnostics");
        Set<String> seen = new LinkedHashSet<>();
        for (Diagnostic d : diags) if (seen.add(d.code + "|" + d.message)) dg.add(VersionStore.M.valueToTree(d.toMap()));
        return out;
    }
}
