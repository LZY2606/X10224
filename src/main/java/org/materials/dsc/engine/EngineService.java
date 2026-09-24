package org.materials.dsc.engine;

import org.materials.dsc.domain.AtmosphereRecord;
import org.materials.dsc.domain.BaselineCandidate;
import org.materials.dsc.domain.CaseInfo;
import org.materials.dsc.domain.CaseState;
import org.materials.dsc.domain.DependencyNode;
import org.materials.dsc.domain.Diagnostic;
import org.materials.dsc.domain.Direction;
import org.materials.dsc.domain.EnthalpyResult;
import org.materials.dsc.domain.Event;
import org.materials.dsc.domain.InstrumentOutput;
import org.materials.dsc.domain.MassRecord;
import org.materials.dsc.domain.Quantity;
import org.materials.dsc.domain.RateRecord;
import org.materials.dsc.domain.RuleVersions;
import org.materials.dsc.domain.Segment;
import org.materials.dsc.domain.TangentSelection;
import org.materials.dsc.domain.TemperatureCalibration;
import org.materials.dsc.domain.Transition;
import org.materials.dsc.domain.Units;
import org.materials.dsc.io.CsvInstrumentImporter;
import org.materials.dsc.store.EventStore;
import org.materials.dsc.store.Json;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 应用编排：命令 -> 局部重算 -> 原子事件批次。
 * 存储层只在整批派生成功后切换 revision；任何一步失败则整批不可见。
 */
public class EngineService {

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    private final EventStore store;
    private final Map<String, String[]> rowSegmentsByCase = new ConcurrentHashMap<>();

    public EngineService(EventStore store) {
        this.store = store;
    }

    public EventStore store() {
        return store;
    }

    // ---------- case ----------

    public CaseState createCase(String name, String material, String note) {
        String id = "case-" + UUID.randomUUID();
        store.createCase(id, name, material, note);
        ObjectNode p = Json.obj();
        p.put("name", name);
        p.put("material", material);
        p.put("note", note);
        commit(id, 0, List.of(event(id, Event.CASE_CREATED, p)));
        return load(id);
    }

    public CaseState createCaseWithId(String id, String name, String material, String note) {
        store.createCase(id, name, material, note);
        ObjectNode p = Json.obj();
        p.put("name", name);
        p.put("material", material);
        p.put("note", note);
        p.put("imported", true);
        store.appendEvents(id, 0, List.of(new Event(1, id, Event.CASE_IMPORTED,
                Instant.now().toString(), "fixture", RuleVersions.CURRENT, p)));
        return load(id);
    }

    public List<String> listCases() {
        return store.listCases();
    }

    public CaseState load(String caseId) {
        return loadAt(caseId, Long.MAX_VALUE);
    }

    public CaseState loadAt(String caseId, long seq) {
        return Projector.project(store.loadEvents(caseId, seq));
    }

    public long revision(String caseId) {
        return store.revision(caseId);
    }

    private List<Event> commit(String caseId, long expectedRevision, List<Event> events) {
        long rev = store.appendEvents(caseId, expectedRevision, events);
        if (rev < 0) {
            throw new ConflictException("case " + caseId + " 已被另一位使用者修改（基线版本 "
                    + expectedRevision + " 过期），请刷新后重试——冲突可解决，不静默覆盖");
        }
        return events;
    }

    private Event event(String caseId, String type, ObjectNode payload) {
        return new Event(0, caseId, type, Instant.now().toString(),
                "reviewer", RuleVersions.CURRENT, payload);
    }

    // ---------- direct sources ----------

    public InstrumentOutput archiveInstrumentOutput(String caseId, String fileName, String csvContent) {
        return archiveInstrumentOutput(caseId, "out-" + shortId(), fileName, csvContent, null, null);
    }

    public InstrumentOutput archiveInstrumentOutput(String caseId, String outputId, String fileName,
                                                    String csvContent,
                                                    Map<String, Segmenter.ProgramInfo> programs,
                                                    Map<Integer, String> rowProgramOverride) {
        CaseState st = load(caseId);
        CsvInstrumentImporter.Parsed parsed = CsvInstrumentImporter.importCsv(outputId, fileName, csvContent);
        InstrumentOutput out = parsed.output();
        out = new InstrumentOutput(out.id(), out.fileName(), out.instrument(), out.declaredDirection(),
                out.signConvention(), out.declaredDirectionRaw(), out.signConventionRaw(),
                out.tempUnit(), out.hfUnit(), out.rows(), out.diagnostics(),
                out.nonUniformTemperature(), out.medianStepCelsius(),
                String.valueOf(st.seq + 1), out.importedAt(), out.sha256());
        store.putBlob(caseId, "raw/" + fileName, csvContent, "text/csv");
        String[] labels = parsed.rowSegmentLabels();
        if (rowProgramOverride != null) {
            rowProgramOverride.forEach((idx, label) -> labels[idx] = label);
        }
        rowSegmentsByCase.put(caseId, labels);
        ObjectNode p = Payloads.output(out);
        ArrayNode names = p.putArray("segmentNames");
        parsed.segmentNames().forEach(names::add);
        addRawNode(p, out.id(), "instrument-output:" + out.fileName(), List.of());
        commit(caseId, st.revision, List.of(event(caseId, Event.OUTPUT_ARCHIVED, p)));
        if (programs == null) {
            programs = defaultPrograms(out, parsed.segmentNames());
        }
        deriveSegments(caseId, out.id(), programs);
        return out;
    }

    private Map<String, Segmenter.ProgramInfo> defaultPrograms(InstrumentOutput out, List<String> names) {
        Map<String, Segmenter.ProgramInfo> map = new LinkedHashMap<>();
        if (names.size() <= 1) {
            return map;
        }
        for (String name : names) {
            map.put(name, new Segmenter.ProgramInfo(Direction.parse(name.contains("Cool") ? "cooling"
                    : name.contains("Heat") ? "heating" : "unknown"), Double.NaN, name));
        }
        return map;
    }

    public MassRecord recordMass(String caseId, double grams, String note) {
        return recordMass(caseId, "mass-" + shortId(), grams, note);
    }

    public MassRecord recordMassWithId(String caseId, String id, double grams, String note) {
        return recordMass(caseId, id, grams, note);
    }

    public MassRecord recordMass(String caseId, String id, double grams, String note) {
        CaseState st = load(caseId);
        int version = st.masses.size() + 1;
        MassRecord m = new MassRecord(id, version, Quantity.of(grams, Units.GRAM),
                note, null, Instant.now().toString());
        ObjectNode massPayload = Payloads.mass(m);
        addRawNode(massPayload, m.id(), "sample-mass-v" + version, List.of());
        String type = version == 1 ? Event.MASS_RECORDED : Event.MASS_REVISED;
        List<Event> batch = new ArrayList<>();
        batch.add(event(caseId, type, massPayload));
        if (version > 1) {
            appendMassInvalidation(st, batch, id);
        }
        commit(caseId, st.revision, batch);
        return m;
    }

    private void appendMassInvalidation(CaseState st, List<Event> batch, String newMassId) {
        ObjectNode p = Json.obj();
        p.put("changedSource", newMassId);
        p.put("reason", "sample-mass-revised");
        p.put("scope", "normalized-derived-only");
        ArrayNode affected = p.putArray("affectedObjects");
        for (EnthalpyResult a : st.analyses.values()) {
            ObjectNode t = affected.addObject();
            t.put("kind", "analysis");
            t.put("id", a.id());
            t.put("effect", "specific-enthalpy-stale");
        }
        batch.add(event(st.info.id(), Event.DERIVED_INVALIDATED, p));
    }

    public void recordRate(String caseId, double celsiusPerMin, String rawText) {
        recordRateWithId(caseId, "rate-" + shortId(), celsiusPerMin, rawText);
    }

    public void recordRateWithId(String caseId, String id, double celsiusPerMin, String rawText) {
        CaseState st = load(caseId);
        RateRecord r = new RateRecord(id,
                Quantity.of(celsiusPerMin, Units.C_PER_MIN), rawText, null, Instant.now().toString());
        ObjectNode rp = Payloads.rate(r);
        addRawNode(rp, id, "scan-rate", List.of());
        List<Event> rateBatch = new ArrayList<>();
        rateBatch.add(event(caseId, Event.RATE_RECORDED, rp));
        if (!st.rates.isEmpty()) {
            rateBatch.add(invalidationEvent(caseId, id, "scan-rate-revised",
                    "rate-changed"));
        }
        commit(caseId, st.revision, rateBatch);
    }

    public void recordAtmosphere(String caseId, String gas, Double flowMlPerMin, String rawText) {
        recordAtmosphereWithId(caseId, "atmo-" + shortId(), gas, flowMlPerMin, rawText);
    }

    public void recordAtmosphereWithId(String caseId, String id, String gas,
                                       Double flowMlPerMin, String rawText) {
        CaseState st = load(caseId);
        AtmosphereRecord a = new AtmosphereRecord(id, gas,
                flowMlPerMin == null ? null : Quantity.raw(String.valueOf(flowMlPerMin), "mL/min"),
                rawText, null, Instant.now().toString());
        ObjectNode ap = Payloads.atmosphere(a);
        addRawNode(ap, id, "atmosphere:" + gas, List.of());
        commit(caseId, st.revision,
                List.of(event(caseId, Event.ATMOSPHERE_RECORDED, ap)));
    }

    public void recordCalibration(String caseId, double slope, double offsetCelsius,
                                  String standardRef, String note) {
        recordCalibrationWithId(caseId, "cal-" + shortId(), slope, offsetCelsius, standardRef, note);
    }

    public void recordCalibrationWithId(String caseId, String id, double slope, double offsetCelsius,
                                        String standardRef, String note) {
        CaseState st = load(caseId);
        TemperatureCalibration c = new TemperatureCalibration(id,
                slope, offsetCelsius, standardRef, note, null, Instant.now().toString(),
                new ArrayList<>());
        ObjectNode cp = Payloads.calibration(c);
        addRawNode(cp, id, "temperature-calibration:" + standardRef, List.of());
        List<Event> calBatch = new ArrayList<>();
        calBatch.add(event(caseId, Event.CALIBRATION_RECORDED, cp));
        if (!st.calibrations.isEmpty()) {
            calBatch.add(invalidationEvent(caseId, id, "temperature-calibration-revised",
                    "calibration-changed"));
        }
        commit(caseId, st.revision, calBatch);
    }

    private Event invalidationEvent(String caseId, String changedSource, String reason, String code) {
        CaseState st = load(caseId);
        ObjectNode p = Json.obj();
        p.put("changedSource", changedSource);
        p.put("reason", reason);
        p.put("code", code);
        ArrayNode affected = p.putArray("affectedObjects");
        for (String impacted : DependencyUpdater.impacted(st, changedSource)) {
            ObjectNode t = affected.addObject();
            t.put("id", impacted);
            t.put("kind", "node");
        }
        for (EnthalpyResult a : st.analyses.values()) {
            ObjectNode t = affected.addObject();
            t.put("kind", "analysis");
            t.put("id", a.id());
            t.put("effect", "recompute-required");
        }
        return event(caseId, Event.DERIVED_INVALIDATED, p);
    }

    /**
     * 仪器输出修订（只改一个直接来源）：新输出版本入库，
     * 旧输出及其分支保留为历史，只传播到该输出的派生。
     */
    public InstrumentOutput reviseInstrumentOutput(String caseId, String newOutputId,
                                                   String fileName, String csvContent,
                                                   Map<String, Segmenter.ProgramInfo> programs) {
        CaseState st = load(caseId);
        CsvInstrumentImporter.Parsed parsed = CsvInstrumentImporter.importCsv(newOutputId, fileName, csvContent);
        InstrumentOutput out = parsed.output();
        out = new InstrumentOutput(out.id(), out.fileName(), out.instrument(), out.declaredDirection(),
                out.signConvention(), out.declaredDirectionRaw(), out.signConventionRaw(),
                out.tempUnit(), out.hfUnit(), out.rows(), out.diagnostics(),
                out.nonUniformTemperature(), out.medianStepCelsius(),
                String.valueOf(st.seq + 1), out.importedAt(), out.sha256());
        store.putBlob(caseId, "raw/" + fileName, csvContent, "text/csv");
        rowSegmentsByCase.put(caseId, parsed.rowSegmentLabels());
        ObjectNode p = Payloads.output(out);
        ArrayNode names = p.putArray("segmentNames");
        parsed.segmentNames().forEach(names::add);
        addRawNode(p, out.id(), "instrument-output(revised):" + out.fileName(), List.of());
        p.put("supersedes", st.outputOrder.get(st.outputOrder.size() - 1));
        List<Event> batch = new ArrayList<>();
        batch.add(event(caseId, Event.OUTPUT_ARCHIVED, p));
        commit(caseId, st.revision, batch);
        deriveSegments(caseId, out.id(), programs);
        return out;
    }

    // ---------- derived pipeline ----------

    public List<Segment> deriveSegments(String caseId, String outputId,
                                        Map<String, Segmenter.ProgramInfo> programs) {
        CaseState st = load(caseId);
        InstrumentOutput out = st.outputs.get(outputId);
        TemperatureCalibration cal = st.calibrations.isEmpty() ? null
                : st.calibrations.values().iterator().next();
        Normalizer.Series series = Normalizer.normalize(out, cal);
        String[] labels = rowSegmentsByCase.computeIfAbsent(caseId, k -> {
            String[] l = new String[out.rowCount()];
            for (int i = 0; i < l.length; i++) {
                l[i] = "S1";
            }
            return l;
        });
        Segmenter.Result sr = Segmenter.segment(out, series, programs, labels);
        List<Event> batch = new ArrayList<>();
        ObjectNode segP = Payloads.segments(sr.segments());
        List<String> segDeps = new ArrayList<>();
        segDeps.add(outputId);
        if (cal != null) segDeps.add(cal.id());
        addNode(segP, "segments-" + outputId, DependencyNode.KIND_DERIVED,
                "segments-of-" + out.fileName(), 1, RuleVersions.CURRENT,
                DependencyNode.STATUS_OK, segDeps);
        batch.add(event(caseId, Event.SEGMENTS_DERIVED, segP));
        for (Diagnostic d : sr.diagnostics()) {
            batch.add(event(caseId, Event.DIAGNOSTIC, diagnosticPayload(d)));
        }
        commit(caseId, st.revision, batch);
        return sr.segments();
    }

    public void detectTransitions(String caseId, String outputId) {
        CaseState st = load(caseId);
        InstrumentOutput out = st.outputs.get(outputId);
        TemperatureCalibration cal = st.calibrations.isEmpty() ? null
                : st.calibrations.values().iterator().next();
        Normalizer.Series series = Normalizer.normalize(out, cal);
        List<Transition> all = new ArrayList<>();
        List<Diagnostic> allDiag = new ArrayList<>();
        for (Segment seg : st.segments.values()) {
            if (!seg.traceId().equals(outputId)) {
                continue;
            }
            Detector.Result dr = Detector.detect(seg, series, RuleVersions.CURRENT,
                    String.valueOf(st.seq + 1));
            all.addAll(dr.transitions());
            allDiag.addAll(dr.diagnostics());
        }
        List<Event> batch = new ArrayList<>();
        ObjectNode trP = Payloads.transitions(all);
        for (Segment seg : st.segments.values()) {
            if (!seg.traceId().equals(outputId)) continue;
            addNode(trP, "detector-" + seg.id(), DependencyNode.KIND_DERIVED,
                    "transitions-of-" + seg.id(), 1, RuleVersions.CURRENT,
                    DependencyNode.STATUS_OK, List.of("segments-" + outputId, seg.id()));
        }
        batch.add(event(caseId, Event.TRANSITIONS_DETECTED, trP));
        for (Diagnostic d : allDiag) {
            batch.add(event(caseId, Event.DIAGNOSTIC, diagnosticPayload(d)));
        }
        commit(caseId, st.revision, batch);
    }

    public void deriveBaselines(String caseId, String transitionId,
                                Double leftX1, Double leftX2, Double rightX1, Double rightX2) {
        CaseState st = load(caseId);
        Transition tr = st.transitions.get(transitionId);
        if (tr == null) {
            throw new IllegalArgumentException("transition not found: " + transitionId);
        }
        InstrumentOutput out = st.outputs.get(tr.traceId());
        Segment seg = st.segments.get(tr.segmentId());
        TemperatureCalibration cal = st.calibrations.isEmpty() ? null
                : st.calibrations.values().iterator().next();
        Normalizer.Series series = Normalizer.normalize(out, cal);
        BaselineFitter.Result fr = BaselineFitter.fit(seg, tr, series,
                RuleVersions.CURRENT, String.valueOf(st.seq + 1));
        List<Event> batch = new ArrayList<>();
        ObjectNode blP = Payloads.baselines(fr.candidates());
        for (BaselineCandidate b : fr.candidates()) {
            addNode(blP, "node-" + b.id(), DependencyNode.KIND_DERIVED,
                    "baseline:" + b.origin() + ":" + transitionId, 1,
                    RuleVersions.CURRENT, DependencyNode.STATUS_OK,
                    List.of("detector-" + tr.segmentId(), tr.traceId()));
        }
        batch.add(event(caseId, Event.BASELINES_DERIVED, blP));
        for (Diagnostic d : fr.diagnostics()) {
            batch.add(event(caseId, Event.DIAGNOSTIC, diagnosticPayload(d)));
        }
        commit(caseId, st.revision, batch);
    }

    public void adoptBaseline(String caseId, String transitionId, String baselineId) {
        CaseState st = load(caseId);
        if (!st.baselines.containsKey(baselineId)) {
            throw new IllegalArgumentException("baseline candidate not found: " + baselineId);
        }
        ObjectNode p = Json.obj();
        p.put("transitionId", transitionId);
        p.put("baselineId", baselineId);
        ArrayNode rejected = p.putArray("rejectedCandidates");
        st.baselinesOf(transitionId).stream()
                .map(BaselineCandidate::id)
                .filter(id -> !id.equals(baselineId))
                .forEach(rejected::add);
        commit(caseId, st.revision, List.of(event(caseId, Event.BASELINE_ADOPTED, p)));
        recomputeAnalysis(caseId, transitionId, baselineId);
    }

    public void rejectTransition(String caseId, String transitionId, String reason) {
        CaseState st = load(caseId);
        ObjectNode p = Json.obj();
        p.put("transitionId", transitionId);
        p.put("reason", reason);
        commit(caseId, st.revision, List.of(event(caseId, Event.BASELINE_REJECTED, p)));
    }

    public void adjustIntervalAndRecompute(String caseId, String transitionId,
                                           double loCelsius, double hiCelsius) {
        CaseState st = load(caseId);
        Transition tr = st.transitions.get(transitionId);
        InstrumentOutput out = st.outputs.get(tr.traceId());
        Segment seg = st.segments.get(tr.segmentId());
        TemperatureCalibration cal = st.calibrations.isEmpty() ? null
                : st.calibrations.values().iterator().next();
        Normalizer.Series series = Normalizer.normalize(out, cal);
        int lo = nearestIndex(series, loCelsius, seg.startIndex(), seg.endIndex());
        int hi = nearestIndex(series, hiCelsius, seg.startIndex(), seg.endIndex());
        if (lo > hi) {
            int tmp = lo;
            lo = hi;
            hi = tmp;
        }
        ObjectNode p = Json.obj();
        p.put("transitionId", transitionId);
        p.put("loIndex", lo);
        p.put("hiIndex", hi);
        p.put("loCelsius", loCelsius);
        p.put("hiCelsius", hiCelsius);
        commit(caseId, st.revision, List.of(event(caseId, Event.INTERVAL_ADJUSTED, p)));
        if (tr.adoptedBaselineId() != null) {
            CaseState fresh = load(caseId);
            recomputeAnalysis(caseId, transitionId, fresh.transitions.get(transitionId)
                    .adoptedBaselineId());
        }
    }

    public EnthalpyResult recomputeAnalysis(String caseId, String transitionId, String baselineId) {
        CaseState st = load(caseId);
        Transition tr = st.transitions.get(transitionId);
        BaselineCandidate bl = st.baselines.get(baselineId);
        Segment seg = st.segments.get(tr.segmentId());
        InstrumentOutput out = st.outputs.get(tr.traceId());
        TemperatureCalibration cal = st.calibrations.isEmpty() ? null
                : st.calibrations.values().iterator().next();
        Normalizer.Series series = Normalizer.normalize(out, cal);
        double rate = seg.rate().convertible() ? seg.rate().canonical() : 10.0;
        if (!seg.rate().convertible()) {
            rate = inferRate(series, seg);
        }
        EngineMath.StepStats stepStats = EngineMath.stepStats(series.tCelsius);
        EnthalpyResult result = Integrator.integrate(seg, tr, bl, series, st.currentMass(),
                rate, stepStats.maxRelDeviation(), RuleVersions.CURRENT,
                String.valueOf(st.seq + 1));
        ObjectNode anP = Payloads.analysis(result);
        List<String> aDeps = new ArrayList<>();
        aDeps.add("node-" + bl.id());
        aDeps.add(seg.id());
        aDeps.add(tr.traceId());
        if (st.currentMassId() != null) aDeps.add(st.currentMassId());
        DependencyNode existing = st.nodes.get("node-" + result.id());
        addNode(anP, "node-" + result.id(), DependencyNode.KIND_DERIVED,
                "analysis:" + transitionId, existing == null ? 1 : existing.version() + 1,
                RuleVersions.CURRENT, DependencyNode.STATUS_OK, aDeps);
        commit(caseId, st.revision,
                List.of(event(caseId, Event.ANALYSIS_DERIVED, anP)));
        return result;
    }

    private int nearestIndex(Normalizer.Series s, double t, int lo, int hi) {
        int best = lo;
        double bestD = Double.POSITIVE_INFINITY;
        for (int i = lo; i <= hi; i++) {
            if (!s.valid[i]) {
                continue;
            }
            double d = Math.abs(s.tCelsius[i] - t);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private double inferRate(Normalizer.Series s, Segment seg) {
        double dt = 0;
        int count = 0;
        for (int i = seg.startIndex() + 1; i <= seg.endIndex(); i++) {
            if (s.valid[i] && s.valid[i - 1]) {
                dt += s.tCelsius[i] - s.tCelsius[i - 1];
                count++;
            }
        }
        return count == 0 ? 10.0 : Math.max(0.1, Math.abs(dt) / (count * 0.02));
    }

    private ObjectNode diagnosticPayload(Diagnostic d) {
        ObjectNode p = Json.obj();
        p.put("id", d.id());
        p.put("severity", d.severity());
        p.put("code", d.code());
        p.put("message", d.message());
        p.put("sourceRef", d.sourceRef());
        return p;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    void addNode(ObjectNode p, String nodeId, String kind, String label, int version,
                 String ruleVersion, String status, List<String> dependsOn) {
        ArrayNode arr = p.withArray("dependencyNodes");
        ObjectNode n = arr.addObject();
        n.put("nodeId", nodeId);
        n.put("kind", kind);
        n.put("label", label);
        n.put("version", version);
        n.put("ruleVersion", ruleVersion);
        n.put("status", status);
        ArrayNode deps = n.putArray("dependsOn");
        dependsOn.forEach(deps::add);
    }

    private void addRawNode(ObjectNode p, String id, String label, List<String> deps) {
        addNode(p, id, DependencyNode.KIND_RAW, label, 1, RuleVersions.CURRENT,
                DependencyNode.STATUS_OK, deps);
    }

}
