package dsc.store;

import dsc.Json;
import dsc.domain.State;

import java.sql.*;
import java.util.*;
import java.util.function.Function;

/**
 * 版本化存储：每次提交产生完整新版本，只有事务完整成功后才切换 current 指针。
 * 写入中断后重启，只能看到旧版本或完整新版本。
 */
public class Store implements AutoCloseable {
    public static final int SCHEMA_VERSION = 2;

    private final Connection conn;
    private Runnable crashHook;

    public Store(String jdbcUrl) {
        try {
            conn = DriverManager.getConnection(jdbcUrl);
            conn.setAutoCommit(true);
            migrate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static Store open(String path) {
        return new Store("jdbc:sqlite:" + path);
    }

    public static Store memory() {
        return new Store("jdbc:sqlite::memory:");
    }

    // ---------- schema & migration ----------

    private void migrate() throws SQLException {
        int v = userVersion();
        if (v < 1) {
            exec("CREATE TABLE IF NOT EXISTS versions(" +
                    "version_id TEXT PRIMARY KEY, parent TEXT, event_id TEXT," +
                    "status TEXT NOT NULL, state_json TEXT NOT NULL, created INTEGER NOT NULL)");
            exec("CREATE TABLE IF NOT EXISTS current(version_id TEXT)");
            exec("CREATE TABLE IF NOT EXISTS events(" +
                    "event_id TEXT PRIMARY KEY, actor TEXT, kind TEXT," +
                    "base_version TEXT, result_version TEXT, payload TEXT, ts INTEGER)");
            setUserVersion(1);
            v = 1;
        }
        if (v < 2) {
            // v2: 事件顺序列 + 冲突表
            if (!columnExists("events", "seq")) {
                exec("ALTER TABLE events ADD COLUMN seq INTEGER");
                exec("UPDATE events SET seq = rowid WHERE seq IS NULL");
            }
            exec("CREATE TABLE IF NOT EXISTS conflicts(" +
                    "conflict_id TEXT PRIMARY KEY, base_version TEXT, ours_version TEXT," +
                    "theirs_version TEXT, ours_state TEXT, theirs_state TEXT," +
                    "status TEXT, resolution TEXT, created INTEGER)");
            setUserVersion(2);
        }
    }

    private int userVersion() throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            return rs.getInt(1);
        }
    }

    private void setUserVersion(int v) throws SQLException {
        exec("PRAGMA user_version = " + v);
    }

    private boolean columnExists(String table, String col) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) if (rs.getString("name").equals(col)) return true;
        }
        return false;
    }

    private void exec(String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    // ---------- reads ----------

    public String currentVersion() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT version_id FROM current")) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public State currentState() {
        String v = currentVersion();
        return v == null ? null : stateAt(v);
    }

    public State stateAt(String versionId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT state_json FROM versions WHERE version_id=? AND status='complete'")) {
            ps.setString(1, versionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return Json.convert(Json.read(rs.getString(1)), State.class);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Map<String, Object>> versions() {
        return query("SELECT version_id, parent, event_id, status, created FROM versions ORDER BY created, version_id");
    }

    public List<Map<String, Object>> events() {
        return query("SELECT event_id, seq, actor, kind, base_version, result_version, payload, ts FROM events ORDER BY seq");
    }

    public List<Map<String, Object>> conflicts() {
        return query("SELECT conflict_id, base_version, ours_version, theirs_version, status, resolution, created FROM conflicts ORDER BY created");
    }

    public Map<String, Object> conflict(String id) {
        List<Map<String, Object>> rows = query(
                "SELECT conflict_id, base_version, ours_version, theirs_version, ours_state, theirs_state, status, resolution FROM conflicts WHERE conflict_id='" + id + "'");
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> query(String sql) {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) row.put(md.getColumnName(i), rs.getObject(i));
                out.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    // ---------- commit ----------

    /** 测试用：在事务中间注入崩溃。 */
    public void setCrashHook(Runnable hook) {
        this.crashHook = hook;
    }

    public static class ConflictException extends RuntimeException {
        public final String conflictId;
        public final String baseVersion;
        public final String currentVersion;

        public ConflictException(String conflictId, String baseVersion, String currentVersion) {
            super("base " + baseVersion + " != current " + currentVersion);
            this.conflictId = conflictId;
            this.baseVersion = baseVersion;
            this.currentVersion = currentVersion;
        }
    }

    /**
     * 乐观并发提交：baseVersion 必须等于当前指针，否则记录冲突并抛 ConflictException。
     * 整个写入在单事务内完成；中途崩溃则全部回滚。
     */
    public synchronized String commit(String baseVersion, String actor, String kind,
                                      Map<String, Object> payload, Function<State, State> fn) {
        String current = currentVersion();
        if (!Objects.equals(current, baseVersion)) {
            String conflictId = "conflict-" + UUID.randomUUID().toString().substring(0, 8);
            State ours = current == null ? null : stateAt(current);
            State base = baseVersion == null ? null : stateAt(baseVersion);
            State theirs = fn.apply(base == null ? new State() : base.copy());
            long now = System.currentTimeMillis();
            execQuiet("INSERT INTO conflicts VALUES('" + conflictId + "'," +
                    q(baseVersion) + "," + q(current) + ",NULL," +
                    q(Json.writeCompact(ours)) + "," + q(Json.writeCompact(theirs)) +
                    ",'open',NULL," + now + ")");
            throw new ConflictException(conflictId, baseVersion, current);
        }
        State next = fn.apply(current == null ? new State() : currentState().copy());
        String eventId = "evt-" + UUID.randomUUID().toString().substring(0, 8);
        String versionId = "v-" + UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();
        try {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO versions VALUES('" + versionId + "'," + q(current) + ",'" + eventId +
                        "','building','" + Json.writeCompact(next).replace("'", "''") + "'," + now + ")");
                if (crashHook != null) crashHook.run();
                st.execute("UPDATE versions SET status='complete' WHERE version_id='" + versionId + "'");
                if (current == null) {
                    st.execute("INSERT INTO current VALUES('" + versionId + "')");
                } else {
                    st.execute("UPDATE current SET version_id='" + versionId + "'");
                }
                int seq = nextSeq(st);
                st.execute("INSERT INTO events VALUES('" + eventId + "'," + seq + "," + q(actor) + "," + q(kind) + "," +
                        q(current) + ",'" + versionId + "'," + q(Json.writeCompact(payload == null ? Map.of() : payload)) + "," + now + ")");
            }
            conn.commit();
        } catch (Exception e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
            }
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
        return versionId;
    }

    private int nextSeq(Statement st) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(seq),0)+1 FROM events")) {
            return rs.getInt(1);
        }
    }

    /** 冲突解决：在当前指针之上提交所选分支，未选分支版本仍保留在 versions 中。 */
    public synchronized String resolveConflict(String conflictId, String actor, String choice) {
        Map<String, Object> c = conflict(conflictId);
        if (c == null) throw new IllegalArgumentException("no conflict " + conflictId);
        String chosenJson = "theirs".equals(choice) ? (String) c.get("theirs_state") : (String) c.get("ours_state");
        State chosen = Json.convert(Json.read(chosenJson), State.class);
        String current = currentVersion();
        String vid = commit(current, actor, "conflict-resolved",
                Map.of("conflictId", conflictId, "choice", choice), s -> chosen.copy());
        execQuiet("UPDATE conflicts SET status='resolved', resolution='" + choice + "' WHERE conflict_id='" + conflictId + "'");
        return vid;
    }

    // ---------- export / import ----------

    /** 导出完整档案：版本、事件、冲突，身份/顺序/来源关系全部保留。 */
    public String exportJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("format", "dsc-archive/1");
        out.put("current", currentVersion());
        out.put("versions", versions());
        List<Map<String, Object>> vs = new ArrayList<>();
        for (Map<String, Object> v : versions()) {
            Map<String, Object> row = new LinkedHashMap<>(v);
            row.put("state", Json.read(stateJsonRaw((String) v.get("version_id"))));
            vs.add(row);
        }
        out.put("versionStates", vs);
        out.put("events", events());
        out.put("conflicts", query("SELECT * FROM conflicts ORDER BY created"));
        return Json.write(out);
    }

    private String stateJsonRaw(String versionId) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT state_json FROM versions WHERE version_id=?")) {
            ps.setString(1, versionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** 导入档案到空库：保持原有 version/event id 与顺序。 */
    public synchronized void importJson(String json) {
        if (currentVersion() != null) throw new IllegalStateException("import requires empty store");
        JsonNode root = Json.read(json);
        try {
            conn.setAutoCommit(false);
            Statement st = conn.createStatement();
            for (JsonNode v : root.get("versionStates")) {
                st.execute("INSERT INTO versions VALUES('" + v.get("version_id").asText() + "'," +
                        q(v.get("parent").isNull() ? null : v.get("parent").asText()) + "," +
                        q(v.get("event_id").isNull() ? null : v.get("event_id").asText()) + ",'" +
                        v.get("status").asText() + "','" +
                        Json.writeCompact(v.get("state")).replace("'", "''") + "'," + v.get("created").asLong() + ")");
            }
            for (JsonNode e : root.get("events")) {
                st.execute("INSERT INTO events VALUES('" + e.get("event_id").asText() + "'," + e.get("seq").asInt() + "," +
                        q(e.get("actor").isNull() ? null : e.get("actor").asText()) + "," +
                        q(e.get("kind").isNull() ? null : e.get("kind").asText()) + "," +
                        q(e.get("base_version").isNull() ? null : e.get("base_version").asText()) + "," +
                        q(e.get("result_version").isNull() ? null : e.get("result_version").asText()) + "," +
                        q(Json.writeCompact(e.get("payload"))) + "," + e.get("ts").asLong() + ")");
            }
            for (JsonNode c : root.get("conflicts")) {
                st.execute("INSERT INTO conflicts VALUES('" + c.get("conflict_id").asText() + "'," +
                        q(textOrNull(c, "base_version")) + "," + q(textOrNull(c, "ours_version")) + "," +
                        q(textOrNull(c, "theirs_version")) + "," + q(textOrNull(c, "ours_state")) + "," +
                        q(textOrNull(c, "theirs_state")) + "," + q(textOrNull(c, "status")) + "," +
                        q(textOrNull(c, "resolution")) + "," + c.get("created").asLong() + ")");
            }
            st.execute("INSERT INTO current VALUES('" + root.get("current").asText() + "')");
            conn.commit();
        } catch (Exception e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
            }
            throw new RuntimeException(e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String q(String s) {
        return s == null ? "NULL" : "'" + s.replace("'", "''") + "'";
    }

    private void execQuiet(String sql) {
        try {
            exec(sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException ignored) {
        }
    }
}
