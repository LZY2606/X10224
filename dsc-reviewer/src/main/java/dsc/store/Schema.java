package dsc.store;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite schema and forward-only migrations.
 *
 * <p>Every version table keeps the full history; the *_current tables are only
 * movable pointers. A legacy v1 fixture (see fixtures/v1-legacy.sql) is
 * migrated on open without changing any stable identity.
 */
public final class Schema {
    public static final int CURRENT_VERSION = 2;

    private Schema() {
    }

    public static void migrate(Connection conn) throws SQLException {
        boolean fresh = !tableExists(conn, "schema_meta");
        int from;
        if (fresh) {
            createV2(conn);
            setVersion(conn, 2);
            return;
        }
        from = readVersion(conn);
        if (from == 1) {
            migrateV1ToV2(conn);
            setVersion(conn, 2);
            from = 2;
        }
        if (from != CURRENT_VERSION) {
            throw new SQLException("Unsupported database schema version: " + from);
        }
    }

    private static boolean tableExists(Connection conn, String name) throws SQLException {
        try (var rs = conn.getMetaData().getTables(null, null, name, null)) {
            return rs.next();
        }
    }

    private static int readVersion(Connection conn) throws SQLException {
        try (var st = conn.createStatement();
                var rs = st.executeQuery("SELECT version FROM schema_meta WHERE singleton = 1")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void setVersion(Connection conn, int version) throws SQLException {
        try (var ps = conn.prepareStatement("INSERT OR REPLACE INTO schema_meta(singleton, version) VALUES (1, ?)")) {
            ps.setInt(1, version);
            ps.executeUpdate();
        }
    }

    static void createV2(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE schema_meta (singleton INTEGER PRIMARY KEY CHECK (singleton = 1), version INTEGER NOT NULL)");
            st.executeUpdate("""
                    CREATE TABLE sample (
                      id TEXT PRIMARY KEY,
                      slug TEXT NOT NULL,
                      name TEXT NOT NULL,
                      created_event_id TEXT NOT NULL,
                      created_seq INTEGER NOT NULL,
                      ordinal INTEGER NOT NULL
                    )""");
            st.executeUpdate("""
                    CREATE TABLE segment (
                      id TEXT PRIMARY KEY,
                      sample_id TEXT NOT NULL,
                      ordinal INTEGER NOT NULL,
                      label TEXT NOT NULL,
                      declared_direction TEXT NOT NULL,
                      sign_convention TEXT NOT NULL,
                      created_event_id TEXT NOT NULL,
                      created_seq INTEGER NOT NULL
                    )""");
            st.executeUpdate("""
                    CREATE TABLE source_version (
                      id TEXT NOT NULL,
                      version INTEGER NOT NULL,
                      scope_id TEXT NOT NULL,
                      kind TEXT NOT NULL,
                      replaced_by TEXT,
                      effective_event_id TEXT NOT NULL,
                      superseded_event_id TEXT,
                      raw_text TEXT NOT NULL,
                      unit TEXT,
                      parsed_json TEXT NOT NULL,
                      diagnostics_json TEXT NOT NULL,
                      content_hash TEXT NOT NULL,
                      parent_hash TEXT,
                      seq INTEGER NOT NULL,
                      PRIMARY KEY (id, version)
                    )""");
            st.executeUpdate("""
                    CREATE TABLE derived_version (
                      id TEXT NOT NULL,
                      version INTEGER NOT NULL,
                      kind TEXT NOT NULL,
                      scope_id TEXT NOT NULL,
                      replaced_by INTEGER NOT NULL DEFAULT 0,
                      deps_json TEXT NOT NULL,
                      payload_json TEXT NOT NULL,
                      diagnostics_json TEXT NOT NULL,
                      produced_by_event TEXT NOT NULL,
                      content_hash TEXT NOT NULL,
                      seq INTEGER NOT NULL,
                      PRIMARY KEY (id, version)
                    )""");
            st.executeUpdate("""
                    CREATE TABLE event_stream (
                      seq INTEGER PRIMARY KEY,
                      id TEXT NOT NULL UNIQUE,
                      kind TEXT NOT NULL,
                      scope_id TEXT NOT NULL,
                      actor TEXT NOT NULL,
                      branch TEXT NOT NULL,
                      parent_event_id TEXT,
                      wall_time INTEGER NOT NULL,
                      payload_json TEXT NOT NULL,
                      produced_ids_json TEXT NOT NULL,
                      base_revision TEXT,
                      content_hash TEXT NOT NULL,
                      prev_hash TEXT
                    )""");
            st.executeUpdate("""
                    CREATE TABLE source_current (
                      id TEXT PRIMARY KEY,
                      scope_id TEXT NOT NULL,
                      kind TEXT NOT NULL,
                      version INTEGER NOT NULL
                    )""");
            st.executeUpdate("""
                    CREATE TABLE derived_current (
                      id TEXT PRIMARY KEY,
                      kind TEXT NOT NULL,
                      scope_id TEXT NOT NULL,
                      version INTEGER NOT NULL
                    )""");
            st.executeUpdate("""
                    CREATE TABLE decision_current (
                      window_id TEXT PRIMARY KEY,
                      adopted_baseline_id TEXT,
                      decided_event_id TEXT NOT NULL,
                      decided_seq INTEGER NOT NULL
                    )""");
            st.executeUpdate("CREATE INDEX ix_source_scope ON source_version(scope_id, kind)");
            st.executeUpdate("CREATE INDEX ix_derived_scope ON derived_version(scope_id, kind)");
            st.executeUpdate("CREATE INDEX ix_event_scope ON event_stream(scope_id, seq)");
        }
    }

    /**
     * v1 kept the sign convention on the source row and lacked the explicit
     * decision_current table. Identities and values are preserved; the column
     * moves to segment metadata and a synthetic adoption event is linked.
     */
    private static void migrateV1ToV2(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("ALTER TABLE segment RENAME TO segment_v1");
            st.executeUpdate("""
                    CREATE TABLE segment (
                      id TEXT PRIMARY KEY,
                      sample_id TEXT NOT NULL,
                      ordinal INTEGER NOT NULL,
                      label TEXT NOT NULL,
                      declared_direction TEXT NOT NULL,
                      sign_convention TEXT NOT NULL,
                      created_event_id TEXT NOT NULL,
                      created_seq INTEGER NOT NULL
                    )""");
            st.executeUpdate("""
                    INSERT INTO segment(id, sample_id, ordinal, label, declared_direction, sign_convention, created_event_id, created_seq)
                    SELECT id, sample_id, ordinal, label,
                           COALESCE(direction, 'UNKNOWN'), 'UNKNOWN',
                           created_event_id, created_seq
                    FROM segment_v1""");
            st.executeUpdate("DROP TABLE segment_v1");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS decision_current (
                      window_id TEXT PRIMARY KEY,
                      adopted_baseline_id TEXT,
                      decided_event_id TEXT NOT NULL,
                      decided_seq INTEGER NOT NULL
                    )""");
        }
    }
}
