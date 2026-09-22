package dsc.store;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** SQLite 连接与 schema 迁移。所有多步写入都在单事务内完成：
 *  版本指针最后更新，写入中断时只能看到旧版本或完整新版本。 */
public final class Db implements AutoCloseable {
    public static final int SCHEMA_VERSION = 2;

    private final Connection conn;

    public Db(Path file) {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
            }
            migrate();
        } catch (SQLException e) {
            throw new RuntimeException("open db failed: " + file, e);
        }
    }

    public Connection conn() { return conn; }

    private int schemaVersion() throws SQLException {
        try (Statement st = conn.createStatement();
             var rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='meta'")) {
            if (!rs.next()) return 0;
        }
        try (Statement st = conn.createStatement();
             var rs = st.executeQuery("SELECT value FROM meta WHERE key='schema_version'")) {
            return rs.next() ? Integer.parseInt(rs.getString(1)) : 0;
        }
    }

    private void migrate() throws SQLException {
        int v = schemaVersion();
        if (v == 0) { installV1(); v = 1; }
        if (v == 1) { migrateV1toV2(); v = 2; }
        if (v != SCHEMA_VERSION) throw new IllegalStateException("unsupported schema " + v);
    }

    private void installV1() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
                INSERT INTO meta(key,value) VALUES('schema_version','1'),('current_version','0');
                CREATE TABLE samples(id TEXT PRIMARY KEY, name TEXT NOT NULL, created_version INTEGER NOT NULL);
                CREATE TABLE mass_versions(id TEXT PRIMARY KEY, sample_id TEXT NOT NULL, version_no INTEGER NOT NULL,
                  value_raw TEXT NOT NULL, unit TEXT NOT NULL, normalized_mg REAL, status TEXT NOT NULL,
                  created_version INTEGER NOT NULL);
                CREATE TABLE runs(id TEXT PRIMARY KEY, sample_id TEXT NOT NULL, source_file TEXT NOT NULL,
                  instrument TEXT, sign_convention TEXT NOT NULL, sign_source TEXT NOT NULL,
                  raw_meta TEXT NOT NULL, created_version INTEGER NOT NULL);
                CREATE TABLE segments(id TEXT PRIMARY KEY, run_id TEXT NOT NULL, ordinal INTEGER NOT NULL,
                  kind TEXT NOT NULL, rate_raw TEXT NOT NULL, rate_unit TEXT NOT NULL, rate_k_per_s REAL,
                  t_start REAL, t_end REAL, created_version INTEGER NOT NULL);
                CREATE TABLE points(segment_id TEXT NOT NULL, seq INTEGER NOT NULL, t_raw TEXT NOT NULL,
                  temp_raw TEXT NOT NULL, hf_raw TEXT NOT NULL, temp_c REAL, hf_mw REAL,
                  PRIMARY KEY(segment_id, seq));
                CREATE TABLE calibrations(id TEXT PRIMARY KEY, run_id TEXT NOT NULL, kind TEXT NOT NULL,
                  coeffs TEXT NOT NULL, created_version INTEGER NOT NULL);
                CREATE TABLE tangents(id TEXT PRIMARY KEY, segment_id TEXT NOT NULL, author TEXT NOT NULL,
                  left_x REAL NOT NULL, right_x REAL NOT NULL, created_version INTEGER NOT NULL);
                CREATE TABLE intervals(id TEXT PRIMARY KEY, segment_id TEXT NOT NULL, x0 REAL NOT NULL,
                  x1 REAL NOT NULL, author TEXT NOT NULL, created_version INTEGER NOT NULL);
                CREATE TABLE baselines(id TEXT PRIMARY KEY, interval_id TEXT NOT NULL, method TEXT NOT NULL,
                  status TEXT NOT NULL, created_version INTEGER NOT NULL);
                CREATE TABLE derived(id TEXT PRIMARY KEY, node_key TEXT NOT NULL, version INTEGER NOT NULL,
                  deps TEXT NOT NULL, status TEXT NOT NULL, result TEXT NOT NULL,
                  created_version INTEGER NOT NULL);
                CREATE TABLE events(id TEXT PRIMARY KEY, seq INTEGER NOT NULL, ts TEXT NOT NULL,
                  actor TEXT NOT NULL, type TEXT NOT NULL, payload TEXT NOT NULL,
                  base_version INTEGER NOT NULL, result_version INTEGER NOT NULL);
                CREATE TABLE conflicts(id TEXT PRIMARY KEY, base_version INTEGER NOT NULL,
                  current_version INTEGER NOT NULL, actor TEXT NOT NULL, type TEXT NOT NULL,
                  payload TEXT NOT NULL, status TEXT NOT NULL, resolution TEXT,
                  created_version INTEGER NOT NULL);
                CREATE INDEX idx_derived_key ON derived(node_key, version);
                CREATE INDEX idx_points_seg ON points(segment_id, seq);
                """);
        }
    }

    /** v1 -> v2：气氛独立存档（segments.atmosphere）。 */
    private void migrateV1toV2() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("ALTER TABLE segments ADD COLUMN atmosphere TEXT");
            st.executeUpdate("UPDATE meta SET value='2' WHERE key='schema_version'");
        }
    }

    @Override public void close() {
        try { conn.close(); } catch (SQLException ignored) {}
    }
}
