package dsc;

import com.fasterxml.jackson.databind.JsonNode;
import dsc.casepack.CasePack;
import dsc.derive.Engine;
import dsc.domain.Diagnostic;
import dsc.store.Database;
import dsc.store.VersionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EngineScenarioTest {

    @TempDir Path tmp;

    private static class Fx {
        VersionStore store; Engine engine; CasePack pack; List<Diagnostic> diags = new ArrayList<>();
        Fx(Path tmp) throws Exception {
            store = new VersionStore(new Database(tmp.resolve("fx.db").toString()));
            engine = new Engine(store);
            pack = new CasePack(store, engine);
        }
    }

    private JsonNode derived(Fx fx, String kind, String id) throws Exception {
        for (JsonNode d : fx.store.derivedAll())
            if (d.get("kind").asText().equals(kind) && d.get("id").asText().equals(id)) return d;
        return null;
    }

    @Test
    void casePackImport_buildsSegmentsBaselinesIntegrations() throws Exception {
        Fx fx = new Fx(tmp);
        fx.pack.importAll(fx.diags);

        // segments: heat-cool-heat detected from data, not file name
        JsonNode segs = derived(fx, "segments", "run-1");
        assertNotNull(segs);
        assertEquals("HEATING", segs.get("payload").get(0).get("direction").asText());
        assertEquals("COOLING", segs.get("payload").get(1).get("direction").asText());
        assertEquals("HEATING", segs.get("payload").get(2).get("direction").asText());

        // parallel baselines retained for melting event
        JsonNode bl = derived(fx, "baselines", "ev-melt");
        assertEquals(3, bl.get("payload").size());

        // integration has onset/peak/endset + enthalpy + error sources
        JsonNode integ = derived(fx, "integration", "ev-melt");
        JsonNode r0 = integ.get("payload").get(0);
        assertTrue(r0.has("onsetC") && r0.has("peakC") && r0.has("endsetC"));
        assertTrue(r0.get("enthalpyJg").asDouble() < 0, "melting is endo under EXO_UP");
        assertTrue(r0.get("errorSources").size() >= 3);

        // empty interval event -> diagnostic, no fabricated enthalpy
        JsonNode empty = derived(fx, "integration", "ev-empty");
        assertEquals("EMPTY_INTERVAL", empty.get("payload").get("diagnostic").asText());
        assertTrue(fx.diags.stream().anyMatch(d -> d.code.equals("EMPTY_INTERVAL")));

        // reverse heat flow flagged from the artifact region
        assertTrue(fx.diags.stream().anyMatch(d -> d.code.equals("REVERSE_HEAT_FLOW")));

        // unconvertible raw row kept with diagnostic
        assertTrue(fx.diags.stream().anyMatch(d -> d.code.equals("UNIT_UNCONVERTIBLE")));

        // cooling event integrated on cooling segment only
        JsonNode cool = derived(fx, "integration", "ev-cool-cryst");
        assertEquals("COOLING", cool.get("payload").get(0).get("direction").asText());
        assertTrue(cool.get("payload").get(0).get("enthalpyJg").asDouble() > 0,
                "crystallization on cooling is exo under EXO_UP");
    }

    @Test
    void localSourceChange_onlyAffectedSubgraphRecomputed() throws Exception {
        Fx fx = new Fx(tmp);
        fx.pack.importAll(fx.diags);
        List<JsonNode> steps = fx.pack.replay(fx.diags);

        JsonNode local = steps.stream().filter(s -> s.get("action").asText().equals("revise-tangents"))
                .findFirst().orElseThrow();
        Map<String, JsonNode> affected = new java.util.HashMap<>();
        local.get("affected").fields().forEachRemaining(e -> affected.put(e.getKey(), e.getValue()));
        // ev-melt depends on tangents -> stale+recomputed; segments do not depend on tangents -> valid
        assertTrue(affected.get("integration/ev-melt").asText().startsWith("STALE"));
        assertTrue(affected.get("segments/run-1").asText().startsWith("VALID"));

        // conflict step opened a branch and kept both decisions
        JsonNode conflict = steps.stream().filter(s -> s.get("action").asText().equals("conflict"))
                .findFirst().orElseThrow();
        assertEquals("CONFLICT_OPEN", conflict.get("status").asText());
        assertEquals(1, fx.store.branches().size());

        // mass revision step ran and integrations carry mass dependency
        JsonNode mass = steps.stream().filter(s -> s.get("action").asText().equals("revise-mass"))
                .findFirst().orElseThrow();
        assertEquals("ACCEPTED", mass.get("status").asText());
        JsonNode integ = derived(fx, "integration", "ev-melt");
        boolean massDep = false;
        for (JsonNode dep : integ.get("deps"))
            if (dep.get("kind").asText().equals("mass")) massDep = true;
        assertTrue(massDep, "normalized derivations must depend on the mass version");

        // machine-readable events exist for every judgement
        List<JsonNode> events = fx.store.events();
        assertTrue(events.stream().anyMatch(e -> e.get("type").asText().equals("IMPORT")));
        assertTrue(events.stream().anyMatch(e -> e.get("type").asText().equals("PARAM_REVISION")));
        assertTrue(events.stream().anyMatch(e -> e.get("type").asText().equals("LOCAL_SOURCE_CHANGE")));
        assertTrue(events.stream().anyMatch(e -> e.get("type").asText().equals("DECISION_CONFLICT")));
        assertTrue(events.stream().anyMatch(e -> e.get("type").asText().equals("MASS_REVISION")));
    }

    @Test
    void massRevision_expiresNormalizedDerivations() throws Exception {
        Fx fx = new Fx(tmp);
        fx.pack.importAll(fx.diags);
        JsonNode before = derived(fx, "integration", "ev-cryst");
        double h1 = before.get("payload").get(0).get("enthalpyJg").asDouble();

        var mass = (com.fasterxml.jackson.databind.node.ObjectNode)
                fx.store.currentSource("mass", "sample-1").orElseThrow();
        mass.put("valueMg", 20.48); // doubled
        String tx = fx.store.begin();
        int v = fx.store.stageSource(tx, "mass", "sample-1", mass);
        fx.store.commit(tx, new String[]{"mass", "sample-1", String.valueOf(v)});
        Map<String, String> report = fx.engine.propagate("mass", "sample-1", fx.diags);

        assertTrue(report.get("integration/ev-cryst").startsWith("STALE"));
        assertTrue(report.get("segments/run-1").startsWith("VALID"),
                "segments do not depend on mass and must stay valid");
        JsonNode after = derived(fx, "integration", "ev-cryst");
        double h2 = after.get("payload").get(0).get("enthalpyJg").asDouble();
        assertEquals(h1 / 2, h2, h1 / 100.0, "enthalpy per mg halves when mass doubles");
        assertEquals("current", after.get("status").asText());
    }
}
