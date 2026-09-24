package dsc.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.nio.file.Path;

/** SQLite schema with explicit, replayable migrations. */
public final class Db {
    public static final int CURRENT_SCHEMA = 2;

    private Db() {}

    public static Connection connect(Path path) throws Exception {
        if (path.getParent() != null) path.getParent().toFile().mkdirs();
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=DELETE"); // rollback journal: crash-safe atomic commit
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA busy_timeout=5000");
        }
        return c;
    }

    public static void migrate(Connection c) throws Exception {
        int v = schemaVersion(c);
        if (v < 1) {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT);
                    CREATE TABLE IF NOT EXISTS versions(
                        id INTEGER PRIMARY KEY, label TEXT, parent INTEGER, created_at TEXT);
                    CREATE TABLE IF NOT EXISTS sources(
                        id TEXT, kind TEXT, version INTEGER, unit TEXT, basis TEXT,
                        raw TEXT, payload TEXT,
                        created_version INTEGER, superseded_version INTEGER,
                        PRIMARY KEY(id, version));
                    CREATE TABLE IF NOT EXISTS segments(
                        id TEXT PRIMARY KEY, source_id TEXT, idx INTEGER, direction TEXT,
                        convention TEXT, temp_unit TEXT, hf_unit TEXT, hf_convertible INTEGER,
                        points TEXT, created_version INTEGER, superseded_version INTEGER);
                    CREATE TABLE IF NOT EXISTS events(
                        id TEXT PRIMARY KEY, segment_id TEXT, type TEXT,
                        t_start REAL, t_end REAL, status TEXT,
                        created_version INTEGER, superseded_version INTEGER);
                    CREATE TABLE IF NOT EXISTS baselines(
                        id TEXT PRIMARY KEY, event_id TEXT, kind TEXT, params TEXT, status TEXT,
                        created_version INTEGER, superseded_version INTEGER);
                    CREATE TABLE IF NOT EXISTS integrations(
                        id TEXT PRIMARY KEY, baseline_id TEXT, normalized INTEGER,
                        payload TEXT, status TEXT,
                        created_version INTEGER, superseded_version INTEGER);
                    CREATE TABLE IF NOT EXISTS decisions(
                        id TEXT PRIMARY KEY, target_id TEXT, action TEXT, params TEXT,
                        author TEXT, base_version INTEGER, conflict_id TEXT,
                        created_version INTEGER);
                    CREATE TABLE IF NOT EXISTS case_events(
                        id TEXT PRIMARY KEY, seq INTEGER, branch TEXT, kind TEXT,
                        scenario TEXT, payload TEXT, version INTEGER);
                    CREATE TABLE IF NOT EXISTS deps(child_id TEXT, parent_id TEXT,
                        PRIMARY KEY(child_id, parent_id));
                    """);
                setSchemaVersion(c, 1);
            }
            v = 1;
        }
        if (v < 2) {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS diagnostics(
                        id TEXT PRIMARY KEY, object_id TEXT, severity TEXT, code TEXT,
                        message TEXT, created_version INTEGER);
                    CREATE TABLE IF NOT EXISTS conflicts(
                        id TEXT PRIMARY KEY, base_version INTEGER, branches TEXT,
                        status TEXT, resolved_branch INTEGER, created_version INTEGER,
                        resolved_version INTEGER);
                    """);
                s.executeUpdate("ALTER TABLE integrations ADD COLUMN stale_from_version INTEGER");
                s.executeUpdate("ALTER TABLE baselines ADD COLUMN stale_from_version INTEGER");
                s.executeUpdate("ALTER TABLE events ADD COLUMN stale_from_version INTEGER");
                setSchemaVersion(c, 2);
            }
        }
    }

    public static int schemaVersion(Connection c) throws Exception {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version(v INTEGER)");
            var rs = s.executeQuery("SELECT v FROM schema_version");
            if (rs.next()) return rs.getInt(1);
            s.executeUpdate("INSERT INTO schema_version(v) VALUES(0)");
            return 0;
        }
    }

    private static void setSchemaVersion(Connection c, int v) throws Exception {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE schema_version SET v=" + v);
        }
    }
}
