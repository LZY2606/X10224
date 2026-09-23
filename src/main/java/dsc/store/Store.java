package dsc.store;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 版本化存储：仪器输出、样品质量、扫描速率、气氛、温度标定、用户切线分别存档；
 * 每次推导产生独立版本，只有完整成功后才切换当前指针。
 * 进程在写入中断后重启：要么看到旧版本，要么看到完整新版本（INCOMPLETE 行在启动时清除）。
 */
public class Store implements AutoCloseable {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private static final int SCHEMA_VERSION = 2;

    private final Connection conn;

    public record Version(String id, String sampleId, String category, String key, int version,
                          String status, Map<String, String> deps, Map<String, Object> payload, String createdAt) {}

    public record Event(long seq, String id, String sampleId, String type,
                        Map<String, Object> payload, Map<String, String> visible, String createdAt) {}

    public static class Conflict extends Exception {
        public final Version current;
        public Conflict(Version current) {
            super("base version mismatch");
            this.current = current;
        }
    }

    public Store(Path dbFile) throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
        conn.setAutoCommit(true);
        migrate();
        recover();
    }

    /** 旧版本迁移：按 PRAGMA user_version 逐步升级。 */
    private void migrate() throws SQLException {
        int v = conn.createStatement().executeQuery("PRAGMA user_version").getInt(1);
        if (v < 1) {
            exec("CREATE TABLE IF NOT EXISTS versions(" +
                    "id TEXT PRIMARY KEY, sample_id TEXT, category TEXT, node_key TEXT, version INT," +
                    "status TEXT, deps TEXT, payload TEXT, created_at TEXT)");
            exec("CREATE TABLE IF NOT EXISTS current_ptr(" +
                    "sample_id TEXT, category TEXT, node_key TEXT, version_id TEXT," +
                    "PRIMARY KEY(sample_id, category, node_key))");
            exec("CREATE TABLE IF NOT EXISTS events(" +
                    "seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT, sample_id TEXT, type TEXT," +
                    "payload TEXT, visible TEXT, created_at TEXT)");
            exec("CREATE TABLE IF NOT EXISTS samples(id TEXT PRIMARY KEY, name TEXT, created_at TEXT)");
            setUserVersion(1);
        }
        if (v < 2) {
            // v2：事件增加 parent 列（反查哪次决定造成了差异）
            exec("ALTER TABLE events ADD COLUMN parent TEXT");
            setUserVersion(2);
        }
    }

    /** 崩溃恢复：清除写入中断留下的不完整版本；当前指针从不指向它们。 */
    private void recover() throws SQLException {
        exec("DELETE FROM versions WHERE status='INCOMPLETE'");
    }

    private void exec(String sql) throws SQLException {
        try (Statement st = conn.createStatement()) { st.execute(sql); }
    }

    private void setUserVersion(int v) throws SQLException {
        exec("PRAGMA user_version=" + v);
    }

    private static String now() {
        return java.time.Instant.now().toString();
    }

    public static String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ---------- samples ----------

    public void createSample(String id, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO samples(id,name,created_at) VALUES(?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, now());
            ps.executeUpdate();
        }
    }

    public List<Map<String, Object>> samples() throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id,name,created_at FROM samples ORDER BY created_at")) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getString(1));
                m.put("name", rs.getString(2));
                m.put("createdAt", rs.getString(3));
                out.add(m);
            }
        }
        return out;
    }

    // ---------- versions ----------

    /**
     * 原子写入：同一事务内插入 COMPLETE 版本并切换当前指针。
     * 只有完整成功后指针才移动；中断时事务回滚，看到的仍是旧版本。
     */
    public Version putVersion(String sampleId, String category, String key,
                              Map<String, String> deps, Map<String, Object> payload) throws SQLException {
        String id = newId("v");
        int next = nextVersionNumber(sampleId, category, key);
        conn.setAutoCommit(false);
        try {
            insertVersion(id, sampleId, category, key, next, "COMPLETE", deps, payload);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO current_ptr(sample_id,category,node_key,version_id) VALUES(?,?,?,?) " +
                            "ON CONFLICT(sample_id,category,node_key) DO UPDATE SET version_id=excluded.version_id")) {
                ps.setString(1, sampleId);
                ps.setString(2, category);
                ps.setString(3, key);
                ps.setString(4, id);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
        return get(id).orElseThrow();
    }

    /** 测试钩子：模拟写入中断——提交一条 INCOMPLETE 版本但不移动指针。 */
    public String putIncomplete(String sampleId, String category, String key, Map<String, Object> payload) throws SQLException {
        String id = newId("v");
        insertVersion(id, sampleId, category, key, nextVersionNumber(sampleId, category, key),
                "INCOMPLETE", Map.of(), payload);
        return id;
    }

    private int nextVersionNumber(String sampleId, String category, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(MAX(version),0)+1 FROM versions WHERE sample_id=? AND category=? AND node_key=?")) {
            ps.setString(1, sampleId);
            ps.setString(2, category);
            ps.setString(3, key);
            return ps.executeQuery().getInt(1);
        }
    }

    private void insertVersion(String id, String sampleId, String category, String key, int version,
                               String status, Map<String, String> deps, Map<String, Object> payload) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO versions(id,sample_id,category,node_key,version,status,deps,payload,created_at) " +
                        "VALUES(?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, sampleId);
            ps.setString(3, category);
            ps.setString(4, key);
            ps.setInt(5, version);
            ps.setString(6, status);
            ps.setString(7, GSON.toJson(deps));
            ps.setString(8, GSON.toJson(payload));
            ps.setString(9, now());
            ps.executeUpdate();
        }
    }

    public Optional<Version> get(String id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id,sample_id,category,node_key,version,status,deps,payload,created_at FROM versions WHERE id=?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return Optional.empty();
            return Optional.of(rowToVersion(rs));
        }
    }

    public Optional<Version> current(String sampleId, String category, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT version_id FROM current_ptr WHERE sample_id=? AND category=? AND node_key=?")) {
            ps.setString(1, sampleId);
            ps.setString(2, category);
            ps.setString(3, key);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return Optional.empty();
            return get(rs.getString(1));
        }
    }

    public List<String> keys(String sampleId, String category) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT node_key FROM versions WHERE sample_id=? AND category=? ORDER BY node_key")) {
            ps.setString(1, sampleId);
            ps.setString(2, category);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    public List<Version> history(String sampleId, String category, String key) throws SQLException {
        List<Version> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id,sample_id,category,node_key,version,status,deps,payload,created_at FROM versions " +
                        "WHERE sample_id=? AND category=? AND node_key=? ORDER BY version")) {
            ps.setString(1, sampleId);
            ps.setString(2, category);
            ps.setString(3, key);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(rowToVersion(rs));
        }
        return out;
    }

    public List<Version> currentVersions(String sampleId) throws SQLException {
        List<Version> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT version_id FROM current_ptr WHERE sample_id=? ORDER BY category, node_key")) {
            ps.setString(1, sampleId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) get(rs.getString(1)).ifPresent(out::add);
        }
        return out;
    }

    private Version rowToVersion(ResultSet rs) throws SQLException {
        Map<String, String> deps = GSON.fromJson(rs.getString(7), new TypeToken<Map<String, String>>() {}.getType());
        Map<String, Object> payload = GSON.fromJson(rs.getString(8), MAP_TYPE);
        return new Version(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getInt(5), rs.getString(6),
                deps == null ? Map.of() : deps, payload == null ? Map.of() : payload, rs.getString(9));
    }

    /**
     * 基于基准版本的冲突检查写入：两位使用者从同一版本编辑时，
     * 后写者得到 Conflict，冲突携带当前版本，可解决后重提。
     */
    public Version saveWithBase(String sampleId, String category, String key,
                                Map<String, Object> payload, String baseVersionId)
            throws SQLException, Conflict {
        Optional<Version> cur = current(sampleId, category, key);
        String curId = cur.map(Version::id).orElse(null);
        if (curId != null && !curId.equals(baseVersionId)) {
            throw new Conflict(cur.get());
        }
        return putVersion(sampleId, category, key, cur.map(Version::deps).orElse(Map.of()), payload);
    }

    // ---------- events（机器可读，可回放） ----------

    /** 追加事件并捕获当时的可见指针快照；回到任意旧事件时可恢复当时的依赖版本与可见结论。 */
    public Event appendEvent(String sampleId, String type, Map<String, Object> payload, String parent) throws SQLException {
        Map<String, String> visible = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT category,node_key,version_id FROM current_ptr WHERE sample_id=?")) {
            ps.setString(1, sampleId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) visible.put(rs.getString(1) + "|" + rs.getString(2), rs.getString(3));
        }
        String id = newId("e");
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO events(id,sample_id,type,payload,visible,created_at,parent) VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, sampleId);
            ps.setString(3, type);
            ps.setString(4, GSON.toJson(payload));
            ps.setString(5, GSON.toJson(visible));
            ps.setString(6, now());
            ps.setString(7, parent);
            ps.executeUpdate();
        }
        return events(sampleId).stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    public List<Event> events(String sampleId) throws SQLException {
        List<Event> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT seq,id,sample_id,type,payload,visible,created_at FROM events WHERE sample_id=? ORDER BY seq")) {
            ps.setString(1, sampleId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> payload = GSON.fromJson(rs.getString(5), MAP_TYPE);
                Map<String, String> visible = GSON.fromJson(rs.getString(6), new TypeToken<Map<String, String>>() {}.getType());
                out.add(new Event(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        payload == null ? Map.of() : payload,
                        visible == null ? Map.of() : visible, rs.getString(7)));
            }
        }
        return out;
    }

    /** 回放：返回某事件时刻可见的全部版本（身份、顺序与来源关系保持）。 */
    public Map<String, Version> visibleAt(String sampleId, long seq) throws SQLException {
        Event ev = events(sampleId).stream().filter(e -> e.seq() == seq).findFirst().orElseThrow();
        Map<String, Version> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> en : ev.visible().entrySet()) {
            get(en.getValue()).ifPresent(v -> out.put(en.getKey(), v));
        }
        return out;
    }

    /** 来源链：从任一版本沿依赖反查。 */
    public Map<String, Object> provenance(String versionId) throws SQLException {
        Version v = get(versionId).orElseThrow();
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", v.id());
        node.put("category", v.category());
        node.put("key", v.key());
        node.put("version", v.version());
        List<Map<String, Object>> deps = new ArrayList<>();
        for (Map.Entry<String, String> d : v.deps().entrySet()) {
            if (d.getValue() == null) continue;
            try {
                deps.add(provenance(d.getValue()));
            } catch (Exception e) {
                deps.add(Map.of("id", d.getValue(), "missing", true));
            }
        }
        node.put("deps", deps);
        return node;
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }
}
