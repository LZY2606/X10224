package dsc.casepack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.derive.Engine;
import dsc.domain.Diagnostic;
import dsc.store.VersionStore;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline case pack for "热转折案：DSC 基线、相变积分与扫描历史审阅器".
 * Loads domain fixtures (never generic tables) and replays the scripted
 * scenario: import -> parameter revision -> local source change ->
 * conflicting human decisions -> mass revision. Every step emits
 * machine-readable events with input fragments, judgement status and
 * affected objects.
 */
public final class CasePack {
    private final VersionStore store;
    private final Engine engine;

    public CasePack(VersionStore store, Engine engine) {
        this.store = store;
        this.engine = engine;
    }

    private static String res(String name) {
        try (InputStream in = CasePack.class.getResourceAsStream("/casepack/" + name)) {
            if (in == null) throw new IllegalStateException("missing casepack resource " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Initial import: each source family stored separately, raw representation preserved. */
    public void importAll(List<Diagnostic> diags) throws SQLException {
        String csv = res("instrument_output.csv");
        ObjectNode run = VersionStore.M.createObjectNode();
        run.put("id", "run-1");
        run.put("rawHeader", "");
        ArrayNode rows = run.putArray("rows");
        StringBuilder header = new StringBuilder();
        for (String line : csv.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                header.append(line).append('\n');
                if (line.contains("exo-convention: EXO_UP")) run.put("exoConvention", "EXO_UP");
                else if (line.contains("exo-convention: EXO_DOWN")) run.put("exoConvention", "EXO_DOWN");
                if (line.contains("heatflow-unit:")) run.put("heatFlowUnit", line.split(":")[1].trim());
                if (line.contains("temperature-unit:")) run.put("tempUnit", line.split(":")[1].trim());
                continue;
            }
            if (line.startsWith("time_s")) continue;
            String[] c = line.split(",");
            ArrayNode r = rows.addArray();
            for (String cell : c) r.add(cell); // raw strings; conversion happens in derived layer
        }
        run.put("rawHeader", header.toString());
        run.put("rawFileName", "instrument_output.csv");
        run.put("directionNote", "exo/endo direction read from header metadata, never from file name");

        put("instrument-output", "run-1", run);
        put("mass", "sample-1", read("mass.json"));
        put("scan-rate", "run-1", read("scan_rate.json"));
        put("atmosphere", "run-1", read("atmosphere.json"));
        put("calibration", "run-1", read("calibration.json"));
        put("tangents", "user-1", read("tangents.json"));

        ObjectNode ev = VersionStore.M.createObjectNode();
        ev.put("step", "s1-import");
        ev.put("status", "ACCEPTED");
        ev.put("fragment", "six source families stored separately with raw representations");
        store.event("IMPORT", ev, 0);
        engine.recomputeAll(diags);
    }

    private static JsonNode read(String name) {
        try { return VersionStore.M.readTree(res(name)); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private void put(String kind, String id, JsonNode payload) throws SQLException {
        String txid = store.begin();
        int v = store.stageSource(txid, kind, id, payload);
        store.commit(txid, new String[]{kind, id, String.valueOf(v)});
    }

    /** Replay the scripted scenario; returns per-step machine-readable reports. */
    public List<JsonNode> replay(List<Diagnostic> diags) throws SQLException {
        List<JsonNode> out = new ArrayList<>();
        JsonNode scenario = read("scenario.json");
        for (JsonNode step : scenario.get("steps")) {
            String action = step.get("action").asText();
            ObjectNode rep = VersionStore.M.createObjectNode();
            rep.put("step", step.get("id").asText());
            rep.put("action", action);
            switch (action) {
                case "import-all" -> rep.put("status", "already imported");
                case "revise-calibration" -> {
                    ObjectNode cal = (ObjectNode) store.currentSource("calibration", "run-1").orElseThrow();
                    cal.put("offsetC", step.get("offsetC").asDouble());
                    cal.put("versionNote", "parameter revision by reviewer");
                    put("calibration", "run-1", cal);
                    var prop = engine.propagate("calibration", "run-1", diags);
                    rep.put("status", "ACCEPTED");
                    rep.set("affected", VersionStore.M.valueToTree(prop));
                    store.event("PARAM_REVISION", rep.deepCopy(), 0);
                }
                case "revise-tangents" -> {
                    ObjectNode tan = (ObjectNode) store.currentSource("tangents", "user-1").orElseThrow();
                    JsonNode patch = step.get("patch");
                    for (JsonNode ev : tan.get("events")) {
                        JsonNode p = patch.get(ev.get("id").asText());
                        if (p != null) {
                            if (p.has("tStart")) ((ObjectNode) ev).put("tStart", p.get("tStart").asDouble());
                            if (p.has("tEnd")) ((ObjectNode) ev).put("tEnd", p.get("tEnd").asDouble());
                        }
                    }
                    put("tangents", "user-1", tan);
                    var prop = engine.propagate("tangents", "user-1", diags);
                    rep.put("status", "ACCEPTED");
                    rep.set("affected", VersionStore.M.valueToTree(prop));
                    store.event("LOCAL_SOURCE_CHANGE", rep.deepCopy(), 0);
                }
                case "conflict" -> {
                    // two reviewers decide differently from the same base version
                    ObjectNode tan = (ObjectNode) store.currentSource("tangents", "user-1").orElseThrow();
                    int base = store.currentVersion("tangents", "user-1");
                    ObjectNode a = tan.deepCopy();
                    a.put("chosenBaseline_ev-melt", "bl-ev-melt-TANGENT");
                    a.put("selectedBy", "reviewer-A");
                    ObjectNode b = tan.deepCopy();
                    b.put("chosenBaseline_ev-melt", "bl-ev-melt-SIGMOIDAL");
                    b.put("selectedBy", "reviewer-B");
                    try {
                        store.saveSource("tangents", "user-1", a, base, "reviewer-A");
                    } catch (VersionStore.Conflict ignored) {}
                    String branchId = null;
                    try {
                        store.saveSource("tangents", "user-1", b, base, "reviewer-B");
                    } catch (VersionStore.Conflict c) { branchId = c.branchId; }
                    rep.put("status", "CONFLICT_OPEN");
                    rep.put("branchId", branchId);
                    rep.put("kept", "both reviewer decisions preserved; unchosen candidate not overwritten");
                    store.event("DECISION_CONFLICT", rep.deepCopy(), base);
                }
                case "revise-mass" -> {
                    ObjectNode mass = (ObjectNode) store.currentSource("mass", "sample-1").orElseThrow();
                    mass.put("valueMg", step.get("valueMg").asDouble());
                    mass.put("versionNote", "re-weighed after drying");
                    put("mass", "sample-1", mass);
                    var prop = engine.propagate("mass", "sample-1", diags);
                    rep.put("status", "ACCEPTED");
                    rep.put("rule", "mass revision expires every normalized derivation");
                    rep.set("affected", VersionStore.M.valueToTree(prop));
                    store.event("MASS_REVISION", rep.deepCopy(), 0);
                }
                default -> rep.put("status", "SKIPPED");
            }
            out.add(rep);
        }
        return out;
    }
}
