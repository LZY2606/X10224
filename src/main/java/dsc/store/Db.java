package dsc.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 存储。user_version 控制迁移：
 *  v1: events / derived / derived_current / meta
 *  v2: conflicts 表（旧库打开时自动补齐，数据保留）
 */
public final class Db implements AutoCloseable {

    public static final int SCHEMA_VERSION = 2;

    private final Connection conn;

    private Db(Connection conn) { this.conn = conn; }

    public static Db open(String path) {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA busy_timeout=5000");
            }
            Db db = new Db(c);
            db.migrate();
            db.recoverInterruptedWrites();
            return db;
        } catch (SQLException e) {
            throw new RuntimeException("打开数据库失败: " + path, e);
        }
    }

    public static Db inMemory() {
        return open(":memory:");
    }

    public Connection conn() { return conn; }

    private int userVersion() throws SQLException {
        try (var st = conn.createStatement(); var rs = st.executeQuery("PRAGMA user_version")) {
            return rs.getInt(1);
        }
    }

    private void migrate() {
        try {
            int v = userVersion();
            if (v < 1) {
                try (Statement s = conn.createStatement()) {
                    s.execute("""
                        CREATE TABLE events(
                          seq INTEGER PRIMARY KEY AUTOINCREMENT,
                          id TEXT NOT NULL UNIQUE,
                          type TEXT NOT NULL,
                          actor TEXT,
                          at TEXT NOT NULL,
                          payload TEXT NOT NULL
                        )""");
                    s.execute("""
                        CREATE TABLE derived(
                          id TEXT PRIMARY KEY,
                          kind TEXT NOT NULL,
                          subject TEXT NOT NULL,
                          version INTEGER NOT NULL,
                          status TEXT NOT NULL,
                          deps TEXT NOT NULL,
                          result TEXT,
                          created_at TEXT NOT NULL
                        )""");
                    s.execute("""
                        CREATE TABLE derived_current(
                          subject TEXT PRIMARY KEY,
                          derived_id TEXT NOT NULL
                        )""");
                    s.execute("CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)");
                    s.execute("PRAGMA user_version=1");
                }
            }
            if (v < 2) {
                try (Statement s = conn.createStatement()) {
                    s.execute("""
                        CREATE TABLE conflicts(
                          id TEXT PRIMARY KEY,
                          subject TEXT NOT NULL,
                          base_seq INTEGER NOT NULL,
                          event_a TEXT NOT NULL,
                          event_b TEXT NOT NULL,
                          status TEXT NOT NULL,
                          resolution_event TEXT
                        )""");
                    s.execute("PRAGMA user_version=2");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("数据库迁移失败", e);
        }
    }

    /** 写入中断恢复：building 状态的派生行要么完整发布，要么标记失败，当前指针绝不停在半截。 */
    private void recoverInterruptedWrites() {
        try (Statement s = conn.createStatement()) {
            s.execute("UPDATE derived SET status='failed' WHERE status='building'");
        } catch (SQLException e) {
            throw new RuntimeException("恢复中断写入失败", e);
        }
    }

    public long currentSeq() {
        try (var st = conn.createStatement();
             var rs = st.executeQuery("SELECT COALESCE(MAX(seq),0) FROM events")) {
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try { conn.close(); } catch (SQLException ignored) {}
    }
}
