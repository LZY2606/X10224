package dsc.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite schema owner. Migrations are numbered and stored in a schema_version
 * table. On every open, derivation rows left in complete=0 state by an
 * interrupted writer are swept away: pointers never reference them, so a
 * restart observes either the previous complete version or the new one.
 */
public final class Database {
    public static final int LATEST_VERSION = 2;

    private Database() {}

    public static Connection open(Path path) {
        return open(path, LATEST_VERSION);
    }

    public static Connection open(Path path, int targetVersion) {
        try {
            Class.forName("org.sqlite.JDBC");
            String url = "jdbc:sqlite:" + path.toAbsolutePath();
            Connection conn = DriverManager.getConnection(url);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA busy_timeout=5000");
            }
            conn.setAutoCommit(true);
            int current = currentVersion(conn);
            for (int v = current + 1; v <= targetVersion; v++) {
                applyMigration(conn, v);
                setVersion(conn, v);
            }
            if (targetVersion == LATEST_VERSION) {
                sweepIncomplete(conn);
            }
            return conn;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int currentVersion(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='schema_version'")) {
            if (!rs.next()) {
                return 0;
            }
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version),0) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void setVersion(Connection conn, int version) throws SQLException {
        try (var ps = conn.prepareStatement("INSERT INTO schema_version(version) VALUES (?)")) {
            ps.setInt(1, version);
            ps.executeUpdate();
        }
    }

    private static void applyMigration(Connection conn, int version) throws SQLException {
        switch (version) {
            case 1 -> migrate1Legacy(conn);
            case 2 -> migrate2Current(conn);
            default -> throw new IllegalStateException("unknown migration " + version);
        }
    }

    /** V1: the legacy single-table archive that real old databases carry. */
    static void migrate1Legacy(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE schema_version(version INTEGER NOT NULL)");
            st.execute("CREATE TABLE documents("
                    + "id TEXT PRIMARY KEY, kind TEXT, version INTEGER, raw TEXT)");
        }
    }

    /** V2: separated sources, immutable derivations, pointers, events, conflicts. */
    static void migrate2Current(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE source_document("
                    + "id TEXT PRIMARY KEY, sample_id TEXT NOT NULL, kind TEXT NOT NULL, "
                    + "version INTEGER NOT NULL, raw TEXT NOT NULL, media_type TEXT, "
                    + "hash TEXT NOT NULL, created_event INTEGER, created_at TEXT NOT NULL)");
            st.execute("CREATE INDEX idx_src ON source_document(sample_id, kind, version)");

            st.execute("CREATE TABLE raw_point("
                    + "source_id TEXT NOT NULL, seg_index INTEGER NOT NULL, seq INTEGER NOT NULL, "
                    + "time_raw REAL, time_unit TEXT, temp_raw REAL, temp_unit TEXT, "
                    + "hf_raw REAL, hf_unit TEXT, "
                    + "PRIMARY KEY(source_id, seg_index, seq))");

            st.execute("CREATE TABLE derivation("
                    + "id TEXT PRIMARY KEY, sample_id TEXT NOT NULL, kind TEXT NOT NULL, "
                    + "version INTEGER NOT NULL, slot TEXT NOT NULL, status TEXT NOT NULL, "
                    + "accepted INTEGER NOT NULL DEFAULT 0, params TEXT NOT NULL, result TEXT, "
                    + "diagnostics TEXT NOT NULL, deps_json TEXT NOT NULL, "
                    + "created_event INTEGER, created_at TEXT NOT NULL, "
                    + "complete INTEGER NOT NULL DEFAULT 1)");
            st.execute("CREATE INDEX idx_der_slot ON derivation(slot, version)");
            st.execute("CREATE INDEX idx_der_sample ON derivation(sample_id)");

            st.execute("CREATE TABLE pointer("
                    + "sample_id TEXT NOT NULL, slot TEXT NOT NULL, "
                    + "derivation_id TEXT NOT NULL, PRIMARY KEY(sample_id, slot))");

            st.execute("CREATE TABLE event_log("
                    + "seq INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, "
                    + "payload TEXT NOT NULL, created_at TEXT NOT NULL)");

            st.execute("CREATE TABLE conflict("
                    + "id TEXT PRIMARY KEY, sample_id TEXT NOT NULL, slot TEXT NOT NULL, "
                    + "base_derivation_id TEXT, branch_a_id TEXT, branch_a_reviewer TEXT, "
                    + "branch_b_id TEXT, branch_b_reviewer TEXT, status TEXT NOT NULL, "
                    + "kept_branch TEXT, created_event INTEGER, resolved_event INTEGER, "
                    + "created_at TEXT NOT NULL)");

            // Carry legacy rows forward instead of dropping them.
            st.execute("INSERT INTO source_document("
                    + "id, sample_id, kind, version, raw, media_type, hash, created_at) "
                    + "SELECT id, 'legacy', kind, COALESCE(version,1), raw, 'text/plain', "
                    + "'legacy-import', '1970-01-01T00:00:00Z' FROM documents");
        }
    }

    static void sweepIncomplete(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM derivation WHERE complete=0");
        }
    }
}
