package dsc;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * SQLite store with explicit schema migrations.
 * Current schema version is 2; version 1 databases are migrated in place
 * (adds derived.provenance_json and event_log.base_seq) without data loss.
 */
public class Database {
    public static final int SCHEMA_VERSION = 2;

    final Connection conn;

    private Database(Connection conn) {
        this.conn = conn;
    }

    public static Database open(Path path) {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
            c.setAutoCommit(true);
            Database db = new Database(c);
            db.migrate();
            return db;
        } catch (Exception e) {
            throw new RuntimeException("cannot open db " + path, e);
        }
    }

    public static Database memory() {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite::memory:");
            Database db = new Database(c);
            db.migrate();
            return db;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    int schemaVersion() {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY, applied_at TEXT)");
            ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version),0) FROM schema_migrations");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void migrate() {
        int v = schemaVersion();
        if (v < 1) {
            exec("CREATE TABLE IF NOT EXISTS sample(id TEXT PRIMARY KEY, name TEXT, meta_json TEXT)");
            exec("CREATE TABLE IF NOT EXISTS instrument_output(id TEXT PRIMARY KEY, sample_id TEXT, source_file TEXT, sign_convention TEXT, header_json TEXT, created_seq INTEGER)");
            exec("CREATE TABLE IF NOT EXISTS segment(id TEXT PRIMARY KEY, instrument_output_id TEXT, seg_index INTEGER, direction TEXT, raw_csv TEXT, times_json TEXT, temps_json TEXT, hf_json TEXT, created_seq INTEGER)");
            exec("CREATE TABLE IF NOT EXISTS scan_rate(id TEXT PRIMARY KEY, segment_id TEXT, value REAL, unit TEXT, created_seq INTEGER)");
            exec("CREATE TABLE IF NOT EXISTS atmosphere(id TEXT PRIMARY KEY, segment_id TEXT, gas TEXT, flow_value REAL, flow_unit TEXT, created_seq INTEGER)");
            exec("CREATE TABLE IF NOT EXISTS mass_version(id TEXT PRIMARY KEY, sample_id TEXT, value REAL, unit TEXT, uncertainty REAL, revision INTEGER, supersedes TEXT, created_seq INTEGER)");
            exec("CREATE TABLE IF NOT EXISTS calibration(id TEXT PRIMARY KEY, instrument_output_id TEXT, temp_offset REAL, temp_slope REAL, enthalpy_factor REAL, reference TEXT, revision INTEGER, created_seq INTEGER)");
            exec("CREATE TABLE IF NOT EXISTS tangent_selection(id TEXT PRIMARY KEY, segment_id TEXT, side TEXT, anchor_temp REAL, slope REAL, intercept REAL, selected_by TEXT, created_seq INTEGER)");
            exec("CREATE TABLE IF NOT EXISTS derived(id TEXT, kind TEXT, subject_id TEXT, version INTEGER, status TEXT, deps_json TEXT, payload_json TEXT, created_seq INTEGER, PRIMARY KEY(id, version))");
            exec("CREATE TABLE IF NOT EXISTS pointer(pkey TEXT PRIMARY KEY, derived_id TEXT, version INTEGER)");
            exec("CREATE TABLE IF NOT EXISTS pointer_history(pkey TEXT, derived_id TEXT, version INTEGER, seq INTEGER)");
            exec("CREATE TABLE IF NOT EXISTS event_log(seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT, type TEXT, actor TEXT, payload_json TEXT)");
            exec("CREATE TABLE IF NOT EXISTS diagnostic(id TEXT PRIMARY KEY, code TEXT, severity TEXT, subject_id TEXT, detail_json TEXT, created_seq INTEGER)");
            exec("INSERT INTO schema_migrations(version, applied_at) VALUES (1, datetime('now'))");
            v = 1;
        }
        if (v < 2) {
            // v2: provenance column on derived, optimistic-concurrency base on events
            exec("ALTER TABLE derived ADD COLUMN provenance_json TEXT");
            exec("ALTER TABLE event_log ADD COLUMN base_seq INTEGER");
            exec("INSERT INTO schema_migrations(version, applied_at) VALUES (2, datetime('now'))");
        }
    }

    void exec(String sql) {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (Exception e) {
            throw new RuntimeException("sql failed: " + sql, e);
        }
    }
}
