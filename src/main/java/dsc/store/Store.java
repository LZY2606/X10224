package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.analysis.Units;
import dsc.util.Ids;
import dsc.util.Json;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 版本化存储：每次推导/裁定产生独立版本，只有完整成功后才切换 current_version。
 * 两位使用者基于同一版本编辑时，后提交者得到可解决的冲突记录。
 */
public final class Store implements AutoCloseable {

    public static final class ConflictException extends RuntimeException {
        public final String conflictId;
        public final long currentVersion;
        public ConflictException(String conflictId, long currentVersion) {
            super("conflict recorded: " + conflictId);
            this.conflictId = conflictId;
            this.currentVersion = currentVersion;
        }
    }

    public static class Row extends LinkedHashMap<String, Object> {}

    private final Db db;
    /** 测试钩子：在事务提交前触发，用于模拟写入中断。 */
    public Runnable beforeCommitHook = () -> {};

    public Store(Path file) { this.db = new Db(file); }
    public Store(Db db) { this.db = db; }

    @Override public void close() { db.close(); }
    public Db db() { return db; }

    // ---------- 查询 ----------

    public List<Row> q(String sql, Object... args) {
        try (PreparedStatement ps = db.conn().prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                List<Row> out = new ArrayList<>();
                var md = rs.getMetaData();
                while (rs.next()) {
                    Row r = new Row();
                    for (int c = 1; c <= md.getColumnCount(); c++) r.put(md.getColumnLabel(c), rs.getObject(c));
                    out.add(r);
                }
                return out;
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Optional<Row> one(String sql, Object... args) {
        List<Row> r = q(sql, args);
        return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
    }

    public long currentVersion() {
        return Long.parseLong((String) one("SELECT value FROM meta WHERE key='current_version'").orElseThrow().get("value"));
    }

    // ---------- 提交 ----------

    public interface Writes { void run() throws Exception; }

    /** 原子提交：检查基线版本 -> 执行写入 -> 追加事件 -> 最后切换版本指针。 */
    public synchronized String commit(long baseVersion, String actor, String type, ObjectNode payload, Writes writes) {
        long cur = currentVersion();
        if (baseVersion != cur) {
            String cid = Ids.newId();
            ObjectNode cp = Json.obj();
            cp.put("type", type);
            cp.set("payload", payload);
            exec("INSERT INTO conflicts(id,base_version,current_version,actor,type,payload,status,created_version) VALUES(?,?,?,?,?,?,?,?)",
                    cid, baseVersion, cur, actor, type, cp.toString(), "open", cur);
            throw new ConflictException(cid, cur);
        }
        String eventId = Ids.newId();
        long nv = cur + 1;
        try {
            db.conn().setAutoCommit(false);
            try {
                writes.run();
                long seq = ((Number) one("SELECT COALESCE(MAX(seq),0)+1 AS s FROM events").get("s")).longValue();
                exec("INSERT INTO events(id,seq,ts,actor,type,payload,base_version,result_version) VALUES(?,?,?,?,?,?,?,?)",
                        eventId, seq, Instant.now().toString(), actor, type, payload.toString(), baseVersion, nv);
                exec("UPDATE meta SET value=? WHERE key='current_version'", Long.toString(nv));
                beforeCommitHook.run();
                db.conn().commit();
            } catch (Exception e) {
                db.conn().rollback();
                if (e instanceof RuntimeException re) throw re;
                throw new RuntimeException(e);
            } finally {
                db.conn().setAutoCommit(true);
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return eventId;
    }

    public void exec(String sql, Object... args) {
        try (PreparedStatement ps = db.conn().prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    // ---------- 领域操作（操作即数据，冲突可重放） ----------

    public String apply(long base, String actor, String type, ObjectNode payload) {
        return commit(base, actor, type, payload, () -> applyOp(type, payload));
    }

    /** 系统派生提交：始终基于当前版本。 */
    public String applySystem(String type, ObjectNode payload, Writes writes) {
        return commit(currentVersion(), "system", type, payload, writes);
    }

    private void applyOp(String type, ObjectNode p) throws Exception {
        switch (type) {
            case "sample.create" -> exec("INSERT INTO samples(id,name,created_version) VALUES(?,?,?)",
                    p.get("sample_id").asText(), p.get("name").asText(), currentVersion() + 1);
            case "run.import" -> applyRunImport(p);
            case "mass.revise" -> applyMassRevise(p);
            case "calibration.add" -> exec("INSERT INTO calibrations(id,run_id,kind,coeffs,created_version) VALUES(?,?,?,?,?)",
                    p.get("calibration_id").asText(), p.get("run_id").asText(), p.get("kind").asText(),
                    p.get("coeffs").toString(), currentVersion() + 1);
            case "tangents.select" -> exec("INSERT INTO tangents(id,segment_id,author,left_x,right_x,created_version) VALUES(?,?,?,?,?,?)",
                    p.get("tangent_id").asText(), p.get("segment_id").asText(), p.get("author").asText(),
                    p.get("left_x").asDouble(), p.get("right_x").asDouble(), currentVersion() + 1);
            case "interval.set" -> exec("INSERT INTO intervals(id,segment_id,x0,x1,author,created_version) VALUES(?,?,?,?,?,?)",
                    p.get("interval_id").asText(), p.get("segment_id").asText(), p.get("x0").asDouble(),
                    p.get("x1").asDouble(), p.get("author").asText(), currentVersion() + 1);
            case "baseline.adopt" -> {
                String intervalId = one("SELECT interval_id FROM baselines WHERE id=?", p.get("baseline_id").asText()).orElseThrow().get("interval_id").toString();
                exec("UPDATE baselines SET status='candidate' WHERE interval_id=? AND status='adopted'", intervalId);
                exec("UPDATE baselines SET status='adopted' WHERE id=?", p.get("baseline_id").asText());
            }
            case "baseline.reject" -> exec("UPDATE baselines SET status='rejected' WHERE id=?", p.get("baseline_id").asText());
            case "points.replace" -> {
                String segId = p.get("segment_id").asText();
                exec("DELETE FROM points WHERE segment_id=?", segId);
                insertPoints(segId, p.withArray("points"), currentVersion() + 1);
            }
            case "conflict.resolve" -> exec("UPDATE conflicts SET status='resolved', resolution=? WHERE id=?",
                    p.get("resolution").asText(), p.get("conflict_id").asText());
            default -> throw new IllegalArgumentException("unknown op " + type);
        }
    }

    private void applyRunImport(ObjectNode p) throws Exception {
        long v = currentVersion() + 1;
        exec("INSERT INTO runs(id,sample_id,source_file,instrument,sign_convention,sign_source,raw_meta,created_version) VALUES(?,?,?,?,?,?,?,?)",
                p.get("run_id").asText(), p.get("sample_id").asText(), p.get("source_file").asText(),
                p.get("instrument").asText(), p.get("sign_convention").asText(), p.get("sign_source").asText(),
                p.get("raw_meta").toString(), v);
        for (JsonNode seg : p.withArray("segments")) {
            String segId = seg.get("segment_id").asText();
            Units.Parsed rate = Units.rateToKPerS(seg.get("rate_raw").asText(), seg.get("rate_unit").asText());
            exec("INSERT INTO segments(id,run_id,ordinal,kind,rate_raw,rate_unit,rate_k_per_s,atmosphere,t_start,t_end,created_version) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    segId, p.get("run_id").asText(), seg.get("ordinal").asInt(), seg.get("kind").asText(),
                    seg.get("rate_raw").asText(), seg.get("rate_unit").asText(),
                    rate.value == null ? null : rate.value,
                    seg.hasNonNull("atmosphere") ? seg.get("atmosphere").asText() : null,
                    seg.get("t_start").asDouble(), seg.get("t_end").asDouble(), v);
            insertPoints(segId, seg.withArray("points"), v);
        }
    }

    private void insertPoints(String segId, ArrayNode points, long v) {
        for (JsonNode pt : points) {
            exec("INSERT INTO points(segment_id,seq,t_raw,temp_raw,hf_raw,temp_c,hf_mw) VALUES(?,?,?,?,?,?,?)",
                    segId, pt.get("seq").asInt(), pt.get("t_raw").asText(), pt.get("temp_raw").asText(),
                    pt.get("hf_raw").asText(),
                    pt.hasNonNull("temp_c") ? pt.get("temp_c").asDouble() : null,
                    pt.hasNonNull("hf_mw") ? pt.get("hf_mw").asDouble() : null);
        }
    }

    private void applyMassRevise(ObjectNode p) {
        Units.Parsed mg = Units.massToMg(p.get("value_raw").asText(), p.get("unit").asText());
        exec("INSERT INTO mass_versions(id,sample_id,version_no,value_raw,unit,normalized_mg,status,created_version) VALUES(?,?,?,?,?,?,?,?)",
                p.get("mass_id").asText(), p.get("sample_id").asText(), p.get("version_no").asInt(),
                p.get("value_raw").asText(), p.get("unit").asText(),
                mg.value == null ? null : mg.value,
                mg.ok() ? "active" : "unconverted", currentVersion() + 1);
    }

    // ---------- 冲突解决 ----------

    /** 解决冲突：accept = 以当前版本为基线重放被拒操作；reject = 仅关闭。 */
    public String resolveConflict(String conflictId, String actor, String choice) {
        Row c = one("SELECT * FROM conflicts WHERE id=?", conflictId).orElseThrow();
        if (!"open".equals(c.get("status"))) throw new IllegalStateException("conflict already resolved");
        String eventId;
        if ("accept".equals(choice)) {
            try {
                ObjectNode cp = (ObjectNode) Json.M.readTree((String) c.get("payload"));
                eventId = apply(currentVersion(), actor, cp.get("type").asText(), (ObjectNode) cp.get("payload"));
            } catch (ConflictException | RuntimeException e) {
                throw new RuntimeException("re-apply failed", e);
            } catch (Exception e) { throw new RuntimeException(e); }
        } else {
            eventId = null;
        }
        ObjectNode p = Json.obj();
        p.put("conflict_id", conflictId);
        p.put("resolution", choice);
        String closeEvent = apply(currentVersion(), actor, "conflict.resolve", p);
        return eventId != null ? eventId : closeEvent;
    }

    // ---------- 导出 / 导入（身份、顺序、来源关系保持） ----------

    private static final List<String> TABLES = List.of("meta", "samples", "mass_versions", "runs", "segments",
            "points", "calibrations", "tangents", "intervals", "baselines", "derived", "events", "conflicts");

    public ObjectNode exportJson() {
        ObjectNode out = Json.obj();
        out.put("format", "dsc-review-export");
        out.put("schema_version", Db.SCHEMA_VERSION);
        out.put("current_version", currentVersion());
        ObjectNode tables = out.putObject("tables");
        for (String t : TABLES) {
            if (t.equals("meta")) continue;
            ArrayNode arr = tables.putArray(t);
            String orderBy = switch (t) {
                case "points" -> " ORDER BY segment_id, seq";
                case "segments" -> " ORDER BY run_id, ordinal";
                case "events" -> " ORDER BY seq";
                case "derived" -> " ORDER BY node_key, version";
                default -> " ORDER BY rowid";
            };
            for (Row r : q("SELECT * FROM " + t + orderBy)) {
                ObjectNode o = arr.addObject();
                r.forEach((k, v) -> {
                    if (v == null) o.putNull(k);
                    else if (v instanceof Number n) {
                        if (n instanceof Double || n instanceof Float) o.put(k, n.doubleValue());
                        else o.put(k, n.longValue());
                    } else o.put(k, v.toString());
                });
            }
        }
        return out;
    }

    public void importJson(ObjectNode snap) {
        if (!"dsc-review-export".equals(snap.get("format").asText()))
            throw new IllegalArgumentException("not a dsc-review export");
        ObjectNode tables = (ObjectNode) snap.get("tables");
        commit(currentVersion(), "system", "store.import", Json.obj().put("source_version", snap.get("current_version").asLong()), () -> {
            try (Statement st = db.conn().createStatement()) {
                for (String t : TABLES) if (!t.equals("meta")) st.executeUpdate("DELETE FROM " + t);
            }
            for (String t : TABLES) {
                if (t.equals("meta") || !tables.has(t)) continue;
                for (JsonNode row : tables.withArray(t)) {
                    StringBuilder cols = new StringBuilder(), marks = new StringBuilder();
                    List<Object> vals = new ArrayList<>();
                    row.fields().forEachRemaining(e -> {
                        cols.append(cols.isEmpty() ? "" : ",").append(e.getKey());
                        marks.append(marks.isEmpty() ? "" : ",").append("?");
                        vals.add(e.getValue().isNull() ? null :
                                e.getValue().isNumber() ? e.getValue().numberValue() : e.getValue().asText());
                    });
                    exec("INSERT INTO " + t + "(" + cols + ") VALUES(" + marks + ")", vals.toArray());
                }
            }
            exec("UPDATE meta SET value=? WHERE key='current_version'", Long.toString(currentVersion() + 1));
        });
    }
}
