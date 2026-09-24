package dsc.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite storage with explicit schema migrations and crash recovery.
 *
 * Recovery rule: derivations are written as BUILDING first and flipped to
 * COMPLETE in the same transaction that moves the current pointer. A process
 * that dies mid-write therefore leaves either the old version fully visible
 * or the new version fully visible; half-written BUILDING rows are removed
 * on open.
 */
public final class Database implements AutoCloseable {
    public static final int SCHEMA_VERSION = 2;

    private final Connection conn;

    private Database(Connection conn) { this.conn = conn; }

    public static Database open(String path) {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA busy_timeout=5000");
            }
            Database db = new Database(c);
            db.migrate();
            db.recover();
            return db;
        } catch (SQLException e) {
            throw new RuntimeException("cannot open database " + path, e);
        }
    }

    public Connection conn() { return conn; }

    private int schemaVersion() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS schema_meta(version INTEGER NOT NULL)");
            var rs = s.executeQuery("SELECT COALESCE(MAX(version),0) FROM schema_meta");
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void migrate() throws SQLException {
        int v = schemaVersion();
        if (v > SCHEMA_VERSION) throw new IllegalStateException("database schema " + v + " newer than supported " + SCHEMA_VERSION);
        List<String> statements = new ArrayList<>();
        if (v < 1) {
            statements.add("""
                CREATE TABLE sample(
                  id TEXT PRIMARY KEY,
                  name TEXT NOT NULL,
                  created_at TEXT NOT NULL)""");
            statements.add("""
                CREATE TABLE source(
                  id TEXT PRIMARY KEY,
                  sample_id TEXT NOT NULL,
                  kind TEXT NOT NULL,
                  scope TEXT NOT NULL,
                  version INTEGER NOT NULL,
                  raw TEXT NOT NULL,
                  meta TEXT NOT NULL,
                  created_at TEXT NOT NULL,
                  UNIQUE(kind, scope, version))""");
            statements.add("""
                CREATE TABLE pointer(
                  key TEXT PRIMARY KEY,
                  target_kind TEXT NOT NULL,
                  target_id TEXT NOT NULL,
                  version INTEGER NOT NULL,
                  updated_at TEXT NOT NULL)""");
            statements.add("""
                CREATE TABLE derivation(
                  id TEXT PRIMARY KEY,
                  key TEXT NOT NULL,
                  kind TEXT NOT NULL,
                  version INTEGER NOT NULL,
                  status TEXT NOT NULL,
                  inputs TEXT NOT NULL,
                  result TEXT NOT NULL,
                  created_at TEXT NOT NULL)""");
            statements.add("""
                CREATE TABLE event(
                  seq INTEGER PRIMARY KEY AUTOINCREMENT,
                  id TEXT NOT NULL,
                  sample_id TEXT,
                  type TEXT NOT NULL,
                  payload TEXT NOT NULL,
                  created_at TEXT NOT NULL)""");
            statements.add("INSERT INTO schema_meta(version) VALUES (1)");
            v = 1;
        }
        if (v < 2) {
            // v2: replay snapshots on events + human decision branches
            statements.add("ALTER TABLE event ADD COLUMN pointers TEXT NOT NULL DEFAULT '{}'");
            statements.add("""
                CREATE TABLE decision(
                  id TEXT PRIMARY KEY,
                  sample_id TEXT NOT NULL,
                  target_key TEXT NOT NULL,
                  base_seq INTEGER NOT NULL,
                  chosen TEXT NOT NULL,
                  rejected TEXT NOT NULL,
                  status TEXT NOT NULL,
                  created_at TEXT NOT NULL)""");
            statements.add("INSERT INTO schema_meta(version) VALUES (2)");
        }
        if (!statements.isEmpty()) {
            boolean prev = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (Statement s = conn.createStatement()) {
                for (String sql : statements) s.execute(sql);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(prev);
            }
        }
    }

    /** Remove half-written derivations left behind by an interrupted write. */
    public void recover() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("DELETE FROM derivation WHERE status='BUILDING'");
        }
    }

    public <T> T tx(SqlWork<T> work) {
        try {
            boolean prev = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                T out = work.run(conn);
                conn.commit();
                conn.setAutoCommit(prev);
                return out;
            } catch (Exception e) {
                conn.rollback();
                conn.setAutoCommit(prev);
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    public interface SqlWork<T> { T run(Connection c) throws Exception; }

    @Override
    public void close() {
        try { conn.close(); } catch (SQLException ignored) {}
    }
}
