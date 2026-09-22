package dsc.store;

import dsc.core.ProjectEvent;
import dsc.core.ProjectService;
import dsc.domain.AtmosphereRecord;
import dsc.domain.CalibrationRecord;
import dsc.domain.InstrumentOutput;
import dsc.domain.MassVersion;
import dsc.domain.TangentChoice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把内存 ProjectService 与 SqliteStore 粘起来：
 *  - 保存：自上次保存序号之后的新事件 + 来源文档快照，原子提交；
 *  - 加载：来源文档先还原，再 replayFrom(events) 完整重建；
 *  - 并发：保存时带上客户端基准序号。
 */
public final class Workspace {

    private final SqliteStore store;
    private final ProjectService service = new ProjectService();
    private long lastSavedSeq = 0;

    public Workspace(SqliteStore store) {
        this.store = store;
        load();
    }

    public ProjectService service() {
        return service;
    }

    public long revision() {
        return store.currentSeq();
    }

    public void load() {
        List<SqliteStore.SourceDoc> docs = store.allSourceDocs();
        hydrateSources(docs);
        service.replayFrom(store.loadEvents());
        lastSavedSeq = store.currentSeq();
    }

    /** 保存并返回新修订号。baseSeq 为客户端开始编辑时看到的修订号。 */
    public synchronized long save(long baseSeq) {
        List<ProjectEvent> all = service.eventLog();
        List<Map<String, Object>> fresh = new ArrayList<>();
        for (ProjectEvent ev : all) {
            if (ev.seq > lastSavedSeq) {
                fresh.add(ev.toMap());
            }
        }
        List<SqliteStore.SourceDoc> docs = snapshotSourceDocs();
        store.appendBatch(fresh, docs, baseSeq);
        lastSavedSeq = store.currentSeq();
        return lastSavedSeq;
    }

    public Map<String, Object> exportPackage() {
        return store.exportPackage();
    }

    public void reimportAfterWipe(Map<String, Object> pkg) {
        store.importPackageAfterWipe(pkg);
        load();
    }

    // ---- 来源文档快照/水合（JSON 逐字保留仪器 CSV） ----

    private List<SqliteStore.SourceDoc> snapshotSourceDocs() {
        List<SqliteStore.SourceDoc> out = new ArrayList<>();
        for (InstrumentOutput o : service.outputs.values()) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("outputId", o.outputId);
            doc.put("fileName", o.fileName);
            doc.put("instrumentModel", o.instrumentModel);
            doc.put("directionHeader", o.directionHeader);
            doc.put("tempUnit", o.tempUnit);
            doc.put("heatFlowUnit", o.heatFlowUnit);
            doc.put("rateRaw", o.rateRaw);
            doc.put("atmosphereLabel", o.atmosphereLabel);
            doc.put("calibrationLabel", o.calibrationLabel);
            doc.put("rawCsv", o.rawCsv);
            out.add(new SqliteStore.SourceDoc("instrument", o.outputId, 1, ProjectService.write(doc), ""));
        }
        for (MassVersion m : service.masses.values()) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("massId", m.massId);
            doc.put("version", m.massVersion);
            doc.put("milligrams", m.milligrams);
            doc.put("rawText", m.rawText);
            doc.put("recordedAt", m.recordedAt);
            doc.put("note", m.note);
            out.add(new SqliteStore.SourceDoc("mass", m.massId, m.massVersion, ProjectService.write(doc), ""));
        }
        for (AtmosphereRecord a : service.atmospheres.values()) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("atmosphereId", a.atmosphereId);
            doc.put("gas", a.gas);
            doc.put("flowMlPerMin", a.flowMlPerMin);
            doc.put("rawFlowText", a.rawFlowText);
            doc.put("recordedAt", a.recordedAt);
            out.add(new SqliteStore.SourceDoc("atmosphere", a.atmosphereId, 1, ProjectService.write(doc), ""));
        }
        for (CalibrationRecord c : service.calibrations.values()) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("calibrationId", c.calibrationId);
            doc.put("standard", c.standard);
            doc.put("offsetK", c.offsetK);
            doc.put("rawText", c.rawText);
            doc.put("recordedAt", c.recordedAt);
            out.add(new SqliteStore.SourceDoc("calibration", c.calibrationId, 1,
                    ProjectService.write(doc), ""));
        }
        for (TangentChoice t : service.tangents.values()) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("tangentId", t.tangentId);
            doc.put("segmentId", t.segmentId);
            doc.put("preStartK", t.preStartK);
            doc.put("preEndK", t.preEndK);
            doc.put("postStartK", t.postStartK);
            doc.put("postEndK", t.postEndK);
            doc.put("basis", t.basis);
            doc.put("decidedBy", t.decidedBy);
            out.add(new SqliteStore.SourceDoc("tangent", t.tangentId, 1, ProjectService.write(doc), ""));
        }
        for (Map.Entry<String, String> r : service.scanRates.entrySet()) {
            String[] parts = r.getValue().split("\\|", 2);
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("rateId", r.getKey());
            doc.put("rawText", parts[0]);
            doc.put("kPerMin", Double.parseDouble(parts[1]));
            out.add(new SqliteStore.SourceDoc("rate", r.getKey(), 1, ProjectService.write(doc), ""));
        }
        return out;
    }

    private void hydrateSources(List<SqliteStore.SourceDoc> docs) {
        service.outputs.clear();
        service.masses.clear();
        service.atmospheres.clear();
        service.calibrations.clear();
        service.tangents.clear();
        service.scanRates.clear();
        for (SqliteStore.SourceDoc d : docs) {
            Map<String, Object> doc = read(d.doc);
            switch (d.kind) {
                case "instrument" -> {
                    List<String[]> rows = CsvDocuments.parseRows(str(doc.get("rawCsv")));
                    service.outputs.put((String) doc.get("outputId"), new InstrumentOutput(
                            str(doc.get("outputId")), str(doc.get("fileName")),
                            str(doc.get("instrumentModel")), nullable(doc.get("directionHeader")),
                            str(doc.get("tempUnit")), str(doc.get("heatFlowUnit")),
                            str(doc.get("rateRaw")), str(doc.get("atmosphereLabel")),
                            str(doc.get("calibrationLabel")), str(doc.get("rawCsv")), rows));
                }
                case "mass" -> service.masses.put((String) doc.get("massId"), new MassVersion(
                        str(doc.get("massId")), num(doc.get("version")).intValue(),
                        num(doc.get("milligrams")), str(doc.get("rawText")),
                        str(doc.get("recordedAt")), str(doc.get("note")), null));
                case "atmosphere" -> service.atmospheres.put((String) doc.get("atmosphereId"),
                        new AtmosphereRecord(str(doc.get("atmosphereId")), str(doc.get("gas")),
                                num(doc.get("flowMlPerMin")), str(doc.get("rawFlowText")),
                                str(doc.get("recordedAt"))));
                case "calibration" -> service.calibrations.put((String) doc.get("calibrationId"),
                        new CalibrationRecord(str(doc.get("calibrationId")), str(doc.get("standard")),
                                num(doc.get("offsetK")), str(doc.get("rawText")),
                                str(doc.get("recordedAt"))));
                case "tangent" -> service.tangents.put((String) doc.get("tangentId"),
                        new TangentChoice(str(doc.get("tangentId")), str(doc.get("segmentId")),
                                num(doc.get("preStartK")), num(doc.get("preEndK")),
                                num(doc.get("postStartK")), num(doc.get("postEndK")),
                                str(doc.get("basis")), str(doc.get("decidedBy"))));
                case "rate" -> service.scanRates.put((String) doc.get("rateId"),
                        str(doc.get("rawText")) + "|" + num(doc.get("kPerMin")));
                default -> throw new IllegalStateException("unknown source kind: " + d.kind);
            }
        }
    }

    private static Map<String, Object> read(String json) {
        try {
            return ProjectService.JSON.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String nullable(Object o) {
        return o == null || o.toString().isEmpty() ? null : o.toString();
    }

    private static Double num(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(o.toString());
    }
}
