package dsc.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite schema 管理与迁移。
 *
 * 原子性策略：每次推导在单个事务内写入全部新版本行，最后才移动
 * current_pointers 指针；进程在写入中断后重启，要么看到旧版本、
 * 要么看到完整新版本（未提交事务由 SQLite 回滚）。
 *
 * 迁移：schema_version=1 -> 2 用 ALTER ADD COLUMN 演示旧版本迁移。
 */
public final class Database {
    public static final int CURRENT_SCHEMA = 2;

    private Database() {
    }

    public static Connection open(String path) {
        try {
            Class.forName("org.sqlite.JDBC");
            String url = "jdbc:sqlite:" + path;
            Connection c = DriverManager.getConnection(url);
            c.createStatement().execute("PRAGMA journal_mode=WAL");
            c.createStatement().execute("PRAGMA foreign_keys=ON");
            c.setAutoCommit(true);
            migrate(c);
            return c;
        } catch (Exception e) {
            throw new IllegalStateException("打开数据库失败: " + path, e);
        }
    }

    public static void migrate(Connection c) throws SQLException {
        c.createStatement().execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT)");
        int v = readVersion(c);
        if (v == 0) {
            createV1(c);
            v = 1;
        }
        if (v < 2) {
            migrateV1ToV2(c);
            v = 2;
        }
        if (v != CURRENT_SCHEMA) {
            throw new IllegalStateException("不支持的数据库 schema 版本: " + v);
        }
        c.createStatement().execute(
                "INSERT OR REPLACE INTO meta(key,value) VALUES('schema_version','" + CURRENT_SCHEMA + "')");
    }

    private static int readVersion(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT value FROM meta WHERE key='schema_version'")) {
            if (rs.next()) {
                return Integer.parseInt(rs.getString(1));
            }
        } catch (SQLException ignored) {
        }
        return 0;
    }

    private static void createV1(Connection c) throws SQLException {
        c.createStatement().executeUpdate("""
                CREATE TABLE sources (
                    id TEXT NOT NULL,
                    version INTEGER NOT NULL,
                    kind TEXT NOT NULL,
                    ref_id TEXT NOT NULL,
                    original_rep TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    sequence INTEGER NOT NULL,
                    PRIMARY KEY (id, version)
                )""");
        c.createStatement().executeUpdate("""
                CREATE TABLE artifacts (
                    id TEXT NOT NULL,
                    version INTEGER NOT NULL,
                    kind TEXT NOT NULL,
                    target_id TEXT NOT NULL,
                    status TEXT NOT NULL,
                    content TEXT NOT NULL,
                    deps_hash TEXT NOT NULL,
                    rule_version TEXT NOT NULL,
                    sequence INTEGER NOT NULL,
                    PRIMARY KEY (id, version)
                )""");
        c.createStatement().executeUpdate("""
                CREATE TABLE current_pointers (
                    artifact_id TEXT PRIMARY KEY,
                    version INTEGER NOT NULL
                )""");
        c.createStatement().executeUpdate("""
                CREATE TABLE events (
                    id TEXT PRIMARY KEY,
                    sequence INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    dep_snapshot TEXT NOT NULL
                )""");
        c.createStatement().executeUpdate("""
                CREATE TABLE decisions (
                    id TEXT PRIMARY KEY,
                    sequence INTEGER NOT NULL,
                    author TEXT NOT NULL,
                    action TEXT NOT NULL,
                    target_id TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    base_version INTEGER NOT NULL,
                    superseded_by INTEGER
                )""");
        c.createStatement().executeUpdate("""
                CREATE TABLE doc_revision (
                    id INTEGER PRIMARY KEY CHECK (id=1),
                    revision INTEGER NOT NULL
                )""");
        c.createStatement().executeUpdate(
                "INSERT INTO doc_revision(id, revision) VALUES(1, 0)");
        c.createStatement().executeUpdate(
                "INSERT OR REPLACE INTO meta(key,value) VALUES('schema_version','1')");
    }

    /**
     * v1->v2：为 decisions 增加 conflict_status（旧行回填 RESOLVED/ACTIVE），
     * 并增加 intervals 表（v1 中区间存在事件 payload 里，v2 独立表）。
     */
    private static void migrateV1ToV2(Connection c) throws SQLException {
        c.createStatement().executeUpdate(
                "ALTER TABLE decisions ADD COLUMN conflict_status TEXT NOT NULL DEFAULT 'ACTIVE'");
        c.createStatement().executeUpdate("""
                CREATE TABLE intervals (
                    interval_id TEXT PRIMARY KEY,
                    segment_id TEXT NOT NULL,
                    transition_id TEXT NOT NULL,
                    t0_k REAL NOT NULL,
                    t1_k REAL NOT NULL,
                    sequence INTEGER NOT NULL
                )""");
        c.createStatement().executeUpdate(
                "INSERT OR REPLACE INTO meta(key,value) VALUES('schema_version','2')");
    }
}
