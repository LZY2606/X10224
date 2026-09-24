package dsc.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite storage. Schema is versioned; migrations run on open so an old
 * database file is upgraded in place while preserving every stored fact.
 */
public class Db {
    public static final int SCHEMA_VERSION = 2;

    private final Connection conn;

    private Db(Connection conn) {
        this.conn = conn;
    }

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
            return db;
        } catch (SQLException e) {
            throw new RuntimeException("cannot open db " + path + ": " + e.getMessage(), e);
        }
    }

    public Connection conn() {
        return conn;
    }

    public int schemaVersion() {
        try (Statement s = conn.createStatement();
             var rs = s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='schema_version'")) {
            if (!rs.next()) return 0;
            try (var rs2 = s.executeQuery("SELECT MAX(version) v FROM schema_version")) {
                return rs2.next() ? rs2.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void migrate() {
        int v = schemaVersion();
        try {
            conn.setAutoCommit(false);
            if (v < 1) {
                try (Statement s = conn.createStatement()) {
                    s.executeUpdate(SCHEMA_V1);
                    s.executeUpdate("INSERT INTO schema_version(version, applied_at) VALUES (1, datetime('now'))");
                }
            }
            if (v < 2) {
                // v2: machine-readable diagnostics and conflict branches become first-class.
                try (Statement s = conn.createStatement()) {
                    s.executeUpdate(SCHEMA_V2);
                    s.executeUpdate("INSERT INTO schema_version(version, applied_at) VALUES (2, datetime('now'))");
                }
            }
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("migration failed: " + e.getMessage(), e);
        }
    }

    public void close() {
        try { conn.close(); } catch (SQLException ignored) {}
    }

    private static final String SCHEMA_V1 = """
        CREATE TABLE IF NOT EXISTS schema_version(version INTEGER NOT NULL, applied_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS counters(kind TEXT PRIMARY KEY, next INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS samples(
            id TEXT PRIMARY KEY, name TEXT NOT NULL, created_seq INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS calibrations(
            id TEXT PRIMARY KEY, ref TEXT, offset_c REAL, slope REAL, created_seq INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS runs(
            id TEXT PRIMARY KEY, sample_id TEXT NOT NULL, source_file TEXT NOT NULL,
            header_json TEXT NOT NULL, convention TEXT NOT NULL, convention_source TEXT NOT NULL,
            atmosphere TEXT, atmosphere_flow REAL, atmosphere_flow_unit TEXT,
            scan_rate REAL, scan_rate_unit TEXT, calibration_id TEXT,
            created_seq INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS run_points(
            run_id TEXT NOT NULL, idx INTEGER NOT NULL,
            time_s REAL NOT NULL, temp_c REAL NOT NULL, heatflow_raw REAL NOT NULL,
            heatflow_unit TEXT NOT NULL, PRIMARY KEY(run_id, idx));
        CREATE TABLE IF NOT EXISTS mass_versions(
            id TEXT PRIMARY KEY, sample_id TEXT NOT NULL, value REAL NOT NULL, unit TEXT NOT NULL,
            uncertainty REAL, version_no INTEGER NOT NULL, supersedes TEXT, reason TEXT,
            event_seq INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS segments(
            id TEXT PRIMARY KEY, run_id TEXT NOT NULL, idx INTEGER NOT NULL,
            kind TEXT NOT NULL, t_start REAL NOT NULL, t_end REAL NOT NULL,
            temp_start REAL NOT NULL, temp_end REAL NOT NULL, n_points INTEGER NOT NULL,
            uniform_spacing INTEGER NOT NULL, start_idx INTEGER NOT NULL, end_idx INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS tangents(
            id TEXT PRIMARY KEY, segment_id TEXT NOT NULL, kind TEXT NOT NULL,
            t0 REAL NOT NULL, t1 REAL NOT NULL, author TEXT, event_seq INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS baselines(
            id TEXT PRIMARY KEY, segment_id TEXT NOT NULL, type TEXT NOT NULL,
            params_json TEXT NOT NULL, interval_t0 REAL NOT NULL, interval_t1 REAL NOT NULL,
            status TEXT NOT NULL, branch_of TEXT, event_seq INTEGER NOT NULL, created_seq INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS derivations(
            id TEXT PRIMARY KEY, kind TEXT NOT NULL, subject TEXT NOT NULL,
            version_no INTEGER NOT NULL, status TEXT NOT NULL,
            inputs_json TEXT NOT NULL, result_json TEXT, error_json TEXT,
            event_seq INTEGER NOT NULL, created_seq INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS pointers(name TEXT PRIMARY KEY, target TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS events(
            seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT UNIQUE NOT NULL, ts TEXT NOT NULL,
            actor TEXT NOT NULL, type TEXT NOT NULL, payload_json TEXT NOT NULL,
            pointer_snapshot_json TEXT NOT NULL);
        """;

    private static final String SCHEMA_V2 = """
        CREATE TABLE IF NOT EXISTS diagnostics(
            id TEXT PRIMARY KEY, subject TEXT NOT NULL, code TEXT NOT NULL,
            severity TEXT NOT NULL, message TEXT NOT NULL, event_seq INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS conflicts(
            id TEXT PRIMARY KEY, subject TEXT NOT NULL, base_version INTEGER NOT NULL,
            branches_json TEXT NOT NULL, status TEXT NOT NULL,
            resolution_event_seq INTEGER, created_seq INTEGER NOT NULL);
        """;
}
