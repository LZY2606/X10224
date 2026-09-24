package dsc.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** SQLite handle with schema versioning. v1 = base schema, v2 adds result.committed
 *  (two-phase write marker for crash recovery). */
public final class Db implements AutoCloseable {
    public static final int SCHEMA_VERSION = 2;

    final Connection conn;

    private Db(Connection conn) { this.conn = conn; }

    public static Db open(String path) {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA foreign_keys=ON");
            }
            Db db = new Db(c);
            db.migrate();
            db.recover();
            return db;
        } catch (SQLException e) {
            throw new RuntimeException("open " + path, e);
        }
    }

    public static Db inMemory() {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite::memory:");
            Db db = new Db(c);
            db.migrate();
            return db;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    int schemaVersion() {
        try (var st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT)");
            var rs = st.executeQuery("SELECT value FROM meta WHERE key='schema_version'");
            return rs.next() ? Integer.parseInt(rs.getString(1)) : 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void migrate() {
        int v = schemaVersion();
        try (var st = conn.createStatement()) {
            if (v < 1) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sample(
                      id TEXT PRIMARY KEY, name TEXT, seq INTEGER);
                    """);
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS segment(
                      id TEXT PRIMARY KEY, sample_id TEXT, parent_id TEXT,
                      range_start INTEGER, range_end INTEGER,
                      label TEXT, source_file TEXT, seq INTEGER);
                    """);
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS segment_rev(
                      segment_id TEXT, version INTEGER,
                      raw_text TEXT, parent_rev_version INTEGER,
                      atmosphere TEXT, scan_rate_raw TEXT, endo_convention TEXT,
                      direction TEXT, seq INTEGER,
                      PRIMARY KEY(segment_id, version));
                    """);
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mass_version(
                      id TEXT PRIMARY KEY, sample_id TEXT, version INTEGER,
                      mass_text TEXT, mass_mg REAL, note TEXT, seq INTEGER);
                    """);
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS calibration(
                      id TEXT PRIMARY KEY, sample_id TEXT, version INTEGER,
                      ref_text TEXT, offset_c REAL, note TEXT, seq INTEGER);
                    """);
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS thermal_event(
                      id TEXT PRIMARY KEY, segment_id TEXT, kind TEXT,
                      t_start_c REAL, t_end_c REAL, version INTEGER,
                      decision_version INTEGER, status TEXT, seq INTEGER);
                    """);
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS baseline(
                      id TEXT PRIMARY KEY, event_id TEXT, version INTEGER,
                      type TEXT, params TEXT, status TEXT, seq INTEGER);
                    """);
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS result(
                      id TEXT PRIMARY KEY, event_id TEXT, baseline_id TEXT, version INTEGER,
                      payload TEXT, inputs TEXT, state TEXT, seq INTEGER);
                    """);
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS event_log(
                      seq INTEGER PRIMARY KEY AUTOINCREMENT,
                      id TEXT, ts TEXT, actor TEXT, kind TEXT, payload TEXT, deps TEXT);
                    """);
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS conflict(
                      id TEXT PRIMARY KEY, entity TEXT, entity_id TEXT, base_version INTEGER,
                      branches TEXT, status TEXT, seq INTEGER);
                    """);
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pointer(key TEXT PRIMARY KEY, value TEXT);
                    """);
                setSchemaVersion(st, 1);
                v = 1;
            }
            if (v < 2) {
                st.executeUpdate("ALTER TABLE result ADD COLUMN committed INTEGER NOT NULL DEFAULT 1");
                setSchemaVersion(st, 2);
            }
        } catch (SQLException e) {
            throw new RuntimeException("migrate", e);
        }
    }

    private void setSchemaVersion(Statement st, int v) throws SQLException {
        st.executeUpdate("INSERT INTO meta(key,value) VALUES('schema_version','" + v + "') " +
                "ON CONFLICT(key) DO UPDATE SET value=excluded.value");
    }

    /** Crash recovery: drop result rows written by an interrupted two-phase write.
     *  After recovery the database shows either the old version or a complete new one. */
    public void recover() {
        try (var st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM result WHERE committed=0");
        } catch (SQLException e) {
            throw new RuntimeException("recover", e);
        }
    }

    @Override public void close() {
        try { conn.close(); } catch (SQLException ignored) {}
    }
}
