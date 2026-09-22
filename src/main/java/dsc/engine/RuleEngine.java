package dsc.engine;

import dsc.Ids;
import dsc.model.AtmosphereRecord;
import dsc.model.BaselineCandidate;
import dsc.model.Branch;
import dsc.model.CalibrationRecord;
import dsc.model.Decision;
import dsc.model.Diagnostic;
import dsc.model.DomainEvent;
import dsc.model.Integration;
import dsc.model.Interval;
import dsc.model.Judgment;
import dsc.model.MassRecord;
import dsc.model.ProjectState;
import dsc.model.RateRecord;
import dsc.model.RawSeries;
import dsc.model.Segment;
import dsc.model.SourceBlob;
import dsc.model.TangentSelection;
import dsc.model.ThermalEvent;
import dsc.units.Quantity;
import dsc.units.Units;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RuleEngine {
    private final ProjectState s;
    private final EventSink sink;
    private String actor = "reviewer";

    public RuleEngine(ProjectState state, EventSink sink) {
        this.s = state;
        this.sink = sink;
    }

    public EngineOutcome apply(Commands.Cmd cmd) {
        this.actor = cmd.actor() == null ? actor : cmd.actor();
        List<DomainEvent> out = new ArrayList<>();
        String conflict = null;
        switch (cmd.type()) {
            case Commands.OPEN_PROJECT -> out.addAll(openProject(cmd));
            case Commands.ARCHIVE_AND_IMPORT -> out.addAll(archiveAndImport(cmd));
            case Commands.SET_MASS -> out.addAll(setMass(cmd));
            case Commands.SET_CALIBRATION -> out.addAll(setCalibration(cmd));
            case Commands.SET_ATMOSPHERE -> out.addAll(setAtmosphere(cmd));
            case Commands.PICK_TANGENT -> out.addAll(pickTangent(cmd));
            case Commands.DRAW_INTERVAL -> out.addAll(drawInterval(cmd));
            case Commands.ADJUST_INTERVAL -> out.addAll(adjustInterval(cmd));
            case Commands.MAKE_DECISION -> {
                var res = makeDecision(cmd);
                out.addAll(res.events());
                conflict = res.conflictWithEvent();
            }
            default -> throw new IllegalArgumentException("unknown command: " + cmd.type());
        }
        return new EngineOutcome(out, conflict);
    }

    private List<DomainEvent> openProject(Commands.Cmd cmd) {
        Map<String, Object> p = cmd.args();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", p.getOrDefault("projectId", Ids.hash("prj", "dsc-case-" + Instant.now())));
        payload.put("name", p.getOrDefault("name", "热转折案"));
        payload.put("ruleVersion", DerivationRunner.RULE_VERSION);
        DomainEvent ev = sink.emit(DomainEvent.PROJECT_OPENED, payload);
        return List.of(ev);
    }

    private List<DomainEvent> archiveAndImport(Commands.Cmd cmd) {
        Map<String, Object> a = cmd.args();
        List<DomainEvent> out = new ArrayList<>();
        String fileName = str(a, "fileName");
        String rawText = str(a, "rawText");
        @SuppressWarnings("unchecked")
        Map<String, String> meta = (Map<String, String>) a.getOrDefault("metadata", Map.of());
        Map<String, String> metadata = new LinkedHashMap<>(meta);
        String blobId = str(a, "blobId", null);
        if (blobId == null) {
            blobId = Ids.hash("blob", fileName + "|" + sha(rawText));
        }
        List<List<String>> rows = dsc.Csv.parse(rawText);
        List<String> headers = rows.isEmpty() ? List.of() : rows.get(0);
        SourceBlob blob = new SourceBlob(blobId, fileName, "text/csv", sha(rawText), rawText, metadata, headers, rows);
        DomainEvent blobEv = sink.emit(DomainEvent.BLOB_ARCHIVED, Map.of("blob", Codecs.toMap(blob)));
        out.add(blobEv);
        applyToState(blobEv);

        String segmentId = str(a, "segmentId", null);
        if (segmentId == null) {
            segmentId = Ids.hash("seg", blobId);
        }
        RawSeriesParser.ParseResult parsed = new RawSeriesParser().parse(segmentId, blob);
        if (parsed.series == null) {
            DomainEvent bad = sink.emit(DomainEvent.DIAGNOSTIC_RAISED, Map.of(
                    "diag", Codecs.toMap(diag("diag-import-" + blobId.substring(0, 10), "IMPORT_UNREADABLE",
                            Diagnostic.ERROR, "blob", blobId, "仪器输出无法解析，原始存档保留，不产生曲线",
                            String.join(";", parsed.diagnostics), sink.nextSeq()))));
            out.add(bad);
            applyToState(bad);
            return out;
        }
        RawSeries series = parsed.series;
        String measuredMode = inferMode(series);
        String declaredMode = metadata.getOrDefault("declaredMode", "");
        String label = str(a, "label", fileName);
        String cycleTag = metadata.getOrDefault("cycleTag", "cycle-1");
        Segment segment = new Segment(segmentId, s.segments.size(), label, measuredMode, cycleTag, blobId,
                blobEv.eventId());
        DomainEvent segEv = sink.emit(DomainEvent.SEGMENT_IMPORTED,
                Map.of("segment", Codecs.toMap(segment), "series", Codecs.toMap(series),
                        "declaredMode", declaredMode), blobEv.eventId());
        out.add(segEv);
        applyToState(segEv);

        double rate = measuredRate(series);
        Quantity declaredRate = Units.parse(metadata.getOrDefault("declaredRate", ""), Quantity.RATE, null);
        RateRecord rateRecord = new RateRecord(segmentId,
                Double.isFinite(rate) ? new Quantity(rate, String.format(Locale.ROOT, "%.6f K/s", rate), "K/s",
                        Quantity.RATE, true, null) : Quantity.unconvertible("", Quantity.RATE, "no-time-column"),
                declaredRate, "measured-from-raw", segEv.eventId());
        out.add(emitSimple(DomainEvent.RATE_MEASURED, Map.of("rate", Codecs.toMap(rateRecord))));

        if (fileNameDirectionGuess(fileName) != null
                && !fileNameDirectionGuess(fileName).equals(measuredMode)) {
            Diagnostic d = diag("diag-filename-" + segmentId, "FILENAME_DIRECTION_MISMATCH", Diagnostic.WARNING,
                    "segment", segmentId,
                    "文件名暗示的扫描方向与实测温度斜率相反；方向以数据与标定为准，禁止按文件名猜测",
                    "file=" + fileName + ", measured=" + measuredMode, sink.nextSeq());
            DomainEvent dev = sink.emit(DomainEvent.DIAGNOSTIC_RAISED, Map.of("diag", Codecs.toMap(d)));
            out.add(dev);
            applyToState(dev);
            Judgment j = judge("J-HEAT-COOL-NOT-GUESS", Judgment.SATISFIED, "segment", segmentId,
                    "吸放热方向不能靠文件名猜测：已按温度斜率确定为 " + measuredMode + "，文件名为反例",
                    List.of(segmentId), List.of(d.diagId()), blobEv.eventId());
            DomainEvent jev = sink.emit(DomainEvent.JUDGMENT_APPENDED, Map.of("judgment", Codecs.toMap(j)));
            out.add(jev);
            applyToState(jev);
        }
        if (!declaredMode.isBlank() && !declaredMode.equalsIgnoreCase(measuredMode)) {
            Diagnostic d = diag("diag-mode-" + segmentId, "DECLARED_MODE_CONFLICT", Diagnostic.ERROR,
                    "segment", segmentId,
                    "元数据声明的加热/冷却方向与实测斜率冲突；该段标记为方向存疑，禁止与反向段混用",
                    "declared=" + declaredMode + ", measured=" + measuredMode, sink.nextSeq());
            DomainEvent dev = sink.emit(DomainEvent.DIAGNOSTIC_RAISED, Map.of("diag", Codecs.toMap(d)));
            out.add(dev);
            applyToState(dev);
        }
        if (RawSeries.EXO_UP.equalsIgnoreCase(series.convention())) {
            Judgment j = judge("J-REVERSE-HEATFLOW", Judgment.SATISFIED, "segment", segmentId,
                    "反向热流（exo-up）已在归一化层翻转到 endo-up；原始符号与约定原样保留",
                    List.of(segmentId, blobId), List.of(), blobEv.eventId());
            DomainEvent jev = sink.emit(DomainEvent.JUDGMENT_APPENDED, Map.of("judgment", Codecs.toMap(j)));
            out.add(jev);
            applyToState(jev);
        }
        return out;
    }
    // section-2
}
