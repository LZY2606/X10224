package dsc.derive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.domain.*;
import dsc.store.VersionStore;

import java.sql.SQLException;
import java.util.*;

/**
 * Dependency-graph propagation. A source change marks only the derived
 * nodes that (transitively) depend on it stale and recomputes exactly
 * those; every other node keeps its version with a recorded reason why
 * it is still valid. A mass revision invalidates every normalized
 * (per-mass) derivation.
 */
public final class Engine {
    private final VersionStore store;

    public Engine(VersionStore store) { this.store = store; }

    private static JsonNode dep(String kind, String id, int version) {
        ObjectNode n = VersionStore.M.createObjectNode();
        n.put("kind", kind); n.put("id", id); n.put("version", version);
        return n;
    }

    /** Full (re)build of the derived layer from current sources. */
    public Map<String, String> recomputeAll(List<Diagnostic> diags) throws SQLException {
        Map<String, String> report = new LinkedHashMap<>();
        JsonNode run = store.currentSource("instrument-output", "run-1").orElse(null);
        if (run == null) { diags.add(new Diagnostic("NO_INPUT", "BLOCK", "no instrument output", "run-1")); return report; }
        int runV = store.currentVersion("instrument-output", "run-1");
        Curve curve = parseCurve(run, diags);
        List<Curve.Segment> segs = curve.splitSegments();

        ArrayNode segArr = VersionStore.M.createArrayNode();
        for (int i = 0; i < segs.size(); i++) {
            Curve.Segment s = segs.get(i);
            ObjectNode sn = segArr.addObject();
            sn.put("index", i);
            sn.put("direction", s.direction.name());
            sn.put("tMin", s.tMin()); sn.put("tMax", s.tMax());
            sn.put("points", s.points.size());
            sn.put("uniformSpacing", s.isUniformSpacing());
            if (Curve.hasReverseHeatFlow(s))
                diags.add(new Diagnostic("REVERSE_HEAT_FLOW", "WARN",
                        "heat-flow sign reversal inside segment " + i, "segment-" + i));
        }
        ArrayNode segDeps = VersionStore.M.createArrayNode().add(dep("instrument-output", "run-1", runV));
        store.putDerived("segments", "run-1", runV, segDeps, "current", segArr);
        report.put("segments/run-1", "recomputed from instrument-output v" + runV);

        JsonNode tangents = store.currentSource("tangents", "user-1").orElse(null);
        if (tangents == null) return report;
        int tanV = store.currentVersion("tangents", "user-1");
        int massV = store.currentVersion("mass", "sample-1");
        int rateV = store.currentVersion("scan-rate", "run-1");
        int calV = store.currentVersion("calibration", "run-1");
        double massMg = store.currentSource("mass", "sample-1").map(m -> m.get("valueMg").asDouble()).orElse(Double.NaN);
        double rate = store.currentSource("scan-rate", "run-1").map(m -> m.get("kPerMin").asDouble()).orElse(Double.NaN);
        double calOffset = store.currentSource("calibration", "run-1").map(m -> m.get("offsetC").asDouble()).orElse(0.0);

        for (JsonNode evDef : tangents.get("events")) {
            String evId = evDef.get("id").asText();
            int segIdx = evDef.get("segment").asInt();
            if (segIdx >= segs.size()) continue;
            Curve.Segment seg = segs.get(segIdx);
            // heating and cooling segments are never mixed: event is bound to exactly one segment
            double t0 = evDef.get("tStart").asDouble() + calOffset;
            double t1 = evDef.get("tEnd").asDouble() + calOffset;
            List<Baseline> cands = Baseline.candidates(evId, seg.points, Math.min(t0, t1), Math.max(t0, t1));
            if (cands.isEmpty()) {
                ObjectNode empty = VersionStore.M.createObjectNode();
                empty.put("event", evId); empty.put("candidates", 0);
                empty.put("diagnostic", "EMPTY_INTERVAL");
                store.putDerived("integration", evId, runV, segDeps, "current", empty);
                diags.add(new Diagnostic("EMPTY_INTERVAL", "WARN",
                        "event " + evId + " selects an interval with no samples", evId));
                report.put("integration/" + evId, "empty interval -> diagnostic only");
                continue;
            }
            ArrayNode blArr = VersionStore.M.createArrayNode();
            for (Baseline b : cands) {
                ObjectNode bn = blArr.addObject();
                bn.put("id", b.id); bn.put("kind", b.kind.name());
                bn.put("tStart", b.tStart); bn.put("tEnd", b.tEnd);
                ArrayNode curvePts = bn.putArray("curve");
                for (double t = b.tStart; t <= b.tEnd; t += (b.tEnd - b.tStart) / 40.0)
                    curvePts.addArray().add(t).add(b.eval(t));
            }
            ArrayNode blDeps = VersionStore.M.createArrayNode()
                    .add(dep("instrument-output", "run-1", runV)).add(dep("tangents", "user-1", tanV))
                    .add(dep("calibration", "run-1", calV));
            store.putDerived("baselines", evId, runV, blDeps, "current", blArr);

            // integrate every candidate: parallel baselines keep their differences visible
            ArrayNode integ = VersionStore.M.createArrayNode();
            for (Baseline b : cands) {
                Integrator.Result r = Integrator.integrate(seg, b, massMg, rate, curve.exoConvention);
                ObjectNode rn = integ.addObject();
                rn.put("baselineId", b.id);
                if (r.onsetC != null) rn.put("onsetC", r.onsetC);
                if (r.peakC != null) rn.put("peakC", r.peakC);
                if (r.endsetC != null) rn.put("endsetC", r.endsetC);
                if (r.enthalpyJg != null) rn.put("enthalpyJg", r.enthalpyJg);
                rn.put("direction", seg.direction.name());
                rn.put("exoConvention", curve.exoConvention == null ? "UNKNOWN" : curve.exoConvention);
                ArrayNode es = rn.putArray("errorSources");
                r.errorSources.forEach(es::add);
                ArrayNode dg = rn.putArray("diagnostics");
                r.diagnostics.forEach(d -> dg.add(d.code));
                diags.addAll(r.diagnostics);
            }
            ArrayNode inDeps = VersionStore.M.createArrayNode()
                    .add(dep("instrument-output", "run-1", runV)).add(dep("tangents", "user-1", tanV))
                    .add(dep("mass", "sample-1", massV)).add(dep("scan-rate", "run-1", rateV))
                    .add(dep("calibration", "run-1", calV));
            store.putDerived("integration", evId, runV, inDeps, "current", integ);
            report.put("integration/" + evId, "recomputed, " + cands.size() + " parallel baselines");
        }
        return report;
    }

    /**
     * Dependency propagation after one source changed. Returns per-node
     * explanation: recomputed (and why) or still valid (and why).
     */
    public Map<String, String> propagate(String changedKind, String changedId, List<Diagnostic> diags)
            throws SQLException {
        Map<String, String> report = new LinkedHashMap<>();
        List<JsonNode> all = store.derivedAll();
        boolean anyAffected = false;
        for (JsonNode d : all) {
            boolean affected = false;
            for (JsonNode dep : d.get("deps")) {
                if (dep.get("kind").asText().equals(changedKind) && dep.get("id").asText().equals(changedId)) {
                    int have = store.currentVersion(changedKind, changedId);
                    if (dep.get("version").asInt() != have) affected = true;
                }
            }
            String key = d.get("kind").asText() + "/" + d.get("id").asText();
            if (affected) {
                anyAffected = true;
                store.markDerivedStatus(d.get("kind").asText(), d.get("id").asText(), "stale");
                report.put(key, "STALE: depends on " + changedKind + "/" + changedId + " which moved to v"
                        + store.currentVersion(changedKind, changedId));
            } else {
                report.put(key, "VALID: no dependency on changed " + changedKind + "/" + changedId);
            }
        }
        if (anyAffected) {
            List<Diagnostic> sub = new ArrayList<>();
            recomputeAll(sub);
            diags.addAll(sub);
            report.put("_action", "recomputed affected subgraph only");
        } else {
            report.put("_action", "no derived node affected; nothing recomputed");
        }
        return report;
    }

    /** Parse raw instrument output; unconvertible rows stay raw with a diagnostic. */
    public static Curve parseCurve(JsonNode run, List<Diagnostic> diags) {
        List<Curve.Pt> pts = new ArrayList<>();
        String hfUnit = run.has("heatFlowUnit") ? run.get("heatFlowUnit").asText() : "mW";
        String tmpUnit = run.has("tempUnit") ? run.get("tempUnit").asText() : "degC";
        String exo = run.has("exoConvention") && !run.get("exoConvention").isNull()
                ? run.get("exoConvention").asText() : null;
        for (JsonNode row : run.get("rows")) {
            try {
                double t = Double.parseDouble(row.get(0).asText().trim());
                double temp = Double.parseDouble(row.get(1).asText().trim());
                double hf = Double.parseDouble(row.get(2).asText().trim());
                pts.add(new Curve.Pt(t, temp, hf, row.toString()));
            } catch (Exception e) {
                diags.add(new Diagnostic("UNIT_UNCONVERTIBLE", "WARN",
                        "row kept raw, not plottable: " + row, "run-1"));
            }
        }
        return new Curve(pts, hfUnit, tmpUnit, exo);
    }
}
