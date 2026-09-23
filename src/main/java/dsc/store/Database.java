package dsc.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** SQLite storage with explicit schema migrations (old-version migration is tested). */
public final class Database {
    public static final int SCHEMA_VERSION = 2;
    private final Connection conn;

    public Database(String path) throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA foreign_keys=ON");
        }
        migrate();
    }

    public Connection conn() { return conn; }

    private int schemaVersion() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS schema_meta(version INTEGER NOT NULL)");
            var rs = s.executeQuery("SELECT COUNT(*) c, COALESCE(MAX(version),0) v FROM schema_meta");
            if (rs.next() && rs.getInt("c") > 0) return rs.getInt("v");
            s.execute("INSERT INTO schema_meta(version) VALUES (1)");
            return 1;
        }
    }

    private void migrate() throws SQLException {
        int v = schemaVersion();
        try (Statement s = conn.createStatement()) {
            if (v < 1) throw new IllegalStateException();
            // v1: core versioned store
            s.execute("CREATE TABLE IF NOT EXISTS sources(" +
                    "kind TEXT NOT NULL, id TEXT NOT NULL, version INTEGER NOT NULL," +
                    "payload TEXT NOT NULL, created_event TEXT, PRIMARY KEY(kind,id,version))");
            s.execute("CREATE TABLE IF NOT EXISTS current_source(" +
                    "kind TEXT NOT NULL, id TEXT NOT NULL, version INTEGER NOT NULL, PRIMARY KEY(kind,id))");
            s.execute("CREATE TABLE IF NOT EXISTS derived(" +
                    "kind TEXT NOT NULL, id TEXT NOT NULL, version INTEGER NOT NULL," +
                    "deps TEXT NOT NULL, status TEXT NOT NULL, payload TEXT NOT NULL," +
                    "PRIMARY KEY(kind,id,version))");
            s.execute("CREATE TABLE IF NOT EXISTS events(" +
                    "seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL, ts TEXT NOT NULL," +
                    "type TEXT NOT NULL, payload TEXT NOT NULL, base_version INTEGER NOT NULL DEFAULT 0)");
            s.execute("CREATE TABLE IF NOT EXISTS write_journal(" +
                    "txid TEXT PRIMARY KEY, state TEXT NOT NULL, payload TEXT NOT NULL)");
            if (v < 2) {
                // v2: branch table for conflicting human decisions
                s.execute("CREATE TABLE IF NOT EXISTS branches(" +
                        "id TEXT PRIMARY KEY, parent_event TEXT NOT NULL, options TEXT NOT NULL," +
                        "chosen TEXT, status TEXT NOT NULL DEFAULT 'OPEN')");
                s.execute("UPDATE schema_meta SET version=" + SCHEMA_VERSION);
            }
        }
    }

    public void close() throws SQLException { conn.close(); }
}
