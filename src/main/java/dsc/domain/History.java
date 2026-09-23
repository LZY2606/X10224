package dsc.domain;

import dsc.domain.Entities.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 由事件流折叠出的状态投影。支持重放到任意 seq，恢复当时的来源版本、
 * 基线候选裁定与可见结论；历史结论永远不会被最新计算覆盖。
 */
public final class History {

    /** 某个状态版本（截至某条事件）下的来源集合。 */
    public static final class Snapshot {
        public final long seq;
        public final String eventId;
        public final Map<String, InstrumentOutput> instruments = new LinkedHashMap<>();
        public final Map<String, MassVersion> masses = new LinkedHashMap<>();
        public final Map<String, ScanRate> rates = new LinkedHashMap<>();
        public final Map<String, Atmosphere> atmospheres = new LinkedHashMap<>();
        public final Map<String, TempCalibration> calibrations = new LinkedHashMap<>();
        public final Map<String, Segment> segments = new LinkedHashMap<>();
        public final Map<String, Tangent> tangents = new LinkedHashMap<>();
        /** key -> mass id, value -> 版本号序列（用于质量修订溯源）。 */
        public final Map<String, List<String>> massHistory = new LinkedHashMap<>();
        /** 基线候选裁定：baselineCandidateId -> verdict。 */
        public final Map<String, Verdict> verdicts = new LinkedHashMap<>();
        /** 事件级判定（机器可读取），保留被采纳与未采纳结果。 */
        public final List<Map<String, Object>> judgments = new ArrayList<>();

        Snapshot(long seq, String eventId) {
            this.seq = seq;
            this.eventId = eventId;
        }
    }

    private final List<Event> events = new ArrayList<>();

    public List<Event> events() {
        return Collections.unmodifiableList(events);
    }

    public long lastSeq() {
        return events.isEmpty() ? 0 : events.get(events.size() - 1).seq();
    }

    public void append(Event e) {
        events.add(e);
    }

    /** 全量重放到末尾。 */
    public Snapshot current() {
        return replay(events.size());
    }

    /** 重放前 n 条事件（含），用于回到任意旧版本。 */
    public Snapshot replay(int n) {
        int limit = Math.max(0, Math.min(n, events.size()));
        Snapshot s = new Snapshot(limit == 0 ? 0 : events.get(limit - 1).seq(),
                limit == 0 ? null : events.get(limit - 1).id());
        for (int i = 0; i < limit; i++) {
            fold(s, events.get(i));
        }
        return s;
    }

    /** 重放到指定事件 id（含该事件）。 */
    public Snapshot replayToEvent(String eventId) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).id().equals(eventId)) {
                return replay(i + 1);
            }
        }
        throw new IllegalArgumentException("未知事件: " + eventId);
    }

    private void fold(Snapshot s, Event e) {
        Map<String, Object> p = e.payload();
        switch (e.type()) {
            case "instrument_output" -> {
                @SuppressWarnings("unchecked")
                List<Values.Point> pts = (List<Values.Point>) p.getOrDefault("points", List.of());
                s.instruments.put(e.str("id"), new InstrumentOutput(
                        e.str("id"), e.str("name"), e.str("heatUnit"), e.str("tempUnit"),
                        ExoSign.fromCode(e.str("exoSign")), e.str("encodingNote"), pts));
            }
            case "mass" -> {
                MassVersion m = new MassVersion(e.str("id"), e.num("kg"), e.str("raw"),
                        e.str("note"), (int) e.num("version"));
                s.masses.put(m.id, m);
                s.massHistory.computeIfAbsent(keyOf(e), k -> new ArrayList<>()).add(m.id);
            }
            case "scan_rate" -> s.rates.put(e.str("id"),
                    new ScanRate(e.str("id"), e.num("kPerMin"), e.str("raw")));
            case "atmosphere" -> s.atmospheres.put(e.str("id"),
                    new Atmosphere(e.str("id"), e.str("gas"), e.str("raw")));
            case "temp_calibration" -> s.calibrations.put(e.str("id"),
                    new TempCalibration(e.str("id"), e.num("offsetK"),
                            p.containsKey("slope") ? e.num("slope") : 1.0,
                            e.str("standard"), e.str("raw")));
            case "segment" -> s.segments.put(e.str("id"), new Segment(
                    e.str("id"), e.str("cycle"), (int) e.num("ordinal"),
                    Direction.valueOf(e.str("direction")),
                    e.str("instrumentOutputId"), e.str("scanRateId"),
                    e.str("atmosphereId"), e.str("tempCalibrationId"),
                    e.num("tempRangeLowK"), e.num("tempRangeHighK")));
            case "tangent" -> s.tangents.put(e.str("id"), new Tangent(
                    e.str("id"), e.str("segmentId"), e.str("eventRef"), e.str("kind"),
                    e.num("fromK"), e.num("toK"), e.num("leftAnchorK"), e.num("rightAnchorK")));
            case "judgment" -> {
                Map<String, Object> j = new LinkedHashMap<>(e.reasons());
                j.put("seq", e.seq());
                j.put("eventId", e.id());
                j.put("subject", e.str("subject"));
                if (p.get("verdict") != null) {
                    s.verdicts.put(e.str("subject"), Verdict.fromCode(e.str("verdict")));
                }
                s.judgments.add(j);
            }
            default -> {
                // 未知事件类型保留但不改变投影（旧版本迁移友好）
            }
        }
    }

    private String keyOf(Event e) {
        String k = e.str("sampleKey");
        return k == null ? "default" : k;
    }
}
