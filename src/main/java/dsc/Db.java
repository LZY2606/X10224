package dsc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** SQLite 存储底座：schema、迁移与事务助手。 */
public final class Db {
    public static final int SCHEMA_VERSION = 2;
    private final Connection conn;

    private Db(Connection conn) { this.conn = conn; }

    public static Db open(String path) {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
            c.setAutoCommit(true);
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA foreign_keys=ON");
            }
            Db db = new Db(c);
            db.migrate();
            return db;
        } catch (SQLException e) {
            throw new RuntimeException("open db failed: " + path, e);
        }
    }

    public Connection conn() { return conn; }

    public int schemaVersion() {
        try (Statement s = conn.createStatement();
             var rs = s.executeQuery("SELECT value FROM meta WHERE key='schema_version'")) {
            return rs.next() ? Integer.parseInt(rs.getString(1)) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    private void migrate() {
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            int v = schemaVersion();
            if (v < 1) {
                s.execute("CREATE TABLE IF NOT EXISTS sources(" +
                        "id TEXT PRIMARY KEY, kind TEXT NOT NULL, name TEXT NOT NULL, version INTEGER NOT NULL," +
                        "payload TEXT NOT NULL, meta TEXT NOT NULL DEFAULT '{}', created_at TEXT NOT NULL," +
                        "UNIQUE(kind,name,version))");
                s.execute("CREATE TABLE IF NOT EXISTS source_heads(kind TEXT NOT NULL, name TEXT NOT NULL," +
                        "version INTEGER NOT NULL, PRIMARY KEY(kind,name))");
                s.execute("CREATE TABLE IF NOT EXISTS derived(" +
                        "id TEXT PRIMARY KEY, kind TEXT NOT NULL, dkey TEXT NOT NULL, version INTEGER NOT NULL," +
                        "inputs TEXT NOT NULL, status TEXT NOT NULL, payload TEXT NOT NULL, created_at TEXT NOT NULL," +
                        "UNIQUE(kind,dkey,version))");
                s.execute("CREATE TABLE IF NOT EXISTS derived_heads(kind TEXT NOT NULL, dkey TEXT NOT NULL," +
                        "version INTEGER NOT NULL, PRIMARY KEY(kind,dkey))");
                s.execute("CREATE TABLE IF NOT EXISTS events(" +
                        "id TEXT PRIMARY KEY, seq INTEGER NOT NULL UNIQUE, type TEXT NOT NULL," +
                        "payload TEXT NOT NULL, created_at TEXT NOT NULL)");
                s.execute("INSERT OR REPLACE INTO meta(key,value) VALUES('schema_version','1')");
                v = 1;
            }
            if (v < 2) {
                // v2: 事件增加分支标记，派生增加诊断列
                s.execute("ALTER TABLE events ADD COLUMN branch TEXT NOT NULL DEFAULT 'main'");
                s.execute("ALTER TABLE derived ADD COLUMN diagnostics TEXT NOT NULL DEFAULT '[]'");
                s.execute("INSERT OR REPLACE INTO meta(key,value) VALUES('schema_version','2')");
            }
        } catch (SQLException e) {
            throw new RuntimeException("migration failed", e);
        }
    }

    /** 事务：只有完整成功才提交；任何异常回滚，调用方看到的仍是旧版本。 */
    public <T> T tx(SqlWork<T> work) {
        try {
            conn.setAutoCommit(false);
            T out = work.run(conn);
            conn.commit();
            return out;
        } catch (Exception e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public void close() {
        try { conn.close(); } catch (SQLException ignored) {}
    }

    @FunctionalInterface
    public interface SqlWork<T> { T run(Connection c) throws Exception; }
}
