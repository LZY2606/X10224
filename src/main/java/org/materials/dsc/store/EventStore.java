package org.materials.dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import org.materials.dsc.domain.Event;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SQLite 事件存储。
 *  - events 只追加；每个 case 有 revision（乐观并发：两位使用者从同一 revision 编辑必须冲突）；
 *  - 每次推导在一个事务里写入，仅完整成功才提交（atomic switch）；
 *  - crashBeforeCommit=true 时在提交前主动抛错并关闭连接，模拟写入中断：
 *    重开库只能看到旧版本或完整新版本，看不到半成品。
 */
public class EventStore implements AutoCloseable {

    public static final int SCHEMA_VERSION = 2;

    private Connection conn;
    private final String url;
    private final AtomicBoolean crashBeforeCommit = new AtomicBoolean(false);

    public EventStore(String path) {
        this.url = "jdbc:sqlite:" + path;
        open();
        try {
            migrate();
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private EventStore(String url, boolean openNow) {
        this.url = url;
        if (openNow) {
            open();
        }
    }

    public static EventStore memory() {
        EventStore store = new EventStore("jdbc:sqlite::memory:", false);
        store.open();
        try {
            store.migrate();
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
        return store;
    }

    private void open() {
        try {
            conn = DriverManager.getConnection(url);
            try (Statement st = conn.createStatement()) {
                try (ResultSet rs = st.executeQuery("PRAGMA journal_mode=WAL")) {
                    if (rs.next()) {
                        rs.getString(1);
                    }
                }
                st.execute("PRAGMA foreign_keys=ON");
            }
            conn.setAutoCommit(false);
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public synchronized void reopen() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException ignored) {
        }
        open();
    }

    /** 旧版本迁移测试入口：在空文件上写 v1 标记与最小结构。 */
    public static void createLegacyV1(String path) {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT)");
            st.execute("INSERT INTO meta VALUES('schema_version','1')");
            st.execute("CREATE TABLE legacy_case(id TEXT PRIMARY KEY, name TEXT)");
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void migrate() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT)");
            ResultSet rs = st.executeQuery("SELECT value FROM meta WHERE key='schema_version'");
            int version = 0;
            if (rs.next()) {
                version = Integer.parseInt(rs.getString(1));
            }
            rs.close();
            if (version == 1) {
                migrateV1ToV2(st);
                version = 2;
            }
            if (version == 0) {
                createSchema(st);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO meta(key,value) VALUES('schema_version','2')")) {
                    ps.execute();
                }
            }
            if (version < SCHEMA_VERSION && version >= 2) {
                createSchema(st);
            }
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        }
    }

    private void migrateV1ToV2(Statement st) throws SQLException {
        createSchema(st);
        st.execute("UPDATE meta SET value='2' WHERE key='schema_version'");
    }

    private void createSchema(Statement st) throws SQLException {
        st.execute("""
                CREATE TABLE IF NOT EXISTS cases(
                  id TEXT PRIMARY KEY, name TEXT NOT NULL, material TEXT,
                  created_at TEXT, note TEXT, revision INTEGER NOT NULL DEFAULT 0)
                """);
        st.execute("""
                CREATE TABLE IF NOT EXISTS events(
                  seq INTEGER NOT NULL, case_id TEXT NOT NULL,
                  type TEXT NOT NULL, at TEXT NOT NULL,
                  actor TEXT, rule_version TEXT, payload TEXT NOT NULL,
                  PRIMARY KEY(case_id, seq))
                """);
        st.execute("""
                CREATE TABLE IF NOT EXISTS raw_blobs(
                  case_id TEXT NOT NULL, name TEXT NOT NULL,
                  content TEXT NOT NULL, media_type TEXT,
                  PRIMARY KEY(case_id, name))
                """);
        st.execute("CREATE INDEX IF NOT EXISTS idx_events_case ON events(case_id, seq)");
    }

    public synchronized void armCrashBeforeCommit() {
        crashBeforeCommit.set(true);
    }

    public synchronized void createCase(String id, String name, String material, String note) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO cases(id,name,material,created_at,note,revision) "
                        + "VALUES(?,?,?,?,?,0)")) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, material);
            ps.setString(4, Instant.now().toString());
            ps.setString(5, note);
            ps.execute();
            conn.commit();
        } catch (SQLException ex) {
            rollback(ex);
        }
    }

    public synchronized List<String> listCases() {
        List<String> ids = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM cases ORDER BY id")) {
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
            conn.commit();
        } catch (SQLException ex) {
            rollback(ex);
        }
        return ids;
    }

    public synchronized long revision(String caseId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT revision FROM cases WHERE id=?")) {
            ps.setString(1, caseId);
            ResultSet rs = ps.executeQuery();
            long rev = rs.next() ? rs.getLong(1) : -1;
            rs.close();
            conn.commit();
            return rev;
        } catch (SQLException ex) {
            rollback(ex);
            return -1;
        }
    }

    /**
     * 条件追加：仅当 expectedRevision 等于当前 revision 时成功（乐观锁）。
     * @return 提交后的 revision；冲突返回 -1
     */
    public synchronized long appendEvents(String caseId, long expectedRevision, List<Event> events) {
        long current;
        long seq0;
        try {
            current = revisionNoCommit(caseId);
            if (current < 0) {
                throw new IllegalStateException("case not found: " + caseId);
            }
            if (current != expectedRevision) {
                conn.rollback();
                return -1;
            }
            seq0 = nextSeqNoCommit(caseId);
        } catch (SQLException ex) {
            rollback(ex);
            return -1;
        }
        try {
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO events(seq,case_id,type,at,actor,rule_version,payload) "
                            + "VALUES(?,?,?,?,?,?,?)");
                 PreparedStatement rev = conn.prepareStatement(
                         "UPDATE cases SET revision=? WHERE id=?")) {
                for (Event e : events) {
                    ins.setLong(1, e.seq() <= 0 ? seq0++ : e.seq());
                    ins.setString(2, e.caseId());
                    ins.setString(3, e.type());
                    ins.setString(4, e.at());
                    ins.setString(5, e.actor());
                    ins.setString(6, e.ruleVersion());
                    ins.setString(7, Json.write(e.payload()));
                    ins.execute();
                }
                rev.setLong(1, current + 1);
                rev.setString(2, caseId);
                rev.execute();
            }
            if (crashBeforeCommit.getAndSet(false)) {
                conn.close();
                throw new SQLException("simulated crash before commit (write interruption)");
            }
            conn.commit();
            return current + 1;
        } catch (SQLException ex) {
            rollback(ex);
            throw new IllegalStateException(ex);
        }
    }

    private long revisionNoCommit(String caseId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT revision FROM cases WHERE id=?")) {
            ps.setString(1, caseId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    private long nextSeqNoCommit(String caseId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(MAX(seq),0)+1 FROM events WHERE case_id=?")) {
            ps.setString(1, caseId);
            ResultSet rs = ps.executeQuery();
            long v = rs.next() ? rs.getLong(1) : 1;
            rs.close();
            return v;
        }
    }

    public synchronized List<Event> loadEvents(String caseId) {
        return loadEvents(caseId, Long.MAX_VALUE);
    }

    public synchronized List<Event> loadEvents(String caseId, long upToSeq) {
        List<Event> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT seq,type,at,actor,rule_version,payload FROM events "
                        + "WHERE case_id=? AND seq<=? ORDER BY seq")) {
            ps.setString(1, caseId);
            ps.setLong(2, upToSeq);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JsonNode payload = Json.tree(rs.getString(6));
                out.add(new Event(rs.getLong(1), caseId, rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), payload));
            }
            rs.close();
            conn.commit();
        } catch (SQLException ex) {
            rollback(ex);
        }
        return out;
    }

    public synchronized void putBlob(String caseId, String name, String content, String mediaType) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO raw_blobs(case_id,name,content,media_type) VALUES(?,?,?,?)")) {
            ps.setString(1, caseId);
            ps.setString(2, name);
            ps.setString(3, content);
            ps.setString(4, mediaType);
            ps.execute();
            conn.commit();
        } catch (SQLException ex) {
            rollback(ex);
        }
    }

    public synchronized String getBlob(String caseId, String name) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT content FROM raw_blobs WHERE case_id=? AND name=?")) {
            ps.setString(1, caseId);
            ps.setString(2, name);
            ResultSet rs = ps.executeQuery();
            String v = rs.next() ? rs.getString(1) : null;
            rs.close();
            conn.commit();
            return v;
        } catch (SQLException ex) {
            rollback(ex);
            return null;
        }
    }

    public synchronized int schemaVersion() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT value FROM meta WHERE key='schema_version'")) {
            int v = rs.next() ? Integer.parseInt(rs.getString(1)) : 0;
            conn.commit();
            return v;
        } catch (SQLException ex) {
            rollback(ex);
            return 0;
        }
    }

    private void rollback(SQLException ex) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
        }
        throw new IllegalStateException(ex);
    }

    @Override
    public synchronized void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException ignored) {
        }
    }
}
