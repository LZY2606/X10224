package dsc.service;

import dsc.domain.*;
import dsc.store.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Archives the six separately stored direct sources:
 * instrument output, mass, scan rate, atmosphere, temperature calibration and user tangents.
 * Original representations stay in source rows; normalization is a derivation.
 */
public class ImportService {

    private final Repository repo;
    private final SegmentDao segments;
    private final SourceDao sources;
    private final EventDao events;
    private final RuleSetDao rules;
    private final Clock clock;

    public ImportService(Repository repo, Clock clock) {
        this.repo = repo;
        this.segments = new SegmentDao(repo);
        this.sources = new SourceDao(repo);
        this.events = new EventDao(repo);
        this.rules = new RuleSetDao(repo);
        this.clock = clock;
    }

    public static class SegmentImport {
        public String fileName;
        public String content;
        public String cycleId;
        public int order;
    }

    public static class MassImport {
        public String rawText;
        public Double valueMg;
        public String unit;
        public String originRef;
    }

    public static class CalibrationImport {
        public double offsetRaw;
        public String offsetUnit;
        public String rawText;
        public String originRef;
    }

    public String createSample(String code, String material, String note, String actor) {
        Sample s = new Sample(Ids.sid("sample:" + code), code, material, note, clock.now());
        Branch main = new Branch();
        main.id = "main"; main.sampleId = s.id; main.name = "main";
        main.selected = true; main.createdAt = clock.now();
        main.reason = "initial review branch";

        RuleSet rs0 = defaultRules(actor);
        repo.tx(c -> {
            repo.insertSample(s);
            repo.insertBranch(main);
            if (rules.current() == null) rules.insert(rs0);
            EventRecord e = event(s.id, "main", null, Enums.EventKind.SAMPLE_CREATED,
                    "SAMPLE", s.id, 1, actor, Map.of("code", code, "material", material),
                    "sample history created; heating and cooling segments share this 履历");
            events.insert(e);
            return null;
        });
        return s.id;
    }

    public static RuleSet defaultRules(String actor) {
        RuleSet r = new RuleSet();
        r.version = 1; r.current = true;
        r.smoothingWindowC = 2.0;
        r.peakProminenceWg = 0.005;
        r.glassStepMinWg = 0.005;
        r.signPolicy = "direction/sign taken from instrument metadata; never from file names";
        r.createdBy = actor; r.createdAt = 0;
        r.note = "initial ruleset";
        return r;
    }

    /** Imports one instrument output and its companion sources as one atomic archive action. */
    public String importSegment(String sampleId, SegmentImport imp, MassImport mass,
                                CalibrationImport calibration, String atmosphereRaw, String actor) {
        InstrumentFile f = InstrumentParser.parse(imp.fileName, imp.content);
        return repo.tx(c -> {
            long ts = clock.now();
            String segId = Ids.sid("seg:" + sampleId + ":" + imp.order + ":" + f.role + ":" + f.points.size());
            String ioId = Ids.sid("src-io:" + segId);

            Segment seg = new Segment();
            seg.id = segId; seg.sampleId = sampleId; seg.cycleId = imp.cycleId;
            seg.order = imp.order; seg.role = f.role; seg.declaredDirection = f.direction;
            seg.signConvention = f.signConvention;
            seg.tUnit = f.tUnit; seg.hfUnit = f.hfUnit;
            seg.pointCount = f.points.size();
            seg.instrumentOutputId = ioId; seg.createdAt = ts;
            if (!f.points.isEmpty()) {
                seg.tStartRaw = f.points.get(0).tRaw;
                seg.tEndRaw = f.points.get(f.points.size() - 1).tRaw;
            }

            SourceRecord io = source(ioId, sampleId, segId, Enums.SourceKind.INSTRUMENT_OUTPUT, 1,
                    imp.content, f.hfUnit, 0, false,
                    Map.of("fileName", imp.fileName, "instrument", nz(f.instrument),
                           "method", nz(f.method), "parseDiagnostics", f.diagnostics),
                    imp.fileName, actor, ts);

            String rateId = Ids.sid("src-rate:" + segId);
            double rateNum = Double.isNaN(f.declaredRate) ? 0 : f.declaredRate;
            String rateRaw = Double.isNaN(f.declaredRate)
                    ? "# rate: " + nz(f.rateUnit)
                    : "# rate: " + trimRate(f.declaredRate) + " " + nz(f.rateUnit);
            SourceRecord rate = source(rateId, sampleId, segId, Enums.SourceKind.SCAN_RATE, 1,
                    rateRaw, f.rateUnit, rateNum, !Double.isNaN(f.declaredRate),
                    Map.of("fileName", imp.fileName), imp.fileName, actor, ts);

            String atmoId = Ids.sid("src-atmo:" + segId);
            Map<String, Object> atmoPayload = new LinkedHashMap<>();
            atmoPayload.put("gas", nz(f.gas));
            atmoPayload.put("gasFlow", Double.isNaN(f.gasFlow) ? null : f.gasFlow);
            atmoPayload.put("gasFlowUnit", nz(f.gasFlowUnit));
            SourceRecord atmo = source(atmoId, sampleId, segId, Enums.SourceKind.ATMOSPHERE, 1,
                    atmosphereRaw != null ? atmosphereRaw : headerLine(f, "atmosphere", ""),
                    f.gasFlowUnit, Double.isNaN(f.gasFlow) ? 0 : f.gasFlow, !Double.isNaN(f.gasFlow),
                    atmoPayload, imp.fileName, actor, ts);

            String calId = Ids.sid("src-cal:" + segId);
            SourceRecord cal = source(calId, sampleId, segId, Enums.SourceKind.TEMPERATURE_CALIBRATION, 1,
                    calibration.rawText, calibration.offsetUnit, calibration.offsetRaw, true,
                    Map.of("offsetRaw", calibration.offsetRaw, "offsetUnit", nz(calibration.offsetUnit)),
                    calibration.originRef, actor, ts);

            String massId = Ids.sid("src-mass:" + sampleId);
            SourceRecord msrc = source(massId, sampleId, null, Enums.SourceKind.MASS, 1,
                    mass.rawText, mass.unit, mass.valueMg == null ? 0 : mass.valueMg, mass.valueMg != null,
                    Map.of("valueMg", mass.valueMg, "unit", nz(mass.unit)), mass.originRef, actor, ts);

            // tag points with segment + normalize temperature representation at parse time into
            // BOTH raw and normalized columns (raw is authoritative; tC/hfWg are derived cache)
            double massMg = mass.valueMg == null ? Double.NaN : mass.valueMg;
            List<CurvePoint> pts = new ArrayList<>(f.points);
            for (int i = 0; i < pts.size(); i++) {
                CurvePoint p = pts.get(i);
                p.segmentId = segId; p.idx = i;
                OptionalDouble tc = Units.toCelsius(p.tRaw + calibration.offsetRaw,
                        calibration.offsetUnit != null ? calibration.offsetUnit : f.tUnit);
                OptionalDouble hf = Units.toWattsPerGram(p.hfRaw, f.hfUnit, massMg);
                p.tC = tc.orElse(Double.NaN);
                p.hfWg = hf.orElse(Double.NaN);
                p.convertible = tc.isPresent() && hf.isPresent();
            }

            segments.insertSegment(seg);
            segments.insertPoints(segId, pts, 500);
            sources.insert(io); sources.insert(rate); sources.insert(atmo);
            sources.insert(cal); sources.insert(msrc);

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("segmentId", segId);
            detail.put("sources", List.of(ioId, massId, rateId, atmoId, calId));
            detail.put("direction", f.direction.name());
            detail.put("signConvention", f.signConvention.name());
            detail.put("points", f.points.size());
            detail.put("unparsableRows", f.diagnostics);
            EventRecord e = event(sampleId, "main", events.lastEventId("main"),
                    Enums.EventKind.SOURCE_ARCHIVED, "SEGMENT", segId, 1, actor, detail,
                    "instrument output, mass, scan rate, atmosphere and temperature calibration archived separately");
            events.insert(e);
            return segId;
        });
    }

    /** Patches one direct source. Only dependency-graph reachers become stale. */
    public int patchSource(String sampleId, String sourceId, String newRawText, Double numericValue,
                           String unit, String actor, String note) {
        return repo.tx(c -> {
            SourceRecord prev = sources.current(sourceId);
            if (prev == null) throw new IllegalArgumentException("unknown source " + sourceId);
            int v = sources.nextVersion(sourceId);
            SourceRecord nr = new SourceRecord();
            nr.id = sourceId; nr.version = v; nr.sampleId = sampleId;
            nr.segmentId = prev.segmentId; nr.kind = prev.kind; nr.current = true;
            nr.rawText = newRawText; nr.unit = unit;
            nr.numericValue = numericValue == null ? 0 : numericValue;
            nr.numericValid = numericValue != null;
            nr.payloadJson = prev.payloadJson; nr.originRef = prev.originRef;
            nr.createdBy = actor; nr.supersedesId = prev.key(); nr.createdAt = clock.now();
            sources.clearCurrent(sourceId);
            sources.insert(nr);
            repo.bumpSample(sampleId);

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("sourceId", sourceId);
            detail.put("fromVersion", prev.version);
            detail.put("toVersion", v);
            detail.put("kind", prev.kind.name());
            detail.put("segmentId", prev.segmentId);
            EventRecord e = event(sampleId, "main", events.lastEventId("main"),
                    Enums.EventKind.SOURCE_PATCH, "SOURCE", sourceId, v, actor, detail,
                    note != null ? note : "direct source patched; propagation is dependency-scoped");
            events.insert(e);
            return v;
        });
    }

    public int reviseRules(RuleSet next, String actor) {
        return repo.tx(c -> {
            int v = rules.nextVersion();
            rules.clearCurrent();
            next.version = v; next.current = true; next.createdAt = clock.now(); next.createdBy = actor;
            rules.insert(next);
            EventRecord e = event(null, "main", events.lastEventId("main"),
                    Enums.EventKind.PARAM_REVISION, "RULESET", "ruleset", v, actor,
                    Map.of("smoothingWindowC", next.smoothingWindowC,
                           "peakProminenceWg", next.peakProminenceWg,
                           "glassStepMinWg", next.glassStepMinWg),
                    next.note);
            events.insert(e);
            return v;
        });
    }

    private SourceRecord source(String id, String sampleId, String segId, Enums.SourceKind kind,
                                int v, String raw, String unit, double num, boolean numValid,
                                Object payload, String origin, String actor, long ts) {
        SourceRecord s = new SourceRecord();
        s.id = id; s.version = v; s.sampleId = sampleId; s.segmentId = segId; s.kind = kind;
        s.current = true; s.rawText = raw; s.unit = unit;
        s.numericValue = num; s.numericValid = numValid;
        s.payloadJson = Json.write(payload); s.originRef = origin;
        s.createdBy = actor; s.createdAt = ts;
        return s;
    }

    private EventRecord event(String sampleId, String branchId, String parent, Enums.EventKind kind,
                              String subjectType, String subjectId, int subjectVersion,
                              String actor, Object detail, String note) {
        EventRecord e = new EventRecord();
        e.eventId = Ids.sid("evt:" + branchId + ":" + kind + ":" + subjectId + ":" + subjectVersion
                + ":" + clock.now() + ":" + (note == null ? "" : note.hashCode()));
        e.branchId = branchId; e.parentEventId = parent; e.kind = kind;
        e.subjectType = subjectType; e.subjectId = subjectId; e.subjectVersion = subjectVersion;
        e.actor = actor; e.detailJson = Json.write(detail); e.note = note;
        e.createdAt = clock.now();
        return e;
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String trimRate(double d) {
        if (d == Math.rint(d)) return String.valueOf((long) d);
        return String.valueOf(d);
    }
    private static String headerLine(InstrumentFile f, String key, String val) {
        return "# " + key + ": " + val;
    }
}
