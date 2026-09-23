package dsc.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import dsc.domain.Event;
import dsc.domain.History;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite 事件存储。
 *
 * 原子性：每个事件在单个事务里写 event 行 + 更新 current 指针（meta）。
 * 进程在写入中断后重启，要么看到旧版本，要么看到完整新版本（SQLite 事务保证）。
 *
 * 并发：append 带 expectedVersion（乐观锁）。两位使用者基于同一版本编辑时，
 * 第二个提交得到 ConflictException，可由调用方解决后重试。
 */
public final class Store implements AutoCloseable {

    public static final class ConflictException extends RuntimeException {
        public ConflictException(String msg) {
            super(msg);
        }
    }

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private final String url;
    private final Object writeLock = new Object();
    private final ObjectMapper mapper = new ObjectMapper();

    public Store(String path) {
        this.url = "jdbc:sqlite:" + path;
        init();
    }

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA busy_timeout=5000");
        }
        return c;
    }

    private void init() {
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.executeUpdate("PRAGMA user_version");
            migrate(c);
        } catch (SQLException e) {
            throw new RuntimeException("初始化数据库失败", e);
        }
    }

    /** 旧版本迁移：按 user_version 顺序向前。 */
    private void migrate(Connection c) throws SQLException {
        int v;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            rs.next();
            v = rs.getInt(1);
        }
        if (v < 1) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS events (
                          seq INTEGER PRIMARY KEY AUTOINCREMENT,
                          id TEXT NOT NULL UNIQUE,
                          type TEXT NOT NULL,
                          parent_id TEXT,
                          base_version TEXT,
                          author TEXT,
                          ts TEXT NOT NULL,
                          payload TEXT NOT NULL,
                          reasons TEXT NOT NULL
                        )""");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS meta (
                          key TEXT PRIMARY KEY,
                          value TEXT NOT NULL
                        )""");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS locks (
                          scope TEXT PRIMARY KEY,
                          version INTEGER NOT NULL
                        )""");
                st.executeUpdate("INSERT OR IGNORE INTO meta(key,value) VALUES('current_seq','0')");
                st.executeUpdate("INSERT OR IGNORE INTO locks(scope,version) VALUES('case',0)");
                st.executeUpdate("PRAGMA user_version=1");
            }
        }
        // 未来迁移在此追加：if (v < 2) { ... PRAGMA user_version=2 }
    }

    public int currentVersion() {
        try (Connection c = conn();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM locks WHERE scope='case'")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 追加事件（乐观并发）。
     * @param expectedVersion 调用方基于的版本；与当前不一致则抛 ConflictException
     * @return 新版本号
     */
    public long append(String type, String id, String parentId, String baseVersion, String author,
                       Map<String, Object> payload, Map<String, Object> reasons,
                       long expectedVersion) {
        synchronized (writeLock) {
            try (Connection c = conn()) {
                c.setAutoCommit(false);
                try {
                    int cur;
                    try (Statement st = c.createStatement();
                         ResultSet rs = st.executeQuery("SELECT version FROM locks WHERE scope='case'")) {
                        rs.next();
                        cur = rs.getInt(1);
                    }
                    if (expectedVersion >= 0 && cur != expectedVersion) {
                        throw new ConflictException(
                                "版本冲突：当前版本 " + cur + "，你的基线版本 " + expectedVersion
                                        + "。请合并他人改动后重试（候选均保留，不会被覆盖）。");
                    }
                    long seq;
                    String payloadJson = mapper.writeValueAsString(payload == null ? Map.of() : payload);
                    String reasonsJson = mapper.writeValueAsString(reasons == null ? Map.of() : reasons);
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO events(id,type,parent_id,base_version,author,ts,payload,reasons) "
                                    + "VALUES(?,?,?,?,?,?,?,?))", Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, id);
                        ps.setString(2, type);
                        ps.setString(3, parentId);
                        ps.setString(4, baseVersion);
                        ps.setString(5, author);
                        ps.setString(6, Instant.now().toString());
                        ps.setString(7, payloadJson);
                        ps.setString(8, reasonsJson);
                        ps.executeUpdate();
                        try (ResultSet gk = ps.getGeneratedKeys()) {
                            gk.next();
                            seq = gk.getLong(1);
                        }
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE locks SET version=? WHERE scope='case'")) {
                        ps.setLong(1, cur + 1);
                        ps.executeUpdate();
                    }
                    c.commit();
                    return seq;
                } catch (Exception e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(true);
                }
            } catch (ConflictException e) {
                throw e;
            } catch (SQLException e) {
                if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                    throw new ConflictException("事件身份已存在: " + e.getMessage());
                }
                throw new RuntimeException("写入事件失败", e);
            } catch (Exception e) {
                throw new RuntimeException("序列化事件失败", e);
            }
        }
    }

    /** 不带乐观锁的便捷追加（内部/导入使用）。 */
    public long appendAny(String type, String id, String parentId, String baseVersion, String author,
                          Map<String, Object> payload, Map<String, Object> reasons) {
        return append(type, id, parentId, baseVersion, author, payload, reasons, -1);
    }

    public History loadAll() {
        History h = new History();
        try (Connection c = conn();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT seq,id,type,parent_id,base_version,author,ts,payload,reasons "
                             + "FROM events ORDER BY seq")) {
            while (rs.next()) {
                h.append(read(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("读取事件失败", e);
        }
        return h;
    }

    @SuppressWarnings("unchecked")
    private Event read(ResultSet rs) throws SQLException {
        try {
            Map<String, Object> payload = mapper.readValue(rs.getString("payload"), Map.class);
            Map<String, Object> reasons = mapper.readValue(rs.getString("reasons"), Map.class);
            return new Event(rs.getLong("seq"), rs.getString("id"), rs.getString("type"),
                    rs.getString("parent_id"), rs.getString("base_version"),
                    rs.getString("author"), rs.getString("ts"),
                    payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload),
                    reasons == null ? new LinkedHashMap<>() : new LinkedHashMap<>(reasons));
        } catch (Exception e) {
            throw new SQLException("解析事件失败 seq=" + rs.getLong("seq"), e);
        }
    }

    /** 导出为可移植 JSON（身份、顺序、来源关系全部保留）。 */
    public Map<String, Object> exportJson() {
        List<Map<String, Object>> evs = new ArrayList<>();
        History h = loadAll();
        for (Event e : h.events()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", e.seq());
            m.put("id", e.id());
            m.put("type", e.type());
            m.put("parentId", e.parentId());
            m.put("baseVersion", e.baseVersion());
            m.put("author", e.author());
            m.put("timestamp", e.timestamp());
            m.put("payload", e.payload());
            m.put("reasons", e.reasons());
            evs.add(m);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", "dsc-reviewer-eventlog");
        root.put("formatVersion", 1);
        root.put("version", currentVersion());
        root.put("exportedAt", Instant.now().toString());
        root.put("events", evs);
        return root;
    }

    /** 导入：在单事务里按 seq 顺序写入；身份冲突时报错而不静默覆盖。 */
    public int importJson(Map<String, Object> root, boolean replace) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evs = (List<Map<String, Object>>) root.get("events");
        synchronized (writeLock) {
            try (Connection c = conn()) {
                c.setAutoCommit(false);
                try {
                    if (replace) {
                        try (Statement st = c.createStatement()) {
                            st.executeUpdate("DELETE FROM events");
                            st.executeUpdate("DELETE FROM sqlite_sequence WHERE name='events'");
                            st.executeUpdate("UPDATE locks SET version=0 WHERE scope='case'");
                        }
                    }
                    int n = 0;
                    for (Map<String, Object> m : evs) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = (Map<String, Object>) m.getOrDefault("payload", Map.of());
                        @SuppressWarnings("unchecked")
                        Map<String, Object> reasons = (Map<String, Object>) m.getOrDefault("reasons", Map.of());
                        String pj = mapper.writeValueAsString(payload);
                        String rj = mapper.writeValueAsString(reasons);
                        try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO events(id,type,parent_id,base_version,author,ts,payload,reasons) "
                                        + "VALUES(?,?,?,?,?,?,?,?)")) {
                            ps.setString(1, (String) m.get("id"));
                            ps.setString(2, (String) m.get("type"));
                            ps.setString(3, (String) m.get("parentId"));
                            ps.setString(4, m.get("baseVersion") == null ? null : m.get("baseVersion").toString());
                            ps.setString(5, (String) m.get("author"));
                            ps.setString(6, (String) m.getOrDefault("timestamp", Instant.now().toString()).toString());
                            ps.setString(7, pj);
                            ps.setString(8, rj);
                            ps.executeUpdate();
                            n++;
                        }
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE locks SET version=? WHERE scope='case'")) {
                    ps.setLong(1, n);
                    ps.executeUpdate();
                    }
                    c.commit();
                    return n;
                } catch (Exception e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new RuntimeException("导入失败（已回滚，数据库保持原状）: " + e.getMessage(), e);
            } catch (Exception e) {
                throw new RuntimeException("导入序列化失败（已回滚）", e);
            }
        }
    }

    /** 测试辅助：模拟写入中断（提交前崩溃），验证不会出现半版本。 */
    public void simulateCrashMidWrite(String type, String id,
                                      Map<String, Object> payload, Map<String, Object> reasons) {
        synchronized (writeLock) {
            Connection c = null;
            try {
                c = conn();
                c.setAutoCommit(false);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO events(id,type,parent_id,base_version,author,ts,payload,reasons) "
                                + "VALUES(?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, id);
                    ps.setString(2, type);
                    ps.setString(3, null);
                    ps.setString(4, null);
                    ps.setString(5, "crash-test");
                    ps.setString(6, Instant.now().toString());
                    ps.setString(7, mapper.writeValueAsString(payload));
                    ps.setString(8, mapper.writeValueAsString(reasons));
                    ps.executeUpdate();
                }
                // 故意不 commit，直接关闭连接 => 事务回滚
                c.close();
                c = null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                if (c != null) {
                    try {
                        c.close();
                    } catch (SQLException ignored) {
                    }
                }
            }
        }
    }

    @Override
    public void close() {
    }
}
